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

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.NoArgsConstructor;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ExcavationDowsing extends SimpleAdaptation<ExcavationDowsing.Config> {
  private static final int MAX_SCAN_RANGE = 24;
  private static final int[][] SCAN_OFFSETS = buildScanOffsets();
  private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

  public ExcavationDowsing() {
    super("excavation-dowsing");
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("excavation.dowsing.description"));
    setDisplayName(Localizer.dLocalize("excavation.dowsing.name"));
    setIcon(Material.COMPASS);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    setInterval(3970);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.COMPASS)
        .key("challenge_excavation_dowsing_200")
        .title(Localizer.dLocalize("advancement.challenge_excavation_dowsing_200.title"))
        .description(Localizer.dLocalize("advancement.challenge_excavation_dowsing_200.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_excavation_dowsing_200", "excavation.dowsing.pockets-found", 200, 400);
  }

  private static int[][] buildScanOffsets() {
    List<int[]> offsets = new ArrayList<>();
    for (int x = -MAX_SCAN_RANGE; x <= MAX_SCAN_RANGE; x += 2) {
      for (int y = -MAX_SCAN_RANGE; y <= MAX_SCAN_RANGE; y += 2) {
        for (int z = -MAX_SCAN_RANGE; z <= MAX_SCAN_RANGE; z += 2) {
          int d2 = (x * x) + (y * y) + (z * z);
          if (d2 < 25 || d2 > MAX_SCAN_RANGE * MAX_SCAN_RANGE) {
            continue;
          }

          offsets.add(new int[]{x, y, z, d2});
        }
      }
    }

    offsets.sort(Comparator.comparingInt(o -> o[3]));
    return offsets.toArray(new int[0][]);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + getScanRange(level) + C.GRAY + " " + Localizer.dLocalize("excavation.dowsing.lore1"));
    v.addLore(C.YELLOW + "* " + Form.duration(getCooldownMillis(level), 1) + C.GRAY + " " + Localizer.dLocalize("excavation.dowsing.lore2"));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    cooldowns.remove(e.getPlayer().getUniqueId());
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

    long now = System.currentTimeMillis();
    long nextReady = cooldowns.getOrDefault(p.getUniqueId(), 0L);
    if (now < nextReady) {
      return;
    }

    art.arcane.adapt.api.adaptation.Adaptation.BlockActionContext context = resolveInteractContext(p, p.getLocation());
    if (context == null) {
      return;
    }

    int level = context.level();
    cooldowns.put(p.getUniqueId(), now + getCooldownMillis(level));

    Block pocket = findNearestPocket(p.getLocation(), getScanRange(level), getConfig().maxSamples);
    if (pocket == null) {
      return;
    }

    Location origin = p.getEyeLocation();
    Location target = pocket.getLocation().add(0.5, 0.5, 0.5);
    Vector direction = target.toVector().subtract(origin.toVector());
    if (direction.lengthSquared() <= 0.0000001) {
      return;
    }

    Material type = pocket.getType();
    renderTrail(p, origin, direction.normalize(), type);
    playTone(p, type, origin.distance(target), getScanRange(level));
    getPlayer(p).getData().addStat("excavation.dowsing.pockets-found", 1);
    xp(p, getConfig().xpPerPing);
  }

  private Block findNearestPocket(Location origin, int range, int maxSamples) {
    World world = origin.getWorld();
    if (world == null) {
      return null;
    }

    int ox = origin.getBlockX();
    int oy = origin.getBlockY();
    int oz = origin.getBlockZ();
    int minY = world.getMinHeight();
    int maxY = world.getMaxHeight() - 1;
    int rangeSq = range * range;
    int samples = 0;

    for (int[] offset : SCAN_OFFSETS) {
      if (offset[3] > rangeSq || samples >= maxSamples) {
        return null;
      }

      samples++;
      int by = oy + offset[1];
      if (by < minY || by > maxY) {
        continue;
      }

      int bx = ox + offset[0];
      int bz = oz + offset[2];
      if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
        continue;
      }

      Block block = world.getBlockAt(bx, by, bz);
      Material type = block.getType();
      if ((type == Material.WATER || type == Material.LAVA || type.isAir()) && block.getLightFromSky() == 0) {
        return block;
      }
    }

    return null;
  }

  private void renderTrail(Player p, Location origin, Vector direction, Material type) {
    if (!areParticlesEnabled()) {
      return;
    }

    Color color = switch (type) {
      case WATER -> Color.fromRGB(70, 140, 255);
      case LAVA -> Color.fromRGB(255, 120, 30);
      default -> Color.fromRGB(205, 205, 215);
    };
    Particle.DustOptions dust = new Particle.DustOptions(color, (float) getConfig().particleSize);
    Location at = origin.clone();
    for (int i = 0; i < getConfig().trailSegments; i++) {
      at = at.add(direction.clone().multiply(getConfig().segmentSpacing));
      p.spawnParticle(Particle.DUST, at, 2, 0.04, 0.04, 0.04, 0, dust);
    }

    p.spawnParticle(Particle.END_ROD, at, 6, 0.1, 0.1, 0.1, 0.02);
  }

  private void playTone(Player p, Material type, double distance, int range) {
    double normalized = Math.min(1.0, distance / Math.max(1.0, range));
    float pitch = (float) Math.max(0.5, Math.min(2.0, 1.95 - (normalized * 1.2)));
    Sound tone = switch (type) {
      case WATER -> Sound.BLOCK_NOTE_BLOCK_CHIME;
      case LAVA -> Sound.BLOCK_NOTE_BLOCK_BASS;
      default -> Sound.BLOCK_NOTE_BLOCK_BIT;
    };
    SoundPlayer sp = SoundPlayer.of(p.getWorld());
    sp.play(p.getLocation(), tone, 0.85f, pitch);
    sp.play(p.getLocation(), Sound.ITEM_SHOVEL_FLATTEN, 0.3f, 1.6f);
  }

  private int getScanRange(int level) {
    return Math.min(MAX_SCAN_RANGE, Math.max(8, (int) Math.round(getConfig().scanRangeBase + (getLevelPercent(level) * getConfig().scanRangeFactor))));
  }

  private long getCooldownMillis(int level) {
    return Math.max(2000L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }

  @Override
  public void onTick() {

  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  @NoArgsConstructor
  @ConfigDescription("Sneaking with a shovel pings the nearest hidden cave, water, or lava pocket with a directional trail.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Scan Range Base for the Excavation Dowsing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double scanRangeBase = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Scan Range Factor for the Excavation Dowsing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double scanRangeFactor = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Samples for the Excavation Dowsing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxSamples = 2000;
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
  }
}
