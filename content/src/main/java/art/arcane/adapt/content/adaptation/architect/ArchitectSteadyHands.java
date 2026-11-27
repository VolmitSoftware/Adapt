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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ArchitectSteadyHands extends SimpleAdaptation<ArchitectSteadyHands.Config> {
  public static final Id ID = Id.adapt("architect/steady_hands");
  public static final PlayerPreference<CommonPreferences.Toggle> KNOCKBACK = CommonPreferences.toggle("knockback", Text.key("adapt.architectsteadyhands.preferences.preference_knockback"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> FALL = CommonPreferences.toggle("fall", Text.key("adapt.architectsteadyhands.preferences.preference_fall"), CommonPreferences.Toggle.ON);

  private static final String SLOT_HASTE = "haste";
  private static final String SLOT_BRACE = "brace";
  private static final String SLOT_BLAST = "blast";
  private static final String SLOT_FALL = "fall";

  private final Cooldowns auraCd = cooldowns();
  private final Map<UUID, Long> bridgeGraceUntil = playerState();

  public ArchitectSteadyHands() {
    super(ID);
    needs(PlatformNeed.attribute(Attributes.BLOCK_BREAK_SPEED), PlatformNeed.attribute(Attributes.SAFE_FALL_DISTANCE),
        PlatformNeed.partialAttribute(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, "no explosion knockback resistance"));
    registerConfiguration(ArchitectSteadyHands.Config.class);
    setIcon(Id.minecraft("lightning_rod"));
    setInterval(10440);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("lightning_rod"))
        .key("challenge_architect_steady_hands_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("lightning_rod"))
            .key("challenge_architect_steady_hands_5k")
            .frame(AdvancementFrame.CHALLENGE)
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
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    PlayerRef p = player.player();
    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.remove(p, getId().toString(), SLOT_HASTE, Attributes.BLOCK_BREAK_SPEED);
    attributes.remove(p, getId().toString(), SLOT_BRACE, Attributes.KNOCKBACK_RESISTANCE);
    attributes.remove(p, getId().toString(), SLOT_BLAST, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE);
    attributes.remove(p, getId().toString(), SLOT_FALL, Attributes.SAFE_FALL_DISTANCE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + Text.key("adapt.architect.steady_hands.lore1").resolve());
    statLore(v, TextFormat.GREEN, "", (int) getShieldedHeight(getLevelPercent(level)), 2);
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
      attributes.applyTimed(p, getId().toString(), SLOT_HASTE, Attributes.BLOCK_BREAK_SPEED, hasteBreakSpeedBonus(getConfig().hasteAmplifier), AttributeOperation.ADD_MULTIPLIED_TOTAL, getConfig().hasteDurationTicks);
    }
    if (preferenceEnabled(p, KNOCKBACK)) {
      attributes.applyTimed(p, getId().toString(), SLOT_BRACE, Attributes.KNOCKBACK_RESISTANCE, 1.0D, AttributeOperation.ADD_VALUE, graceTicks);
      attributes.applyTimed(p, getId().toString(), SLOT_BLAST, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0D, AttributeOperation.ADD_VALUE, graceTicks);
    }
    if (preferenceEnabled(p, FALL)) {
      attributes.applyTimed(p, getId().toString(), SLOT_FALL, Attributes.SAFE_FALL_DISTANCE, getShieldedHeight(getLevelPercent(context.level())), AttributeOperation.ADD_VALUE, graceTicks);
    }
    long now = System.currentTimeMillis();
    bridgeGraceUntil.put(p.uuid(), now + (graceTicks * 50L));
    HudStatus.duration(p.uuid(), this, now, graceTicks * 50L);
    addStat(p, "architect.steady-hands.bridge-blocks", 1);
    Mutations.landed(this, p);
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
      attributes.remove(p, getId().toString(), SLOT_BRACE, Attributes.KNOCKBACK_RESISTANCE);
      attributes.remove(p, getId().toString(), SLOT_BLAST, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE);
      return;
    }

    Long until = bridgeGraceUntil.get(p.uuid());
    if (until == null) {
      return;
    }

    long remainingMillis = until - System.currentTimeMillis();
    if (remainingMillis <= 0L) {
      return;
    }

    long remainingTicks = graceDurationTicks(remainingMillis);
    attributes.applyTimed(p, getId().toString(), SLOT_BRACE, Attributes.KNOCKBACK_RESISTANCE, 1.0D, AttributeOperation.ADD_VALUE, remainingTicks);
    attributes.applyTimed(p, getId().toString(), SLOT_BLAST, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0D, AttributeOperation.ADD_VALUE, remainingTicks);
  }

  static double hasteBreakSpeedBonus(int amplifier) {
    return 0.2D * (amplifier + 1);
  }

  static long graceDurationTicks(long graceMillis) {
    return Math.max(1L, graceMillis / 50L);
  }

  static double shieldedHeight(double minBlocks, double maxBlocks, double factor) {
    return (minBlocks + factor * (maxBlocks - minBlocks));
  }

  private double getShieldedHeight(double factor) {
    return shieldedHeight(getConfig().minShieldedBlocks, getConfig().maxShieldedBlocks, factor);
  }


  @ConfigDescription("Stay rock-steady while bridging: no knockback and reduced fall damage.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Fall damage shielded in blocks at level 0 progression.", impact = "Higher values absorb more fall damage for low-level players while bridging.")
    double minShieldedBlocks = 3;
    @ConfigDoc(value = "Fall damage shielded in blocks at maximum level progression.", impact = "Higher values absorb more fall damage for max-level players while bridging.")
    double maxShieldedBlocks = 12;
    @ConfigDoc(value = "How long in milliseconds after a bridge placement the protections stay active.", impact = "Higher values keep knockback and fall protection up longer between placements.")
    long bridgeGraceMillis = 4000;
    @ConfigDoc(value = "Duration in ticks of the haste boost granted per bridge placement.", impact = "Higher values keep the placement-speed boost active longer.")
    int hasteDurationTicks = 40;
    @ConfigDoc(value = "Amplifier of the haste boost granted per bridge placement.", impact = "Higher values strengthen the placement-speed boost.")
    int hasteAmplifier = 0;

    public Config() {
      baseCost = 2;
      initialCost = 1;
      costFactor = 0.5;
    }
  }
}
