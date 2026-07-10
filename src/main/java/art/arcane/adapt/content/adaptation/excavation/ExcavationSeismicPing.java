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
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.integration.hiddenore.HiddenOreLink;
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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class ExcavationSeismicPing extends SimpleAdaptation<ExcavationSeismicPing.Config> {
  private static final int MAX_SCAN_RANGE = 32;
  private static final int MAX_BLOCK_CHECKS_PER_ACTIVATION = 2048;
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, UUID> activeScans = new ConcurrentHashMap<>();

  public ExcavationSeismicPing() {
    super("excavation-seismic-ping");
    registerConfiguration(Config.class);
    setIcon(Material.GOAT_HORN);
    setInterval(2200);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BELL)
        .key("challenge_excavation_seismic_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_excavation_seismic_200", "excavation.seismic-ping.pings-triggered", 200, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getScanRange(level), 1);
    statLore(v, Form.pc(getPingChance(level), 0), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    Player p = e.getPlayer();
    if (!isExcavationTool(p.getInventory().getItemInMainHand())) {
      return;
    }

    Adaptation.BlockActionContext context = resolveBlockBreakContext(p, e.getBlock().getLocation());
    if (context == null) {
      return;
    }

    int level = context.level();
    if (!cooldowns.isReady(p.getUniqueId(), getCooldownMillis(level))) {
      return;
    }

    cooldowns.mark(p.getUniqueId());
    if (ThreadLocalRandom.current().nextDouble() > getPingChance(level)) {
      return;
    }

    if (activeScans.containsKey(p.getUniqueId())) {
      return;
    }

    Location blockLocation = e.getBlock().getLocation();
    int scanRange = getScanRange(level);
    startScan(p, blockLocation, scanRange, level);
  }

  private void startScan(Player p, Location origin, int scanRange, int level) {
    World world = origin.getWorld();
    if (world == null) {
      return;
    }

    ArrayList<WorldBlockScanScheduler.AdditionalMatch> hiddenMatches = new ArrayList<>(1);
    HiddenOreLink.VeinTarget hidden = HiddenOreLink.nearestVein(origin, scanRange);
    if (hidden != null && hidden.location().getWorld() == world) {
      Location at = hidden.location();
      double dx = at.getX() - origin.getX();
      double dy = at.getY() - origin.getY();
      double dz = at.getZ() - origin.getZ();
      double distanceSquared = (dx * dx) + (dy * dy) + (dz * dz);
      if (at.getBlockY() >= world.getMinHeight()
          && at.getBlockY() < world.getMaxHeight()
          && distanceSquared <= (double) scanRange * scanRange) {
        hiddenMatches.add(new WorldBlockScanScheduler.AdditionalMatch(
            at.getBlockX(), at.getBlockY(), at.getBlockZ(), hidden.display(), distanceSquared
        ));
      }
    }

    int checks = Math.min(MAX_BLOCK_CHECKS_PER_ACTIVATION, Math.max(1, getConfig().maxBlockChecks));
    WorldBlockScanScheduler.ScanRequest request = WorldBlockScanScheduler.ScanRequest.builder(origin)
        .radius(scanRange)
        .denseRadius(getConfig().denseScanRadius)
        .maxSamples(checks)
        .maxResults(1)
        .seed(ThreadLocalRandom.current().nextInt())
        .additionalMatches(hiddenMatches)
        .matcher(this::isOre)
        .completion(result -> completeScan(p, origin, scanRange, level, result))
        .build();
    UUID scanId = WorldBlockScanScheduler.submit(this, p.getUniqueId(), request);
    activeScans.put(p.getUniqueId(), scanId);
  }

  private void completeScan(Player p, Location blockLocation, int scanRange, int level,
                            WorldBlockScanScheduler.ScanResult result) {
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
        fx(blockLocation.clone().add(0.5, 0.5, 0.5), FxPriority.TRANSITION)
            .sound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.3f, 0.5f);
        return;
      }

      WorldBlockScanScheduler.Match target = matches.get(0);
      Location targetCenter = target.center(result.world());
      Location playerOrigin = p.getEyeLocation();
      Vector direction = targetCenter.toVector().subtract(playerOrigin.toVector());
      if (direction.lengthSquared() <= 0.0000001) {
        return;
      }

      Color tint = oreTint(target.material());
      groundPulse(blockLocation.clone().add(0.5, 0.5, 0.5), tint);
      renderDirectionHint(playerOrigin, direction.normalize(), getHintSegments(level), tint);
      playPingSound(p, playerOrigin.distance(targetCenter), scanRange);
      addStat(p, "excavation.seismic-ping.pings-triggered", 1);
      xp(p, getConfig().xpPerPing + (getValue(target.material()) * getConfig().targetValueXpMultiplier));
    });
    if (!scheduled) {
      activeScans.remove(playerId, result.scanId());
    }
  }

  private Color oreTint(Material type) {
    if (type == null) {
      return Color.fromRGB(110, 230, 255);
    }

    String name = type.name();
    if (name.contains("REDSTONE")) {
      return Color.fromRGB(255, 60, 60);
    }
    if (name.contains("DIAMOND")) {
      return Color.fromRGB(90, 230, 235);
    }
    if (name.contains("GOLD")) {
      return Color.fromRGB(255, 215, 70);
    }
    if (name.contains("IRON") || name.contains("COPPER")) {
      return Color.fromRGB(210, 180, 150);
    }
    if (name.contains("EMERALD")) {
      return Color.fromRGB(70, 230, 130);
    }
    if (name.contains("LAPIS")) {
      return Color.fromRGB(60, 110, 240);
    }
    return Color.fromRGB(110, 230, 255);
  }

  private void groundPulse(Location center, Color tint) {
    float size = (float) getConfig().particleSize;
    timeline(center)
        .duration(4)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(20)
        .frame((fx, tick, progress) -> {
          fx.dustRing(tint, 0.4D + (1.4D * progress), 10, size);
          if (tick == 0) {
            fx.sound(Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.4f, 0.8f);
          }
        })
        .start();
  }

  private void renderDirectionHint(Location origin, Vector direction, int segments, Color tint) {
    int total = Math.max(2, segments);
    double spacing = getConfig().segmentSpacing;
    double dx = direction.getX();
    double dy = direction.getY();
    double dz = direction.getZ();
    int perSegment = Math.max(1, getConfig().segmentParticleCount);
    int tipCount = Math.max(1, getConfig().tipParticleCount);
    Particle.DustOptions dust = new Particle.DustOptions(tint, (float) getConfig().particleSize);
    timeline(origin)
        .duration(total)
        .priority(FxPriority.TRAIL)
        .cullRadius(20)
        .frame((fx, tick, progress) -> {
          double head = (tick + 1) * spacing;
          fx.particle(Particles.REDSTONE, perSegment, dx * head, dy * head, dz * head, 0.05D, 0, dust);
          if (tick + 1 >= total) {
            double tip = total * spacing;
            fx.particle(Particle.ELECTRIC_SPARK, tipCount, dx * tip, dy * tip, dz * tip, 0.1D, 0.04D);
          }
        })
        .start();
  }

  private void playPingSound(Player p, double distance, int range) {
    double normalized = Math.min(1.0, distance / Math.max(1.0, range));
    float pitch = (float) Math.max(0.45, Math.min(1.95, 1.9 - (normalized * 1.1)));
    fx(p.getLocation(), FxPriority.GAMEPLAY)
        .chord(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, pitch, Sound.BLOCK_NOTE_BLOCK_BIT, 0.65f, (float) Math.min(2.0, pitch + 0.2));
  }

  private boolean isOre(Material type) {
    return type == Material.ANCIENT_DEBRIS || type.name().endsWith("_ORE");
  }

  private boolean isExcavationTool(ItemStack item) {
    if (!isItem(item)) {
      return false;
    }

    String name = item.getType().name();
    return name.endsWith("_SHOVEL") || name.endsWith("_PICKAXE");
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

  private int getScanRange(int level) {
    return Math.min(MAX_SCAN_RANGE, Math.max(6, (int) Math.round(getConfig().scanRangeBase + (getLevelPercent(level) * getConfig().scanRangeFactor))));
  }

  private double getPingChance(int level) {
    return Math.min(getConfig().maxPingChance, getConfig().pingChanceBase + (getLevelPercent(level) * getConfig().pingChanceFactor));
  }

  private long getCooldownMillis(int level) {
    return Math.max(350L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }

  private int getHintSegments(int level) {
    return Math.max(4, (int) Math.round(getConfig().hintSegmentsBase + (getLevelPercent(level) * getConfig().hintSegmentsFactor)));
  }


  @ConfigDescription("Mining can emit seismic pings that hint toward nearby ore direction.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Scan Range Base for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double scanRangeBase = 11;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Scan Range Factor for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double scanRangeFactor = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum world block checks made by one seismic ping.", impact = "Higher values improve ore detection but increase total budgeted scan work; values above the hard safety cap are clamped.")
    int maxBlockChecks = 1024;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Radius searched completely before the remaining seismic budget is spread across the full range.", impact = "Higher values prioritize nearby accuracy; lower values reserve more samples for distant ore.")
    int denseScanRadius = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Ping Chance Base for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double pingChanceBase = 0.14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Ping Chance Factor for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double pingChanceFactor = 0.37;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Ping Chance for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxPingChance = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisBase = 2600;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisFactor = 1850;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Hint Segments Base for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hintSegmentsBase = 7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Hint Segments Factor for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hintSegmentsFactor = 9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Segment Spacing for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double segmentSpacing = 0.55;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Particle Size for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double particleSize = 0.65;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Segment Particle Count for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int segmentParticleCount = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Tip Particle Count for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int tipParticleCount = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Ping for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerPing = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Target Value Xp Multiplier for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double targetValueXpMultiplier = 0.5;

    public Config() {
      costFactor = 0.78;
      initialCost = 4;
    }
  }
}
