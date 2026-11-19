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

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ArchitectMessages;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.math.M;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ArchitectSteadyHands extends SimpleAdaptation<ArchitectSteadyHands.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> KNOCKBACK = CommonPreferences.toggle("knockback", ArchitectMessages.ARCHITECTSTEADYHANDS_PREFERENCE_KNOCKBACK, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> FALL = CommonPreferences.toggle("fall", ArchitectMessages.ARCHITECTSTEADYHANDS_PREFERENCE_FALL, CommonPreferences.Toggle.ON);

  private static final String SLOT_HASTE = "haste";
  private static final String SLOT_BRACE = "brace";
  private static final String SLOT_BLAST = "blast";
  private static final String SLOT_FALL = "fall";

  private final Cooldowns auraCd = cooldowns();
  private final Map<UUID, Long> bridgeGraceUntil = playerState();

  public ArchitectSteadyHands() {
    super("architect-steady-hands");
    needs(PlatformNeed.attribute(Attributes.BLOCK_BREAK_SPEED), PlatformNeed.attribute(Attributes.SAFE_FALL_DISTANCE),
        PlatformNeed.partialAttribute(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, "no explosion knockback resistance"));
    registerConfiguration(ArchitectSteadyHands.Config.class);
    setIcon(Id.minecraft("lightning_rod"));
    setInterval(10440);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("lightning_rod"))
        .key("challenge_architect_steady_hands_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("lightning_rod"))
            .key("challenge_architect_steady_hands_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_architect_steady_hands_500", "architect.steady-hands.bridge-blocks", 500, 300);
    registerMilestone("challenge_architect_steady_hands_5k", "architect.steady-hands.bridge-blocks", 5000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, KNOCKBACK, FALL);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    PlayerRef p = player.getPlayer();
    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.remove(p, getName(), SLOT_HASTE, Attributes.BLOCK_BREAK_SPEED);
    attributes.remove(p, getName(), SLOT_BRACE, Attributes.KNOCKBACK_RESISTANCE);
    attributes.remove(p, getName(), SLOT_BLAST, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE);
    attributes.remove(p, getName(), SLOT_FALL, Attributes.SAFE_FALL_DISTANCE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(ArchitectMessages.STEADY_HANDS_LORE1));
    statLore(v, C.GREEN, "", (int) getShieldedHeight(getLevelPercent(level)), 2);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockPlaceEvent e) {
    PlayerRef p = e.player();
    if (!p.isSneaking()) {
      return;
    }

    if (!e.block().relative(BlockFace.DOWN).type().isAir()) {
      return;
    }

    Adaptation.BlockActionContext context = resolveBlockPlaceContext(p, e.block().location(), PlayerRef::isSneaking);
    if (context == null) {
      return;
    }

    AdaptAttributeService attributes = AdaptAttributeService.get();
    long graceTicks = graceDurationTicks(getConfig().bridgeGraceMillis);
    if (getConfig().hasteDurationTicks > 0) {
      attributes.applyTimed(p, getName(), SLOT_HASTE, Attributes.BLOCK_BREAK_SPEED, hasteBreakSpeedBonus(getConfig().hasteAmplifier), AttributeOperation.ADD_MULTIPLIED_TOTAL, getConfig().hasteDurationTicks);
    }
    if (preferenceEnabled(p, KNOCKBACK)) {
      attributes.applyTimed(p, getName(), SLOT_BRACE, Attributes.KNOCKBACK_RESISTANCE, 1.0D, AttributeOperation.ADD_VALUE, graceTicks);
      attributes.applyTimed(p, getName(), SLOT_BLAST, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0D, AttributeOperation.ADD_VALUE, graceTicks);
    }
    if (preferenceEnabled(p, FALL)) {
      attributes.applyTimed(p, getName(), SLOT_FALL, Attributes.SAFE_FALL_DISTANCE, getShieldedHeight(getLevelPercent(context.level())), AttributeOperation.ADD_VALUE, graceTicks);
    }
    bridgeGraceUntil.put(p.uuid(), M.ms() + (graceTicks * 50L));
    addStat(p, "architect.steady-hands.bridge-blocks", 1);
    if (auraCd.isReady(p.uuid(), 250)) {
      auraCd.mark(p.uuid());
      fx(p.location(), FxPriority.TRANSITION)
          .dustRing(new Rgb(255, 225, 120), 0.5D, 12, 0.8F)
          .sound(Sounds.BLOCK_WOOL_STEP, 0.3f, 1.2f);
    }
  }

  @Handle
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    if (getActiveLevel(p) <= 0 || !preferenceEnabled(p, KNOCKBACK)) {
      return;
    }

    AdaptAttributeService attributes = AdaptAttributeService.get();
    if (!e.isSneaking()) {
      attributes.remove(p, getName(), SLOT_BRACE, Attributes.KNOCKBACK_RESISTANCE);
      attributes.remove(p, getName(), SLOT_BLAST, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE);
      return;
    }

    Long until = bridgeGraceUntil.get(p.uuid());
    if (until == null) {
      return;
    }

    long remainingMillis = until - M.ms();
    if (remainingMillis <= 0L) {
      return;
    }

    long remainingTicks = graceDurationTicks(remainingMillis);
    attributes.applyTimed(p, getName(), SLOT_BRACE, Attributes.KNOCKBACK_RESISTANCE, 1.0D, AttributeOperation.ADD_VALUE, remainingTicks);
    attributes.applyTimed(p, getName(), SLOT_BLAST, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0D, AttributeOperation.ADD_VALUE, remainingTicks);
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
