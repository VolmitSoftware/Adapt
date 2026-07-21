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
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.GeneratedStructure;

import java.util.UUID;

public class DiscoveryTrailblazer extends SimpleAdaptation<DiscoveryTrailblazer.Config> {
  private static final long SCAN_INTERVAL_MILLIS = 1200L;

  private final Cooldowns scanThrottle = cooldowns();

  public DiscoveryTrailblazer() {
    super("discovery-trailblazer");
    registerConfiguration(Config.class);
    setIcon(Material.LEATHER_BOOTS);
    setInterval(600);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.LEATHER_BOOTS)
        .key("challenge_discovery_trailblazer_25")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.FILLED_MAP)
            .key("challenge_discovery_trailblazer_100")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_discovery_trailblazer_25", "discovery.trailblazer.discoveries", 25, 400);
    registerMilestone("challenge_discovery_trailblazer_100", "discovery.trailblazer.discoveries", 100, 1200);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(firstVisitXp(getLevelPercent(level), getConfig().firstVisitXpBase, getConfig().firstVisitXpFactor), 0), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(speedDurationTicks(getLevelPercent(level), getConfig().speedDurationTicksBase, getConfig().speedDurationTicksFactor) * 50D, 1), 2);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
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
      if (!scanThrottle.isReady(id, SCAN_INTERVAL_MILLIS)) {
        continue;
      }

      scanThrottle.mark(id);
      J.runEntity(player, () -> scanOwned(player));
    }
  }

  private void scanOwned(Player player) {
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }

    Location loc = player.getLocation();
    World world = loc.getWorld();
    if (world == null) {
      return;
    }

    PlayerData data = getPlayer(player).getData();
    Biome biome = loc.getBlock().getBiome();
    String biomeKey = "discovery.trailblazer.biome." + biome.getKey();
    if (data.getStat(biomeKey) <= 0) {
      data.addStat(biomeKey, 1);
      rewardDiscovery(player, level, false);
      return;
    }

    for (GeneratedStructure structure : loc.getChunk().getStructures()) {
      if (!structure.getBoundingBox().contains(loc.getX(), loc.getY(), loc.getZ())) {
        continue;
      }

      String structureKey = "discovery.trailblazer.structure." + structure.getStructure().getStructureType().getKey();
      if (data.getStat(structureKey) > 0) {
        continue;
      }

      data.addStat(structureKey, 1);
      rewardDiscovery(player, level, true);
      return;
    }
  }

  private void rewardDiscovery(Player player, int level, boolean structure) {
    double xpAmount = firstVisitXp(getLevelPercent(level), getConfig().firstVisitXpBase, getConfig().firstVisitXpFactor);
    if (structure) {
      xpAmount *= getConfig().structureXpMultiplier;
    }

    xp(player, xpAmount);
    int speedTicks = speedDurationTicks(getLevelPercent(level), getConfig().speedDurationTicksBase, getConfig().speedDurationTicksFactor);
    if (speedTicks > 0) {
      AdaptAttributeService.get().applyTimed(player, getName(), "speed", Attributes.MOVEMENT_SPEED, speedBonus(getConfig().speedAmplifier), AttributeModifier.Operation.MULTIPLY_SCALAR_1, speedTicks);
    }
    getPlayer(player).getData().addStat("discovery.trailblazer.discoveries", 1);

    Color color = structure ? Color.fromRGB(255, 200, 60) : Color.AQUA;
    timeline(player)
        .duration(18)
        .priority(FxPriority.TRANSITION)
        .cullRadius(28)
        .frame((f, tick, progress) -> {
          f.dustRing(color, 0.6D + (progress * 3.0D), 12, 1.0F);
          if (tick == 0) {
            f.chord(Sound.ENTITY_PLAYER_LEVELUP, 0.5F, structure ? 1.0F : 1.4F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.6F);
          }
        })
        .start();
  }

  static double firstVisitXp(double levelPercent, double base, double factor) {
    return Math.max(0D, base + (levelPercent * factor));
  }

  static int speedDurationTicks(double levelPercent, double base, double factor) {
    return Math.max(20, (int) Math.round(base + (levelPercent * factor)));
  }

  static double speedBonus(int amplifier) {
    return 0.20D * (Math.max(0, amplifier) + 1);
  }

  @ConfigDescription("Your first visit to each biome or structure type grants a skill-XP burst and brief speed.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls First Visit Xp Base for the Discovery Trailblazer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double firstVisitXpBase = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls First Visit Xp Factor for the Discovery Trailblazer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double firstVisitXpFactor = 160;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Multiplier applied to the reward when the first visit is a structure type instead of a biome.", impact = "Higher values make structure discoveries pay more than biome discoveries.")
    double structureXpMultiplier = 2.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Speed Duration Ticks Base for the Discovery Trailblazer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double speedDurationTicksBase = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Speed Duration Ticks Factor for the Discovery Trailblazer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double speedDurationTicksFactor = 120;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Speed tier granted on a fresh discovery (0 grants +20% movement speed, each tier adds another +20%).", impact = "Higher values grant a stronger speed burst.")
    int speedAmplifier = 1;

    public Config() {
      baseCost = 2;
      costFactor = 0.3;
      maxLevel = 5;
      initialCost = 3;
    }
  }
}
