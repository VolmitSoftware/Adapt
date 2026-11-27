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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
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
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class HunterJumpBoost extends SimpleAdaptation<HunterJumpBoost.Config> {
  public static final Id ID = Id.adapt("hunter/jumpboost");
  private static final Id HUNGER = Id.minecraft("hunger");
  private static final Id POISON = Id.minecraft("poison");
  private static final Id JUMP_BOOST = Id.minecraft("jump_boost");
  private final Cooldowns fxCooldown = cooldowns();
  private final Map<UUID, Long> jumpUntil = playerState();

  public HunterJumpBoost() {
    super(ID);
    needs(PlatformNeed.attribute(Attributes.JUMP_STRENGTH), PlatformNeed.attribute(Attributes.SAFE_FALL_DISTANCE));
    registerConfiguration(Config.class);
    setLocalizationKey("adapt.hunter.jump_boost");
    setIcon(Id.minecraft("pufferfish_bucket"));
    setInterval(9544);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("rabbit_foot"))
        .key("challenge_hunter_jump_200")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_hunter_jump_200", "hunter.jump-boost.activations", 200, 300);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    if (getActiveLevel(player.player()) <= 0) {
      AdaptAttributeService.get().removeAll(player.player(), getId().toString());
    }
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, HunterPreferences.RESERVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GRAY + Text.key("adapt.hunter.jump_boost.lore1").resolve());
    statLore(v, level, 2);
    statLore(v, TextFormat.RED, "- ", (5 + level), 3);
    statLore(v, TextFormat.GRAY, "* ", level, 4);
    statLore(v, TextFormat.GRAY, "* ", level, 5);
    statLore(v, TextFormat.GRAY, "- ", level, TextFormat.RED, Text.key("adapt.hunter.penalty.lore1"));

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
        || (AdaptApi.settings().hungerEffectPausesHunterAdaptations()
        && p.hasEffect(HUNGER))) {
      return;
    }

    if (!getConfig().useConsumable) {
      if (p.foodLevel() == 0) {
        starvePenalty(p, level);
      } else if (applyJumpBuff(p, level)) {
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
    if (!canApplyJumpBuff(p, level) || !activateWithConsumable(p, material, level)) {
      return;
    }
    recordActivation(p);
  }

  private boolean activateWithConsumable(PlayerRef p, Id material, int level) {
    AtomicBoolean defaultApplied = new AtomicBoolean();
    AbilityCharge charge = payItemCostDeferred(p, "consumable", AdaptApi.platform().host().items().create(material), 1, () -> {
      if (!p.inventory().contains(material, 1) || !applyJumpBuff(p, level)) {
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
    if (!applyJumpBuff(p, level)) {
      refundCost(charge.activationId(), AbilityRefundReason.ACTIVATION_FAILED);
      return false;
    }
    settleCost(charge.activationId());
    return true;
  }

  private boolean canApplyJumpBuff(PlayerRef p, int level) {
    if (p.hasEffect(JUMP_BOOST)) {
      return false;
    }

    boolean stackBuff = getConfig().stackBuff;
    long now = System.currentTimeMillis();
    Long until = jumpUntil.get(p.uuid());
    long remainingTicks = until == null ? 0L : Math.max(0L, (until - now) / 50L);
    if (remainingTicks > 0L && !stackBuff) {
      return false;
    }

    return buffDurationTicks(getConfig().baseEffectbyLevel, level, remainingTicks, stackBuff) > 0L;
  }

  private boolean applyJumpBuff(PlayerRef p, int level) {
    if (!canApplyJumpBuff(p, level)) {
      return false;
    }

    boolean stackBuff = getConfig().stackBuff;
    long now = System.currentTimeMillis();
    Long until = jumpUntil.get(p.uuid());
    long remainingTicks = until == null ? 0L : Math.max(0L, (until - now) / 50L);
    long durationTicks = buffDurationTicks(getConfig().baseEffectbyLevel, level, remainingTicks, stackBuff);
    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.applyTimed(p, getId().toString(), "jump", Attributes.JUMP_STRENGTH, jumpStrengthBonus(level), AttributeOperation.ADD_VALUE, durationTicks);
    attributes.applyTimed(p, getId().toString(), "fall", Attributes.SAFE_FALL_DISTANCE, safeFallBonus(level), AttributeOperation.ADD_VALUE, durationTicks);
    jumpUntil.put(p.uuid(), now + (durationTicks * 50L));
    HudStatus.duration(p.uuid(), this, now, durationTicks * 50L);
    return true;
  }

  private void recordActivation(PlayerRef p) {
    addStat(p, "hunter.jump-boost.activations", 1);
    activateFx(p);
  }

  private void starvePenalty(PlayerRef p, int level) {
    if (getConfig().poisonPenalty) {
      addPotionStacks(p, POISON, getConfig().basePoisonFromLevel - level,
          getConfig().baseHungerDuration, getConfig().stackPoisonPenalty);
    }
    starveFx(p);
  }

  static double jumpStrengthBonus(int level) {
    return 0.1D * (level + 1);
  }

  static double safeFallBonus(int level) {
    return level + 1.0D;
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
    Loc feet = p.location();
    fx(feet, FxPriority.TRANSITION)
        .ring(Particles.CLOUD, 0.6D, 10, 0.05D)
        .column(Particles.CLOUD, 6, 1.2D)
        .chord(Sounds.BLOCK_NOTE_BLOCK_PLING, 0.7F, 1.2F, Sounds.BLOCK_NOTE_BLOCK_BASS, 0.3F, 0.8F);
  }

  private void starveFx(PlayerRef p) {
    if (!fxCooldown.isReady(p.uuid(), 1200L)) {
      return;
    }
    fxCooldown.mark(p.uuid());
    FxPresets.failFizzle(this, p);
  }


  @ConfigDescription("Gain jump boost when struck, at the cost of hunger.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Use Consumable for the Hunter Jump Boost adaptation.", impact = "True enables this behavior and false disables it.")
    boolean useConsumable = false;
    @ConfigDoc(value = "Controls Poison Penalty for the Hunter Jump Boost adaptation.", impact = "True enables this behavior and false disables it.")
    boolean poisonPenalty = true;
    @ConfigDoc(value = "Controls Stack Hunger Penalty for the Hunter Jump Boost adaptation.", impact = "True enables this behavior and false disables it.")
    boolean stackHungerPenalty = false;
    @ConfigDoc(value = "Controls Stack Poison Penalty for the Hunter Jump Boost adaptation.", impact = "True enables this behavior and false disables it.")
    boolean stackPoisonPenalty = false;
    @ConfigDoc(value = "Controls Stack Buff for the Hunter Jump Boost adaptation.", impact = "True enables this behavior and false disables it.")
    boolean stackBuff = false;
    @ConfigDoc(value = "Controls Base Effectby Level for the Hunter Jump Boost adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseEffectbyLevel = 100;
    @ConfigDoc(value = "Controls Base Hunger From Level for the Hunter Jump Boost adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseHungerFromLevel = 10;
    @ConfigDoc(value = "Controls Base Hunger Duration for the Hunter Jump Boost adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseHungerDuration = 50;
    @ConfigDoc(value = "Controls Base Poison From Level for the Hunter Jump Boost adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int basePoisonFromLevel = 6;
    @ConfigDoc(value = "Controls Consumable for the Hunter Jump Boost adaptation.", impact = "Changing this alters the identifier or text used by the feature.")
    String consumable = "ROTTEN_FLESH";

    public Config() {
      baseCost = 1;
      costFactor = 0.5;
      initialCost = 1;
    }
  }
}
