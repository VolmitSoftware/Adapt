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

package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.HunterMessages;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class HunterStrength extends SimpleAdaptation<HunterStrength.Config> {
  private static final Id HUNGER = Id.minecraft("hunger");
  private static final Id POISON = Id.minecraft("poison");
  private static final Id STRENGTH = Id.minecraft("strength");
  private static final Rgb RAGE_RED = new Rgb(200, 30, 30);
  private final Cooldowns fxCooldown = cooldowns();
  private final Map<UUID, Long> strengthUntil = playerState();

  public HunterStrength() {
    super("hunter-strength");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("cod_bucket"));
    setInterval(9044);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("blaze_powder"))
        .key("challenge_hunter_strength_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_hunter_strength_200", "hunter.strength.activations", 200, 300);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    if (getActiveLevel(player.getPlayer()) <= 0) {
      AdaptAttributeService.get().remove(player.getPlayer(), getName(), "strength", Attributes.ATTACK_DAMAGE);
    }
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, HunterPreferences.RESERVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GRAY + AdaptLanguage.text(HunterMessages.STRENGTH_LORE1));
    statLore(v, level, 2);
    statLore(v, C.RED, "- ", (5 + level), 3);
    statLore(v, C.GRAY, "* ", level, 4);
    statLore(v, C.GRAY, "* ", level, 5);
    statLore(v, C.GRAY, "- ", level, C.RED, HunterMessages.PENALTY_LORE1);

  }


  @Handle
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p) || !isAdaptableDamageCause(e)) {
      return;
    }

    if (!preference(p, HunterPreferences.RESERVE).permits(p.foodLevel())) {
      return;
    }
    int level = getActiveLevel(p);
    if (level <= 0
        || (AdaptConfig.get().isPreventHunterSkillsWhenHungerApplied()
        && p.hasEffect(HUNGER))) {
      return;
    }

    if (!getConfig().useConsumable) {
      if (p.foodLevel() == 0) {
        starvePenalty(p, level);
      } else if (applyStrengthBuff(p, level)) {
        addPotionStacks(p, HUNGER, getConfig().baseHungerFromLevel - level,
            getConfig().baseHungerDuration * level, getConfig().stackHungerPenalty);
        recordActivation(p);
      }
      return;
    }

    Id material = HunterConsumables.resolve(getConfig().consumable);
    if (material == null) {
      return;
    }
    if (!p.inventory().contains(material, 1)) {
      starvePenalty(p, level);
      return;
    }
    if (!canApplyStrengthBuff(p, level) || !activateWithConsumable(p, material, level)) {
      return;
    }
    recordActivation(p);
  }

  private boolean activateWithConsumable(PlayerRef p, Id material, int level) {
    AtomicBoolean defaultApplied = new AtomicBoolean();
    AbilityCharge charge = payItemCostDeferred(p, "consumable", AdaptCore.platform().host().items().create(material), 1, () -> {
      if (!p.inventory().contains(material, 1) || !applyStrengthBuff(p, level)) {
        return false;
      }
      p.inventory().removeItem(material, 1);
      defaultApplied.set(true);
      return true;
    });
    if (!charge.allowed()) {
      return false;
    }
    if (defaultApplied.get()) {
      settleCost(charge.activationId());
      return true;
    }
    if (!applyStrengthBuff(p, level)) {
      refundCost(charge.activationId(), AbilityRefundReason.ACTIVATION_FAILED);
      return false;
    }
    settleCost(charge.activationId());
    return true;
  }

  @Handle
  public void on(PlayerDeathEvent e) {
    strengthUntil.remove(e.player().uuid());
  }

  private boolean canApplyStrengthBuff(PlayerRef p, int level) {
    if (p.hasEffect(STRENGTH)) {
      return false;
    }

    boolean stackBuff = getConfig().stackBuff;
    long now = System.currentTimeMillis();
    Long until = strengthUntil.get(p.uuid());
    long remainingTicks = until == null ? 0L : Math.max(0L, (until - now) / 50L);
    if (remainingTicks > 0L && !stackBuff) {
      return false;
    }

    return buffDurationTicks(getConfig().baseEffectbyLevel, level, remainingTicks, stackBuff) > 0L;
  }

  private boolean applyStrengthBuff(PlayerRef p, int level) {
    if (!canApplyStrengthBuff(p, level)) {
      return false;
    }

    boolean stackBuff = getConfig().stackBuff;
    long now = System.currentTimeMillis();
    Long until = strengthUntil.get(p.uuid());
    long remainingTicks = until == null ? 0L : Math.max(0L, (until - now) / 50L);
    long durationTicks = buffDurationTicks(getConfig().baseEffectbyLevel, level, remainingTicks, stackBuff);
    AdaptAttributeService.get().applyTimed(p, getName(), "strength", Attributes.ATTACK_DAMAGE, strengthDamageBonus(level), AttributeOperation.ADD_VALUE, durationTicks);
    strengthUntil.put(p.uuid(), now + (durationTicks * 50L));
    return true;
  }

  private void recordActivation(PlayerRef p) {
    addStat(p, "hunter.strength.activations", 1);
    activateFx(p);
  }

  private void starvePenalty(PlayerRef p, int level) {
    if (getConfig().poisonPenalty) {
      addPotionStacks(p, POISON, getConfig().basePoisonFromLevel - level,
          getConfig().baseHungerDuration, getConfig().stackPoisonPenalty);
    }
    starveFx(p);
  }

  static double strengthDamageBonus(int level) {
    return 3.0D * (level + 1);
  }

  static long buffDurationTicks(int baseTicksPerLevel, int level, long remainingTicks, boolean stackBuff) {
    long base = (long) baseTicksPerLevel * level;
    if (base <= 0L) {
      return 0L;
    }
    return base + (stackBuff ? Math.max(0L, remainingTicks) : 0L);
  }

  private void activateFx(PlayerRef p) {
    if (!fxCooldown.isReady(p.uuid(), 1200L)) {
      return;
    }
    fxCooldown.mark(p.uuid());
    Loc loc = p.eyeLocation();
    fx(loc, FxPriority.TRANSITION)
        .dustBurst(RAGE_RED, 10, 0.4D, 1.3F)
        .particle(Particles.FLAME, 4, 0, 0, 0, 0.2D, 0.02D)
        .chord(Sounds.ENTITY_ZOGLIN_ATTACK, 0.4F, 1.4F, Sounds.ENTITY_PLAYER_ATTACK_STRONG, 0.5F, 0.8F);
  }

  private void starveFx(PlayerRef p) {
    if (!fxCooldown.isReady(p.uuid(), 1200L)) {
      return;
    }
    fxCooldown.mark(p.uuid());
    FxPresets.failFizzle(this, p);
  }


  @ConfigDescription("Gain strength when struck, at the cost of hunger.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Use Consumable for the Hunter Strength adaptation.", impact = "True enables this behavior and false disables it.")
    boolean useConsumable = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Poison Penalty for the Hunter Strength adaptation.", impact = "True enables this behavior and false disables it.")
    boolean poisonPenalty = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stack Hunger Penalty for the Hunter Strength adaptation.", impact = "True enables this behavior and false disables it.")
    boolean stackHungerPenalty = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stack Poison Penalty for the Hunter Strength adaptation.", impact = "True enables this behavior and false disables it.")
    boolean stackPoisonPenalty = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stack Buff for the Hunter Strength adaptation.", impact = "True enables this behavior and false disables it.")
    boolean stackBuff = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Effectby Level for the Hunter Strength adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseEffectbyLevel = 25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Hunger From Level for the Hunter Strength adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseHungerFromLevel = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Poison From Level for the Hunter Strength adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int basePoisonFromLevel = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Hunger Duration for the Hunter Strength adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseHungerDuration = 50;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Consumable for the Hunter Strength adaptation.", impact = "Changing this alters the identifier or text used by the feature.")
    String consumable = "ROTTEN_FLESH";

    public Config() {
      costFactor = 0.4;
      initialCost = 8;
    }
  }
}
