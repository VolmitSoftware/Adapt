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

package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.SeabornMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.RunsWithoutLearnedAdaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;

public class SeaborneTurtlesMiningSpeed extends SimpleAdaptation<SeaborneTurtlesMiningSpeed.Config> {
  private static final String SUBMERGED_ATTRIBUTE_SLOT = "submerged-mining";
  private static final String FLOATING_ATTRIBUTE_SLOT = "floating-mining";
  private static final int MIN_REFRESH_INTERVAL_MILLIS = 250;
  private static final int MAX_REFRESH_INTERVAL_MILLIS = 10_000;
  private static final long MAX_ATTRIBUTE_DURATION_TICKS = 20L * 60L;
  private static final double MAX_MINING_SPEED_MULTIPLIER = 10D;

  private final Map<UUID, Boolean> submerged = playerState();
  private final Map<UUID, Boolean> floating = playerState();
  private final Cooldowns breakPulse = cooldowns();

  public SeaborneTurtlesMiningSpeed() {
    super("seaborne-turtles-mining-speed");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.haste");
    setIcon(Material.PRISMARINE_SHARD);
    setInterval(refreshIntervalMillis(getConfig().refreshIntervalMillis));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_PICKAXE)
        .key("challenge_seaborne_mining_2500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_PICKAXE)
            .key("challenge_seaborne_mining_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_seaborne_mining_2500", "seaborne.turtles-mining.blocks-underwater", 2500, 300);
    registerMilestone("challenge_seaborne_mining_25k", "seaborne.turtles-mining.blocks-underwater", 25000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GRAY + AdaptLanguage.text(SeabornMessages.HASTE_LORE1));
  }


  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    Player p = e.getPlayer();
    Location at = e.getBlock().getLocation();
    withAdaptedPlayer(p, e, () -> {
      if (!p.isInWater()) {
        return;
      }

      addStat(p, "seaborne.turtles-mining.blocks-underwater", 1);
      if (!breakPulse.isReady(p.getUniqueId(), 150L)) {
        return;
      }
      breakPulse.mark(p.getUniqueId());
      fx(at.add(0.5D, 0.5D, 0.5D), FxPriority.AMBIENT)
          .particle(Particle.BUBBLE, 4, 0, 0, 0, 0.05D, 0.02D)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.2F, 1.7F);
    });
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockDamageEvent e) {
    Player p = e.getPlayer();
    withAdaptedPlayer(p, e, () -> refreshMiningState(p));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  @RunsWithoutLearnedAdaptation
  public void on(PlayerMoveEvent e) {
    if (submerged.isEmpty()) {
      return;
    }

    Player p = e.getPlayer();
    UUID id = p.getUniqueId();
    if (!submerged.containsKey(id)) {
      return;
    }
    if (getActiveLevel(p) <= 0 || !p.isInWater()) {
      clearMiningState(p, id);
      return;
    }
    if (p.isOnGround()) {
      clearFloatingModifier(p, id);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    UUID id = e.getPlayer().getUniqueId();
    submerged.remove(id);
    floating.remove(id);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    for (AdaptPlayer adaptPlayer : learnedCandidates(System.currentTimeMillis())) {
      Player player = adaptPlayer.getPlayer();
      withPlayerThread(player, () -> {
        if (!player.isOnline()) {
          return;
        }
        refreshMiningState(player);
      });
    }
  }

  private void refreshMiningState(Player player) {
    UUID id = player.getUniqueId();
    if (getActiveLevel(player) <= 0 || !player.isInWater()) {
      clearMiningState(player, id);
      return;
    }

    AdaptAttributeService attributes = AdaptAttributeService.get();
    long durationTicks = attributeDurationTicks(getConfig().attributeDurationTicks);
    double submergedScalar = multiplierScalar(getConfig().underwaterMiningSpeedMultiplier);
    if (submergedScalar > 0D) {
      attributes.applyTimed(player, getName(), SUBMERGED_ATTRIBUTE_SLOT, Attributes.SUBMERGED_MINING_SPEED,
          submergedScalar, AttributeModifier.Operation.MULTIPLY_SCALAR_1, durationTicks);
    } else {
      attributes.remove(player, getName(), SUBMERGED_ATTRIBUTE_SLOT, Attributes.SUBMERGED_MINING_SPEED);
    }

    boolean compensateFloating = getConfig().compensateFloatingPenalty && !player.isOnGround();
    double floatingScalar = multiplierScalar(getConfig().floatingMiningSpeedMultiplier);
    if (compensateFloating && floatingScalar > 0D) {
      attributes.applyTimed(player, getName(), FLOATING_ATTRIBUTE_SLOT, Attributes.BLOCK_BREAK_SPEED,
          floatingScalar, AttributeModifier.Operation.MULTIPLY_SCALAR_1, durationTicks);
      floating.put(id, true);
    } else {
      clearFloatingModifier(player, id);
    }

    if (submerged.put(id, true) == null) {
      fx(player.getLocation().add(0D, 1.0D, 0D), FxPriority.TRANSITION)
          .ring(Particle.CRIT, 0.5D, 6, 0.0D)
          .particle(Particle.BUBBLE, 6, 0D, 0.2D, 0D, 0.35D, 0.02D)
          .dustBurst(3, 0.3D, 0.9F)
          .chord(Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.4F, 1.5F, Sound.BLOCK_CONDUIT_AMBIENT_SHORT, 0.25F, 1.0F);
    }
  }

  private void clearMiningState(Player player, UUID id) {
    if (submerged.remove(id) != null) {
      AdaptAttributeService.get().remove(player, getName(), SUBMERGED_ATTRIBUTE_SLOT, Attributes.SUBMERGED_MINING_SPEED);
    }
    clearFloatingModifier(player, id);
  }

  private void clearFloatingModifier(Player player, UUID id) {
    if (floating.remove(id) != null) {
      AdaptAttributeService.get().remove(player, getName(), FLOATING_ATTRIBUTE_SLOT, Attributes.BLOCK_BREAK_SPEED);
    }
  }

  static double multiplierScalar(double multiplier) {
    if (!Double.isFinite(multiplier)) {
      return 0D;
    }
    return Math.max(0D, Math.min(MAX_MINING_SPEED_MULTIPLIER, multiplier) - 1D);
  }

  static long attributeDurationTicks(long configuredTicks) {
    return Math.max(20L, Math.min(MAX_ATTRIBUTE_DURATION_TICKS, configuredTicks));
  }

  static int refreshIntervalMillis(int configuredMillis) {
    return Math.max(MIN_REFRESH_INTERVAL_MILLIS, Math.min(MAX_REFRESH_INTERVAL_MILLIS, configuredMillis));
  }

  @Override
  protected void onConfigReload(Config previousConfig, Config newConfig) {
    super.onConfigReload(previousConfig, newConfig);
    setInterval(refreshIntervalMillis(newConfig.refreshIntervalMillis));
  }

  @ConfigDescription("Mine faster while underwater, with optional compensation for the vanilla floating penalty.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Mining speed multiplier applied specifically while submerged.", impact = "1.4 matches the previous 40% underwater boost; 1 disables this part of the bonus.")
    double underwaterMiningSpeedMultiplier = 1.4D;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cancels the separate vanilla airborne mining slowdown while the player floats underwater.", impact = "True makes the adaptation equally effective when swimming or standing on the seabed.")
    boolean compensateFloatingPenalty = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Block break speed multiplier used while floating underwater when compensation is enabled.", impact = "5 cancels vanilla's one-fifth airborne mining speed; 1 disables the compensation boost.")
    double floatingMiningSpeedMultiplier = 5D;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of each refreshed mining attribute modifier.", impact = "Keep this above the refresh interval converted to ticks to avoid gaps between refreshes.")
    long attributeDurationTicks = 160L;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds between passive underwater-state refreshes.", impact = "Lower values react faster but scan learned players more frequently; block damage still activates the boost immediately.")
    int refreshIntervalMillis = 3000;

    public Config() {
      baseCost = 15;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 3;
    }
  }
}
