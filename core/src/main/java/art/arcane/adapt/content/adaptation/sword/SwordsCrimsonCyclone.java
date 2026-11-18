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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.content.adaptation.sword.effects.DamagingBleedEffect;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SwordsCrimsonCyclone extends SimpleAdaptation<SwordsCrimsonCyclone.Config> {
  private static final int HARD_MAX_CANDIDATES_PER_ACTIVATION = 32;
  private static final int HARD_MAX_AFFECTED_PER_ACTIVATION = 16;
  private static final int HARD_MAX_TARGET_FX_PER_ACTIVATION = 12;
  private static final Rgb CRIMSON = Rgb.of(0x9E1414);
  private static final Id CRIMSON_ROOTS = Id.minecraft("crimson_roots");
  private static final Id VOID_AIR = Id.minecraft("void_air");
  private final Cooldowns cooldowns = cooldowns();

  public SwordsCrimsonCyclone() {
    super("sword-crimson-cyclone");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("netherite_sword"));
    setInterval(2400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_sword"))
        .key("challenge_swords_cyclone_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_sword"))
            .key("challenge_swords_cyclone_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_swords_cyclone_500", "swords.crimson-cyclone.mobs-hit", 500, 400);
    registerMilestone("challenge_swords_cyclone_5k", "swords.crimson-cyclone.mobs-hit", 5000, 1500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("netherite_sword"))
        .key("challenge_swords_cyclone_6")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, SwordPreferences.IGNORE_PASSIVE, SwordPreferences.VISUALS, SwordPreferences.SNEAK);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getRadius(level)), 1);
    statLore(v, Form.f(getBaseDamage(level), 2), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTicks(level) * 50D, 1), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    MeleeContext combat = resolveMeleeContext(e, this::isSword);
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

    double radius = getRadius(level);
    double damage = getBaseDamage(level);
    Loc center = primaryTarget.location();
    int targetFxLimit = preferenceEnabled(p, SwordPreferences.VISUALS) ? getTargetFxLimit() : 0;
    p.setFoodLevel(Math.max(0, p.foodLevel() - hungerCost));
    cooldowns.mark(playerId);

    e.setDamage(e.damage() + damage);
    applyBleed(primaryTarget, p, level, targetFxLimit > 0);
    if (targetFxLimit > 0) {
      crimsonSpark(primaryTarget);
    }

    List<LivingRef> candidates = collectCandidates(p, primaryTarget, center, radius);
    if (candidates.isEmpty() || getAffectedLimit() <= 1) {
      finishCyclone(p, center, radius, level, 1, getConfig().xpPerTargetHit);
      return;
    }

    CycloneBatch batch = new CycloneBatch(p, center, radius, level, damage, getAffectedLimit(),
        targetFxLimit, getConfig().xpPerTargetHit, targetFxLimit > 0, candidates.size());
    for (LivingRef target : candidates) {
      if (!Tasks.runEntity(target, () -> inspectCandidateOwned(batch, target))) {
        batch.complete();
      }
    }
    Tasks.runEntity(p, batch::finishTimedOut, 3);
  }

  private List<LivingRef> collectCandidates(PlayerRef player, LivingRef primaryTarget, Loc center, double radius) {
    int limit = getCandidateLimit();
    List<LivingRef> candidates = new ArrayList<>(limit);
    for (EntityRef entity : center.world().nearbyEntities(center.position(), radius, radius, radius)) {
      if (!(entity instanceof LivingRef candidate)
          || candidate.uuid().equals(player.uuid()) || candidate.uuid().equals(primaryTarget.uuid())
          || !AdaptationDamageTargets.allows(candidate, getConfig().ignorePassiveMobs || preferenceEnabled(player, SwordPreferences.IGNORE_PASSIVE))) {
        continue;
      }
      candidates.add(candidate);
      if (candidates.size() >= limit) {
        break;
      }
    }
    return candidates;
  }

  private void inspectCandidateOwned(CycloneBatch batch, LivingRef target) {
    if (batch.isFinalized() || !batch.hasAffectedCapacity()) {
      batch.complete();
      return;
    }
    Loc targetLocation = validTargetLocation(batch, target);
    if (targetLocation == null) {
      batch.complete();
      return;
    }

    boolean playerTarget = target instanceof PlayerRef;
    if (!Tasks.runEntity(batch.player, () -> authorizeCandidate(batch, target, targetLocation, playerTarget))) {
      batch.complete();
    }
  }

  private void authorizeCandidate(CycloneBatch batch, LivingRef target, Loc targetLocation, boolean playerTarget) {
    if (batch.isFinalized() || !batch.player.isOnline()) {
      batch.complete();
      return;
    }

    boolean allowed = playerTarget ? canPVP(batch.player, targetLocation) : canPVE(batch.player, targetLocation);
    if (!allowed || !Tasks.runEntity(target, () -> applyCycloneOwned(batch, target))) {
      batch.complete();
    }
  }

  private void applyCycloneOwned(CycloneBatch batch, LivingRef target) {
    if (batch.isFinalized()) {
      return;
    }
    if (validTargetLocation(batch, target) == null || !batch.claimAffected()) {
      batch.complete();
      return;
    }

    target.damage(batch.damage, batch.player);
    boolean visual = batch.claimTargetFx();
    applyBleed(target, batch.player, batch.level, visual);
    if (visual) {
      crimsonSpark(target);
    }
    batch.complete();
  }

  private Loc validTargetLocation(CycloneBatch batch, LivingRef target) {
    if (!target.isValid() || target.isDead() || isProtectedFriendly(null, target)
        || !AdaptationDamageTargets.allows(target, getConfig().ignorePassiveMobs)) {
      return null;
    }
    UUID owner = target.tameOwner();
    if (owner != null && batch.playerId.equals(owner)) {
      return null;
    }

    Loc location = target.location();
    if (!location.sameWorld(batch.center)
        || Math.abs(location.x() - batch.center.x()) > batch.radius
        || Math.abs(location.y() - batch.center.y()) > batch.radius
        || Math.abs(location.z() - batch.center.z()) > batch.radius) {
      return null;
    }
    return location;
  }

  private void finishCyclone(PlayerRef player, Loc center, double radius, int level, int hits, double xpPerTargetHit) {
    double maxRadius = radius;
    int ringPoints = Math.min(24, 10 + (level * 2));
    timeline(center)
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
    xp(player, hits * xpPerTargetHit);
    addStat(player, "swords.crimson-cyclone.mobs-hit", hits);

    if (hits >= 6 && grantOnce(player, "challenge_swords_cyclone_6")) {
      fx(player.location().add(0, 1, 0), FxPriority.TRANSITION)
          .burst(Particles.TOTEM, 12, 0.4D)
          .particle(Particles.FLASH, 1, 0, 0, 0, 0, 0)
          .sound(Sounds.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1F);
    }
  }

  private void crimsonSpark(LivingRef target) {
    fx(target.location().add(0, 0.8D, 0), FxPriority.COMBAT)
        .particle(Particles.CRIMSON_SPORE, 3, 0, 0, 0, 0.3D, 0.01D)
        .dustBurst(CRIMSON, 1, 0.2D, 1.0F);
  }

  static boolean isCritTrigger(boolean critical) {
    return critical;
  }

  private boolean applyDurabilityCost(PlayerRef player, ItemRef hand, int durabilityCost) {
    int max = AdaptCore.platform().host().items().maxDurability(hand.type());
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

  private void applyBleed(LivingRef target, PlayerRef source, int level, boolean visual) {
    DamagingBleedEffect.DamageContext context = new DamagingBleedEffect.DamageContext(
        getBleedDamagePerProc(level), target, source);
    new DamagingBleedEffect(context).start(visual && getConfig().showBleedParticles ? CRIMSON_ROOTS : VOID_AIR,
        Math.max(4, (int) Math.ceil(getBleedTicks(level) / 5D)), 5);
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
    return Math.max(1, Math.min(getConfig().maxCandidatesPerActivation, HARD_MAX_CANDIDATES_PER_ACTIVATION));
  }

  private int getAffectedLimit() {
    return Math.max(1, Math.min(Math.min(getConfig().maxAffectedPerActivation, getCandidateLimit() + 1), HARD_MAX_AFFECTED_PER_ACTIVATION));
  }

  private int getTargetFxLimit() {
    return Math.max(0, Math.min(getConfig().maxTargetFxPerActivation, HARD_MAX_TARGET_FX_PER_ACTIVATION));
  }


  @ConfigDescription("Land a sword crit while falling to unleash a bleeding crimson cyclone around your target.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Exclude passive and neutral mobs from area damage and its secondary effects.", impact = "When enabled, protected mobs do not consume target limits. Direct attacks and player targeting are unchanged.")
    boolean ignorePassiveMobs = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Show Bleed Particles for the Swords Crimson Cyclone adaptation.", impact = "True enables this behavior and false disables it.")
    boolean showBleedParticles = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 2.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 2.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Damage for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseDamage = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageFactor = 4.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bleed Ticks Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bleedTicksBase = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bleed Ticks Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bleedTicksFactor = 90;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bleed Damage Per Proc Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bleedDamagePerProcBase = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bleed Damage Per Proc Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bleedDamagePerProcFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Hunger Cost Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hungerCostBase = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Hunger Cost Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hungerCostFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Durability Cost Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durabilityCostBase = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Durability Cost Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durabilityCostFactor = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 320;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksFactor = 160;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Target Hit for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerTargetHit = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum secondary living targets inspected by one crimson cyclone.", impact = "Lower values reduce dense-entity scheduling; values are clamped to an absolute maximum of 32.")
    int maxCandidatesPerActivation = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum total targets hit by one crimson cyclone, including the primary target.", impact = "Lower values cap combat work and rewards in dense groups; values are clamped to an absolute maximum of 16.")
    int maxAffectedPerActivation = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum hit targets that receive individual crimson spark effects.", impact = "Lower values reduce particle dispatch while preserving the cyclone ring; values are clamped to an absolute maximum of 12.")
    int maxTargetFxPerActivation = 9;

    public Config() {
      baseCost = 5;
      costFactor = 0.76;
      initialCost = 5;
    }
  }

  private final class CycloneBatch {
    private final PlayerRef player;
    private final UUID playerId;
    private final Loc center;
    private final double radius;
    private final int level;
    private final double damage;
    private final int affectedLimit;
    private final int targetFxLimit;
    private final double xpPerTargetHit;
    private final AtomicInteger remaining;
    private final AtomicInteger affected = new AtomicInteger(1);
    private final AtomicInteger targetFx;
    private final AtomicBoolean finalized = new AtomicBoolean();

    private CycloneBatch(PlayerRef player, Loc center, double radius, int level, double damage,
                         int affectedLimit, int targetFxLimit, double xpPerTargetHit, boolean primaryFx,
                         int candidates) {
      this.player = player;
      playerId = player.uuid();
      this.center = center;
      this.radius = radius;
      this.level = level;
      this.damage = damage;
      this.affectedLimit = affectedLimit;
      this.targetFxLimit = targetFxLimit;
      this.xpPerTargetHit = xpPerTargetHit;
      remaining = new AtomicInteger(candidates);
      targetFx = new AtomicInteger(primaryFx ? 1 : 0);
    }

    private synchronized boolean claimAffected() {
      if (finalized.get() || affected.get() >= affectedLimit) {
        return false;
      }
      affected.incrementAndGet();
      return true;
    }

    private boolean hasAffectedCapacity() {
      return affected.get() < affectedLimit;
    }

    private boolean claimTargetFx() {
      return claim(targetFx, targetFxLimit);
    }

    private boolean claim(AtomicInteger counter, int limit) {
      int current = counter.get();
      while (current < limit) {
        if (counter.compareAndSet(current, current + 1)) {
          return true;
        }
        current = counter.get();
      }
      return false;
    }

    private void complete() {
      if (finalized.get() || remaining.decrementAndGet() != 0 || !markFinalized()) {
        return;
      }
      Tasks.runEntity(player, () -> finishCyclone(player, center, radius, level, affected.get(), xpPerTargetHit));
    }

    private synchronized boolean markFinalized() {
      return finalized.compareAndSet(false, true);
    }

    private boolean isFinalized() {
      return finalized.get();
    }

    private void finishTimedOut() {
      if (markFinalized()) {
        finishCyclone(player, center, radius, level, affected.get(), xpPerTargetHit);
      }
    }
  }
}
