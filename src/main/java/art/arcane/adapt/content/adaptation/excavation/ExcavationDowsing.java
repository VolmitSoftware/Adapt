/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.common.world.WorldBlockScanScheduler;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class ExcavationDowsing extends SimpleAdaptation<ExcavationDowsing.Config> {
  private static final int MAX_SCAN_RANGE = 24;
  private static final int MAX_BLOCK_CHECKS_PER_ACTIVATION = 4096;
  private static final int MIN_POCKET_DISTANCE_SQUARED = 25;
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, UUID> activeScans = new ConcurrentHashMap<>();

  public ExcavationDowsing() {
    super("excavation-dowsing");
    registerConfiguration(Config.class);
    setIcon(Material.COMPASS);
    setInterval(3970);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.COMPASS)
        .key("challenge_excavation_dowsing_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_excavation_dowsing_200", "excavation.dowsing.pockets-found", 200, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getScanRange(level), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    if (!e.isSneaking()) {
      return;
    }

    Player p = e.getPlayer();
    if (!isShovel(p.getInventory().getItemInMainHand())) {
      return;
    }

    art.arcane.adapt.api.adaptation.Adaptation.BlockActionContext context = resolveInteractContext(p, p.getLocation());
    if (context == null) {
      return;
    }

    int level = context.level();
    if (!cooldowns.isReady(p.getUniqueId(), getCooldownMillis(level))) {
      return;
    }

    if (activeScans.containsKey(p.getUniqueId())) {
      return;
    }

    cooldowns.mark(p.getUniqueId());
    startScan(p, p.getLocation(), getScanRange(level));
  }

  private void startScan(Player p, Location origin, int range) {
    World world = origin.getWorld();
    if (world == null) {
      return;
    }

    int originX = origin.getBlockX();
    int originY = origin.getBlockY();
    int originZ = origin.getBlockZ();
    int checks = Math.min(MAX_BLOCK_CHECKS_PER_ACTIVATION, Math.max(1, getConfig().maxSamples));
    WorldBlockScanScheduler.ScanRequest request = WorldBlockScanScheduler.ScanRequest.builder(origin)
        .radius(range)
        .denseRadius(getConfig().denseScanRadius)
        .maxSamples(checks)
        .maxResults(1)
        .seed(ThreadLocalRandom.current().nextInt())
        .blockMatcher(block -> isPocket(block, originX, originY, originZ))
        .completion(result -> completeScan(p, range, result))
        .build();
    UUID scanId = WorldBlockScanScheduler.submit(this, p.getUniqueId(), request);
    activeScans.put(p.getUniqueId(), scanId);
  }

  private void completeScan(Player p, int range, WorldBlockScanScheduler.ScanResult result) {
    UUID playerId = p.getUniqueId();
    if (!result.scanId().equals(activeScans.get(playerId))) {
      return;
    }

    boolean scheduled = J.runEntity(p, () -> {
      if (!activeScans.remove(playerId, result.scanId()) || !p.isOnline()) {
        return;
      }

      List<WorldBlockScanScheduler.Match> matches = result.matches();
      if (matches.isEmpty()) {
        fx(p.getEyeLocation(), FxPriority.TRANSITION)
            .particle(Particles.SMOKE, 2, 0, 0, 0, 0.1D, 0.01D)
            .sound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.3f, 0.6f);
        return;
      }

      WorldBlockScanScheduler.Match pocket = matches.get(0);
      Location origin = p.getEyeLocation();
      Location target = pocket.center(result.world());
      Vector direction = target.toVector().subtract(origin.toVector());
      if (direction.lengthSquared() <= 0.0000001) {
        return;
      }

      Material type = pocket.material();
      Color color = pocketColor(type);
      ripplePulse(p.getLocation(), color);
      renderTrail(origin, direction.normalize(), color, type);
      playTone(p, type, origin.distance(target), range);
      addStat(p, "excavation.dowsing.pockets-found", 1);
      xp(p, getConfig().xpPerPing);
    });
    if (!scheduled) {
      activeScans.remove(playerId, result.scanId());
    }
  }

  private boolean isPocket(Block block, int originX, int originY, int originZ) {
    int dx = block.getX() - originX;
    int dy = block.getY() - originY;
    int dz = block.getZ() - originZ;
    if (((dx * dx) + (dy * dy) + (dz * dz)) < MIN_POCKET_DISTANCE_SQUARED) {
      return false;
    }

    Material type = block.getType();
    return (type == Material.WATER || type == Material.LAVA || type.isAir()) && block.getLightFromSky() == 0;
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    UUID playerId = e.getPlayer().getUniqueId();
    activeScans.remove(playerId);
    WorldBlockScanScheduler.cancel(this, playerId);
  }

  @Override
  public void unregister() {
    activeScans.clear();
    WorldBlockScanScheduler.cancelOwner(this);
    super.unregister();
  }

  private Color pocketColor(Material type) {
    return switch (type) {
      case WATER -> Color.fromRGB(70, 140, 255);
      case LAVA -> Color.fromRGB(255, 120, 30);
      default -> Color.fromRGB(205, 205, 215);
    };
  }

  private void ripplePulse(Location center, Color color) {
    float size = (float) getConfig().particleSize;
    timeline(center)
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .cullRadius(16)
        .frame((fx, tick, progress) -> {
          fx.dustRing(color, 0.5D + (1.5D * progress), 12, size);
          if (tick == 0) {
            fx.sound(Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.4f, 1.2f);
          }
        })
        .start();
  }

  private void renderTrail(Location origin, Vector direction, Color color, Material type) {
    int segments = Math.max(2, getConfig().trailSegments);
    double spacing = getConfig().segmentSpacing;
    double dx = direction.getX();
    double dy = direction.getY();
    double dz = direction.getZ();
    Particle.DustOptions dust = new Particle.DustOptions(color, (float) getConfig().particleSize);
    timeline(origin)
        .duration(segments)
        .priority(FxPriority.TRAIL)
        .cullRadius(16)
        .frame((fx, tick, progress) -> {
          double head = (tick + 1) * spacing;
          fx.particle(Particles.REDSTONE, 2, dx * head, dy * head, dz * head, 0.04D, 0, dust);
          if (tick + 1 >= segments) {
            double tip = segments * spacing;
            arrivalBurst(fx, type, dx * tip, dy * tip, dz * tip);
          }
        })
        .start();
  }

  private void arrivalBurst(FxEmitter fx, Material type, double ox, double oy, double oz) {
    switch (type) {
      case WATER -> fx.particle(Particle.SPLASH, 6, ox, oy, oz, 0.12D, 0.02D);
      case LAVA -> fx.particle(Particle.LAVA, 6, ox, oy, oz, 0.12D, 0.02D)
          .particle(Particle.FLAME, 3, ox, oy, oz, 0.1D, 0.02D);
      default -> fx.particle(Particle.END_ROD, 6, ox, oy, oz, 0.1D, 0.02D);
    }
    fx.sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.4f);
  }

  private void playTone(Player p, Material type, double distance, int range) {
    double normalized = Math.min(1.0, distance / Math.max(1.0, range));
    float pitch = (float) Math.max(0.5, Math.min(2.0, 1.95 - (normalized * 1.2)));
    Sound tone = switch (type) {
      case WATER -> Sound.BLOCK_NOTE_BLOCK_CHIME;
      case LAVA -> Sound.BLOCK_NOTE_BLOCK_BASS;
      default -> Sound.BLOCK_NOTE_BLOCK_BIT;
    };
    fx(p.getLocation(), FxPriority.GAMEPLAY)
        .chord(tone, 0.85f, pitch, Sound.ITEM_SHOVEL_FLATTEN, 0.3f, 1.6f);
  }

  private int getScanRange(int level) {
    return Math.min(MAX_SCAN_RANGE, Math.max(8, (int) Math.round(getConfig().scanRangeBase + (getLevelPercent(level) * getConfig().scanRangeFactor))));
  }

  private long getCooldownMillis(int level) {
    return Math.max(2000L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }


  @ConfigDescription("Sneaking with a shovel pings the nearest hidden cave, water, or lava pocket with a directional trail.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Scan Range Base for the Excavation Dowsing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double scanRangeBase = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Scan Range Factor for the Excavation Dowsing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double scanRangeFactor = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Samples for the Excavation Dowsing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxSamples = 2000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Radius searched completely before the remaining Dowsing budget is spread across the full range.", impact = "Higher values prioritize nearby accuracy; lower values reserve more samples for distant pockets.")
    int denseScanRadius = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Excavation Dowsing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisBase = 9000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Excavation Dowsing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisFactor = 4500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Trail Segments for the Excavation Dowsing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int trailSegments = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Segment Spacing for the Excavation Dowsing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double segmentSpacing = 0.55;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Particle Size for the Excavation Dowsing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double particleSize = 0.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Ping for the Excavation Dowsing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerPing = 6;

    public Config() {
      costFactor = 0.7;
      initialCost = 4;
    }
  }
}
