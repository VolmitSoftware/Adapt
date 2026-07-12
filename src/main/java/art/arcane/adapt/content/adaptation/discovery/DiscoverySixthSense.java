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

package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.StructureType;
import org.bukkit.util.StructureSearchResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class DiscoverySixthSense extends SimpleAdaptation<DiscoverySixthSense.Config> {
  private static volatile List<StructureType> structureTypes;

  private static List<StructureType> structureTypes() {
    List<StructureType> local = structureTypes;
    if (local == null) {
      local = List.of(
          StructureType.BURIED_TREASURE,
          StructureType.DESERT_PYRAMID,
          StructureType.IGLOO,
          StructureType.JUNGLE_TEMPLE,
          StructureType.MINESHAFT,
          StructureType.OCEAN_MONUMENT,
          StructureType.OCEAN_RUIN,
          StructureType.RUINED_PORTAL,
          StructureType.SHIPWRECK,
          StructureType.STRONGHOLD,
          StructureType.SWAMP_HUT,
          StructureType.WOODLAND_MANSION,
          StructureType.FORTRESS,
          StructureType.END_CITY,
          StructureType.NETHER_FOSSIL);
      structureTypes = local;
    }
    return local;
  }

  private final Cooldowns pulseThrottle = cooldowns();
  private final AtomicInteger typeCursor = new AtomicInteger();

  public DiscoverySixthSense() {
    super("discovery-sixth-sense");
    registerConfiguration(Config.class);
    setIcon(Material.ECHO_SHARD);
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ECHO_SHARD)
        .key("challenge_discovery_sixthsense_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SCULK_SENSOR)
            .key("challenge_discovery_sixthsense_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_discovery_sixthsense_100", "discovery.sixth-sense.senses", 100, 300);
    registerMilestone("challenge_discovery_sixthsense_1k", "discovery.sixth-sense.senses", 1000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(detectionRange(getLevelPercent(level), getConfig().detectionRangeBase, getConfig().detectionRangeFactor), 0), 1);
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    for (AdaptPlayer adaptPlayer : learnedCandidates(now)) {
      Player player = adaptPlayer.getPlayer();
      if (player == null || !player.isOnline()) {
        continue;
      }

      UUID id = player.getUniqueId();
      if (!pulseThrottle.isReady(id, getConfig().pulseIntervalMillis)) {
        continue;
      }

      pulseThrottle.mark(id);
      J.runEntity(player, () -> senseOwned(player));
    }
  }

  private void senseOwned(Player player) {
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }

    double range = detectionRange(getLevelPercent(level), getConfig().detectionRangeBase, getConfig().detectionRangeFactor);
    int radiusChunks = Math.max(1, (int) Math.ceil(range / 16D));
    List<StructureType> types = structureTypes();
    StructureType type = types.get(Math.floorMod(typeCursor.getAndIncrement(), types.size()));
    World world = player.getWorld();
    Location origin = player.getLocation();

    StructureSearchResult result;
    try {
      result = world.locateNearestStructure(origin, type, radiusChunks, true);
    } catch (Throwable t) {
      return;
    }

    if (result == null || result.getLocation() == null || result.getLocation().getWorld() != world) {
      return;
    }

    Location target = result.getLocation();
    double dx = target.getX() - origin.getX();
    double dz = target.getZ() - origin.getZ();
    double horizontal = Math.sqrt((dx * dx) + (dz * dz));
    if (horizontal > range || horizontal < getConfig().exploredRadius) {
      return;
    }

    pulseTowards(player, dx / horizontal, dz / horizontal, horizontal);
    getPlayer(player).getData().addStat("discovery.sixth-sense.senses", 1);
  }

  private void pulseTowards(Player player, double ux, double uz, double horizontal) {
    if (!getConfig().showParticles) {
      return;
    }

    double length = Math.min(6D, horizontal);
    fx(player.getEyeLocation(), FxPriority.AMBIENT)
        .trail(Particles.END_ROD, ux, 0.0D, uz, length, 8)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.22F, 1.9F);
    fx(player.getLocation(), FxPriority.AMBIENT)
        .ring(Particles.END_ROD, 1.1D, 8, 0.1D);
  }

  static double detectionRange(double levelPercent, double base, double factor) {
    return Math.max(16D, base + (levelPercent * factor));
  }


  @ConfigDescription("A soft particle pulse hints when an unexplored structure is within range.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Detection Range Base for the Discovery Sixth Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double detectionRangeBase = 48;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Detection Range Factor for the Discovery Sixth Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double detectionRangeFactor = 112;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Distance under which a structure is treated as already reached, suppressing the pulse.", impact = "Higher values stop the pulse from firing sooner as you approach a structure.")
    double exploredRadius = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds between sense pulses for a single player.", impact = "Higher values pulse less often and reduce structure search work.")
    long pulseIntervalMillis = 4000;

    public Config() {
      baseCost = 2;
      costFactor = 0.4;
      maxLevel = 5;
      initialCost = 3;
    }
  }
}
