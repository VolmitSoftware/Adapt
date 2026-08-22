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

package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ArchitectMessages;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import lombok.NoArgsConstructor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.Map;
import java.util.UUID;

public class ArchitectSteadyHands extends SimpleAdaptation<ArchitectSteadyHands.Config> {
  private static final String SLOT_HASTE = "haste";
  private static final String SLOT_BRACE = "brace";
  private static final String SLOT_BLAST = "blast";
  private static final String SLOT_FALL = "fall";

  private final Cooldowns auraCd = cooldowns();
  private final Map<UUID, Long> bridgeGraceUntil = playerState();

  public ArchitectSteadyHands() {
    super("architect-steady-hands");
    registerConfiguration(ArchitectSteadyHands.Config.class);
    setIcon(Material.LIGHTNING_ROD);
    setInterval(10440);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.LIGHTNING_ROD)
        .key("challenge_architect_steady_hands_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.LIGHTNING_ROD)
            .key("challenge_architect_steady_hands_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_architect_steady_hands_500", "architect.steady-hands.bridge-blocks", 500, 300);
    registerMilestone("challenge_architect_steady_hands_5k", "architect.steady-hands.bridge-blocks", 5000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(ArchitectMessages.STEADY_HANDS_LORE1));
    statLore(v, C.GREEN, "", (int) getShieldedHeight(getLevelPercent(level)), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPlaceEvent e) {
    Player p = e.getPlayer();
    if (!p.isSneaking()) {
      return;
    }

    if (!e.getBlock().getRelative(BlockFace.DOWN).getType().isAir()) {
      return;
    }

    Adaptation.BlockActionContext context = resolveBlockPlaceContext(p, e.getBlock().getLocation(), Player::isSneaking);
    if (context == null) {
      return;
    }

    AdaptAttributeService attributes = AdaptAttributeService.get();
    long graceTicks = graceDurationTicks(getConfig().bridgeGraceMillis);
    if (getConfig().hasteDurationTicks > 0) {
      attributes.applyTimed(p, getName(), SLOT_HASTE, Attributes.BLOCK_BREAK_SPEED, hasteBreakSpeedBonus(getConfig().hasteAmplifier), AttributeModifier.Operation.MULTIPLY_SCALAR_1, getConfig().hasteDurationTicks);
    }
    attributes.applyTimed(p, getName(), SLOT_BRACE, Attributes.KNOCKBACK_RESISTANCE, 1.0D, AttributeModifier.Operation.ADD_NUMBER, graceTicks);
    attributes.applyTimed(p, getName(), SLOT_BLAST, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0D, AttributeModifier.Operation.ADD_NUMBER, graceTicks);
    attributes.applyTimed(p, getName(), SLOT_FALL, Attributes.SAFE_FALL_DISTANCE, getShieldedHeight(getLevelPercent(context.level())), AttributeModifier.Operation.ADD_NUMBER, graceTicks);
    bridgeGraceUntil.put(p.getUniqueId(), M.ms() + (graceTicks * 50L));
    addStat(p, "architect.steady-hands.bridge-blocks", 1);
    if (auraCd.isReady(p.getUniqueId(), 250)) {
      auraCd.mark(p.getUniqueId());
      fx(p.getLocation(), FxPriority.TRANSITION)
          .dustRing(Color.fromRGB(255, 225, 120), 0.5D, 12, 0.8F)
          .sound(Sound.BLOCK_WOOL_STEP, 0.3f, 1.2f);
    }
  }

  @EventHandler
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    if (getActiveLevel(p) <= 0) {
      return;
    }

    AdaptAttributeService attributes = AdaptAttributeService.get();
    if (!e.isSneaking()) {
      attributes.remove(p, getName(), SLOT_BRACE, Attributes.KNOCKBACK_RESISTANCE);
      attributes.remove(p, getName(), SLOT_BLAST, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE);
      return;
    }

    Long until = bridgeGraceUntil.get(p.getUniqueId());
    if (until == null) {
      return;
    }

    long remainingMillis = until - M.ms();
    if (remainingMillis <= 0L) {
      return;
    }

    long remainingTicks = graceDurationTicks(remainingMillis);
    attributes.applyTimed(p, getName(), SLOT_BRACE, Attributes.KNOCKBACK_RESISTANCE, 1.0D, AttributeModifier.Operation.ADD_NUMBER, remainingTicks);
    attributes.applyTimed(p, getName(), SLOT_BLAST, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0D, AttributeModifier.Operation.ADD_NUMBER, remainingTicks);
  }

  static double hasteBreakSpeedBonus(int amplifier) {
    return 0.2D * (amplifier + 1);
  }

  static long graceDurationTicks(long graceMillis) {
    return Math.max(1L, graceMillis / 50L);
  }

  static double shieldedHeight(double minBlocks, double maxBlocks, double factor) {
    return M.lerp(minBlocks, maxBlocks, factor);
  }

  private double getShieldedHeight(double factor) {
    return shieldedHeight(getConfig().minShieldedBlocks, getConfig().maxShieldedBlocks, factor);
  }


  @NoArgsConstructor
  @ConfigDescription("Stay rock-steady while bridging: no knockback and reduced fall damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fall damage shielded in blocks at level 0 progression.", impact = "Higher values absorb more fall damage for low-level players while bridging.")
    double minShieldedBlocks = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fall damage shielded in blocks at maximum level progression.", impact = "Higher values absorb more fall damage for max-level players while bridging.")
    double maxShieldedBlocks = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "How long in milliseconds after a bridge placement the protections stay active.", impact = "Higher values keep knockback and fall protection up longer between placements.")
    long bridgeGraceMillis = 4000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of the haste boost granted per bridge placement.", impact = "Higher values keep the placement-speed boost active longer.")
    int hasteDurationTicks = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Amplifier of the haste boost granted per bridge placement.", impact = "Higher values strengthen the placement-speed boost.")
    int hasteAmplifier = 0;
  }
}
