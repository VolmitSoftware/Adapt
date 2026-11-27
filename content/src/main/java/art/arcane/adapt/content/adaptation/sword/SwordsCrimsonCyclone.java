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

package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;
import java.util.UUID;

public class SwordsCrimsonCyclone extends SimpleAdaptation<SwordsCrimsonCyclone.Config> {
  public static final Id ID = Id.adapt("sword/crimson_cyclone");
  private static final Rgb CRIMSON = Rgb.of(0x9E1414);
  private final Cooldowns cooldowns = cooldowns();
  private final SwordCyclone cyclone = new SwordCyclone(this);

  public SwordsCrimsonCyclone() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("netherite_sword"));
    setInterval(2400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_sword"))
        .key("challenge_swords_cyclone_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_sword"))
            .key("challenge_swords_cyclone_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_swords_cyclone_500", "swords.crimson-cyclone.mobs-hit", 500, 400);
    registerMilestone("challenge_swords_cyclone_5k", "swords.crimson-cyclone.mobs-hit", 5000, 1500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("netherite_sword"))
        .key("challenge_swords_cyclone_6")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, SwordPreferences.IGNORE_PASSIVE, SwordPreferences.VISUALS, SwordPreferences.SNEAK);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getRadius(level), 1), 1);
    statLore(v, Format.f(getBaseDamage(level), 2), 2);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getCooldownTicks(level) * 50D, 1), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    MeleeContext combat = resolveMeleeContext(e, ItemKinds::isSword);
    if (combat == null) {
      return;
    }

    PlayerRef p = combat.attacker();
    LivingRef primaryTarget = combat.target();
    ItemRef hand = combat.mainHand();
    if ((preferenceEnabled(p, SwordPreferences.SNEAK) && !p.isSneaking()) || !isCritTrigger(e.isCritical())) {
      return;
    }

    int level = combat.level();
    UUID playerId = p.uuid();
    int cooldownTicks = getCooldownTicks(level);
    if (!cooldowns.isReady(playerId, cooldownTicks * 50L)) {
      return;
    }
    int hungerCost = getHungerCost(level);
    if (p.foodLevel() < hungerCost) {
      return;
    }

    if (!applyDurabilityCost(p, hand, getDurabilityCost(level))) {
      return;
    }

    double damage = getBaseDamage(level);
    int targetFxLimit = preferenceEnabled(p, SwordPreferences.VISUALS) ? getTargetFxLimit() : 0;
    p.setFoodLevel(Math.max(0, p.foodLevel() - hungerCost));
    cooldowns.mark(playerId, cooldownTicks * 50L);

    e.setDamage(e.damage() + damage);
    int candidateLimit = getCandidateLimit();
    cyclone.release(new SwordCyclone.Strike(p, primaryTarget, level, getRadius(level), damage, candidateLimit,
        SwordCyclone.affectedLimit(getConfig().maxAffectedPerActivation, candidateLimit), targetFxLimit,
        getConfig().ignorePassiveMobs || preferenceEnabled(p, SwordPreferences.IGNORE_PASSIVE),
        getConfig().ignorePassiveMobs, getBleedDamagePerProc(level), getBleedTicks(level),
        getConfig().showBleedParticles), this::finishCyclone);
  }

  private void finishCyclone(PlayerRef player, List<LivingRef> hit) {
    int hits = hit.size();
    xp(player, hits * getConfig().xpPerTargetHit);
    addStat(player, "swords.crimson-cyclone.mobs-hit", hits);

    if (hits >= 6 && grantOnce(player, "challenge_swords_cyclone_6")) {
      fx(player.location().add(0, 1, 0), FxPriority.TRANSITION)
          .burst(Particles.TOTEM, 12, 0.4D)
          .particle(Particles.FLASH, 1, 0, 0, 0, 0, 0)
          .sound(Sounds.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1F);
    }
  }

  static void ringFx(Adaptation<?> owner, Loc center, double maxRadius, int ringPoints) {
    FxTimeline.at(owner, center)
        .duration(8)
        .priority(FxPriority.COMBAT)
        .cullRadius(Math.min(48D, maxRadius + 16D))
        .frame((fx, tick, progress) -> {
          double growth = tick <= 3 ? (tick + 1) / 4.0D : (8 - tick) / 4.0D;
          fx.ring(Particles.CRIMSON_SPORE, maxRadius * growth, ringPoints, 1.0D);
          if (tick == 0) {
            fx.sound(Sounds.ENTITY_PLAYER_ATTACK_SWEEP, 1F, 0.6F);
          } else if (tick == 2) {
            fx.sound(Sounds.ENTITY_ENDER_DRAGON_FLAP, 0.5F, 1.2F);
          } else if (tick == 4) {
            fx.sound(Sounds.ENTITY_WITHER_HURT, 0.65F, 1.45F);
          }
        })
        .start();
  }

  static void sparkFx(Adaptation<?> owner, LivingRef target) {
    Fx.now(owner, target.location().add(0, 0.8D, 0), FxPriority.COMBAT)
        .particle(Particles.CRIMSON_SPORE, 3, 0, 0, 0, 0.3D, 0.01D)
        .dustBurst(CRIMSON, 1, 0.2D, 1.0F);
  }

  static boolean isCritTrigger(boolean critical) {
    return critical;
  }

  private boolean applyDurabilityCost(PlayerRef player, ItemRef hand, int durabilityCost) {
    int max = AdaptApi.platform().host().items().maxDurability(hand.type());
    if (max <= 0) {
      return true;
    }

    int next = hand.damage() + durabilityCost;
    if (next >= max) {
      return false;
    }

    hand.setDamage(next);
    player.inventory().setMainHand(hand);
    return true;
  }

  private double getRadius(int level) {
    return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
  }

  private double getBaseDamage(int level) {
    return getConfig().baseDamage + (getLevelPercent(level) * getConfig().damageFactor);
  }

  private int getBleedTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().bleedTicksBase + (getLevelPercent(level) * getConfig().bleedTicksFactor)));
  }

  private double getBleedDamagePerProc(int level) {
    return Math.max(0.01, getConfig().bleedDamagePerProcBase + (getLevelPercent(level) * getConfig().bleedDamagePerProcFactor));
  }

  private int getHungerCost(int level) {
    return Math.max(1, (int) Math.round(getConfig().hungerCostBase - (getLevelPercent(level) * getConfig().hungerCostFactor)));
  }

  private int getDurabilityCost(int level) {
    return Math.max(1, (int) Math.round(getConfig().durabilityCostBase - (getLevelPercent(level) * getConfig().durabilityCostFactor)));
  }

  private int getCooldownTicks(int level) {
    return Math.max(40, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
  }

  private int getCandidateLimit() {
    return SwordCyclone.candidateLimit(getConfig().maxCandidatesPerActivation);
  }

  private int getTargetFxLimit() {
    return SwordCyclone.targetFxLimit(getConfig().maxTargetFxPerActivation);
  }


  @ConfigDescription("Land a sword crit while falling to unleash a bleeding crimson cyclone around your target.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Exclude passive and neutral mobs from area damage and its secondary effects.", impact = "When enabled, protected mobs do not consume target limits. Direct attacks and player targeting are unchanged.")
    boolean ignorePassiveMobs = false;
    @ConfigDoc(value = "Controls Show Bleed Particles for the Swords Crimson Cyclone adaptation.", impact = "True enables this behavior and false disables it.")
    boolean showBleedParticles = true;
    @ConfigDoc(value = "Controls Radius Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 2.6;
    @ConfigDoc(value = "Controls Radius Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 2.4;
    @ConfigDoc(value = "Controls Base Damage for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseDamage = 2.0;
    @ConfigDoc(value = "Controls Damage Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageFactor = 4.0;
    @ConfigDoc(value = "Controls Bleed Ticks Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bleedTicksBase = 40;
    @ConfigDoc(value = "Controls Bleed Ticks Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bleedTicksFactor = 90;
    @ConfigDoc(value = "Controls Bleed Damage Per Proc Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bleedDamagePerProcBase = 0.35;
    @ConfigDoc(value = "Controls Bleed Damage Per Proc Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bleedDamagePerProcFactor = 0.45;
    @ConfigDoc(value = "Controls Hunger Cost Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hungerCostBase = 2;
    @ConfigDoc(value = "Controls Hunger Cost Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hungerCostFactor = 2;
    @ConfigDoc(value = "Controls Durability Cost Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durabilityCostBase = 3;
    @ConfigDoc(value = "Controls Durability Cost Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durabilityCostFactor = 1.5;
    @ConfigDoc(value = "Controls Cooldown Ticks Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 320;
    @ConfigDoc(value = "Controls Cooldown Ticks Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksFactor = 160;
    @ConfigDoc(value = "Controls Xp Per Target Hit for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerTargetHit = 10;
    @ConfigDoc(value = "Maximum secondary living targets inspected by one crimson cyclone.", impact = "Lower values reduce dense-entity scheduling; values are clamped to an absolute maximum of 32.")
    int maxCandidatesPerActivation = 16;
    @ConfigDoc(value = "Maximum total targets hit by one crimson cyclone, including the primary target.", impact = "Lower values cap combat work and rewards in dense groups; values are clamped to an absolute maximum of 16.")
    int maxAffectedPerActivation = 12;
    @ConfigDoc(value = "Maximum hit targets that receive individual crimson spark effects.", impact = "Lower values reduce particle dispatch while preserving the cyclone ring; values are clamped to an absolute maximum of 12.")
    int maxTargetFxPerActivation = 9;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      initialCost = 3;
    }
  }
}
