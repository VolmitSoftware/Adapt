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

package art.arcane.adapt.content.adaptation.excavation;

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
import art.arcane.adapt.localization.catalog.ExcavationMessages;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ExcavationEarthMover extends SimpleAdaptation<ExcavationEarthMover.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> IGNORE_PASSIVE = CommonPreferences.toggle("ignore-passive", ExcavationMessages.PREFERENCE_EXCAVATIONEARTHMOVER_IGNORE_PASSIVE, CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> FOOD_RESERVE = CommonPreferences.toggle("food-reserve", ExcavationMessages.PREFERENCE_EXCAVATIONEARTHMOVER_FOOD_RESERVE, CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> OFFHAND = CommonPreferences.toggle("offhand-empty", ExcavationMessages.PREFERENCE_EXCAVATIONEARTHMOVER_OFFHAND, CommonPreferences.Toggle.OFF);

  private static final int HARD_MAX_CANDIDATES_PER_ACTIVATION = 32;
  private static final int HARD_MAX_AFFECTED_PER_ACTIVATION = 16;
  private static final int HARD_MAX_TARGET_FX_PER_ACTIVATION = 12;
  private static final int BATCH_TIMEOUT_TICKS = 20;
  private static final Id DIRT = Id.minecraft("dirt");
  private static final Id SLOWNESS = Id.minecraft("slowness");
  private final Cooldowns cooldowns = cooldowns();

  public ExcavationEarthMover() {
    super("excavation-earth-mover");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("dirt"));
    setInterval(3730);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("dirt"))
        .key("challenge_excavation_earthmover_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_excavation_earthmover_250", "excavation.earth-mover.waves-unleashed", 250, 450);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, IGNORE_PASSIVE, FOOD_RESERVE, OFFHAND);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getRadius(level), 1), 1);
    statLore(v, Form.pc(getDamageMultiplier(level), 0), 2);
    statLore(v, Form.pc(getForce(level), 0), 3);
    statLore(v, Form.duration(getSlowTicks(level) * 50D, 1), 4);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 5);
    statLore(v, C.RED, "- ", getConfig().hungerCost, 6);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if (action != InteractAction.RIGHT_CLICK_AIR && action != InteractAction.RIGHT_CLICK_BLOCK) {
      return;
    }

    if (e.hand() != null && e.hand() != Hand.MAIN_HAND) {
      return;
    }

    PlayerRef p = e.player();
    if (!p.isSneaking() || preferenceEnabled(p, OFFHAND) && !p.inventory().offHand().type().isAir()) {
      return;
    }

    if (!isShovel(p.inventory().mainHand())) {
      return;
    }

    int hungerCost = getConfig().hungerCost;
    if (p.foodLevel() - hungerCost < (preferenceEnabled(p, FOOD_RESERVE) ? 8 : 0)) {
      return;
    }

    BlockActionContext context = resolveInteractContext(p, p.location());
    if (context == null) {
      return;
    }

    int level = context.level();
    if (!cooldowns.isReady(p.uuid(), getCooldownMillis(level))) {
      return;
    }

    cooldowns.mark(p.uuid());
    p.setFoodLevel(Math.max(0, p.foodLevel() - hungerCost));

    double radius = getRadius(level);
    double force = getForce(level);
    double damage = DamageScaling.scaledDamage(
        shovelDamage(p.inventory().mainHand().type()),
        getDamageMultiplier(level)
    );
    int slowTicks = getSlowTicks(level);
    int slowAmplifier = getSlowAmplifier(level);
    Loc origin = p.location();
    BlockStateRef dirtData = AdaptCore.platform().host().items().blockState(DIRT);
    List<LivingRef> candidates = collectCandidates(p, origin, radius);
    renderWave(origin, radius);
    addStat(p, "excavation.earth-mover.waves-unleashed", 1);

    if (candidates.isEmpty()) {
      fx(origin, FxPriority.COMBAT).sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.5f);
      return;
    }

    EarthMoverBatch batch = new EarthMoverBatch(p, origin, radius, getConfig().verticalRange, force,
        damage, getConfig().liftVelocity, slowTicks, slowAmplifier, getAffectedLimit(), getTargetFxLimit(),
        getConfig().xpPerMobHit, dirtData, candidates.size());
    for (LivingRef enemy : candidates) {
      if (!Tasks.runEntity(enemy, () -> inspectCandidateOwned(batch, enemy))) {
        batch.complete();
      }
    }
    Tasks.runEntity(p, batch::finishTimedOut, BATCH_TIMEOUT_TICKS);
  }

  private List<LivingRef> collectCandidates(PlayerRef player, Loc origin, double radius) {
    int limit = getCandidateLimit();
    List<LivingRef> candidates = new ArrayList<>(limit);
    for (EntityRef entity : origin.world().nearbyEntities(
        origin.position(), radius, getConfig().verticalRange, radius)) {
      if (!isEarthMoverTarget(entity) || !(entity instanceof LivingRef living)
          || !AdaptationDamageTargets.allows(entity, getConfig().ignorePassiveMobs || preferenceEnabled(player, IGNORE_PASSIVE))) {
        continue;
      }
      candidates.add(living);
      if (candidates.size() >= limit) {
        break;
      }
    }
    return candidates;
  }

  private void inspectCandidateOwned(EarthMoverBatch batch, LivingRef enemy) {
    if (batch.isFinalized() || !batch.hasAffectedCapacity()) {
      batch.complete();
      return;
    }
    Loc targetLocation = validTargetLocation(batch, enemy);
    if (targetLocation == null) {
      batch.complete();
      return;
    }

    if (!Tasks.runEntity(batch.player, () -> authorizeCandidate(batch, enemy, targetLocation))) {
      batch.complete();
    }
  }

  private void authorizeCandidate(EarthMoverBatch batch, LivingRef enemy, Loc targetLocation) {
    if (batch.isFinalized() || !batch.player.isOnline() || !canPVE(batch.player, targetLocation)) {
      batch.complete();
      return;
    }

    if (!Tasks.runEntity(enemy, () -> applyImpactOwned(batch, enemy))) {
      batch.complete();
    }
  }

  private void applyImpactOwned(EarthMoverBatch batch, LivingRef enemy) {
    if (batch.isFinalized()) {
      return;
    }
    Loc targetLocation = validTargetLocation(batch, enemy);
    if (targetLocation == null || !batch.claimAffected()) {
      batch.complete();
      return;
    }

    Vec3 direction = targetLocation.position().subtract(batch.origin.position()).withY(0);
    if (direction.lengthSquared() < 0.01D) {
      direction = new Vec3(1, 0, 0);
    }

    direction = direction.normalize().multiply(batch.force).withY(batch.liftVelocity);
    int previousNoDamageTicks = enemy.noDamageTicks();
    double healthBefore = enemy.health() + enemy.absorption();
    enemy.setNoDamageTicks(0);
    enemy.damage(batch.damage, batch.player);
    double healthAfter = enemy.isDead() ? 0D : enemy.health() + enemy.absorption();
    if (healthAfter >= healthBefore) {
      enemy.setNoDamageTicks(previousNoDamageTicks);
      batch.complete();
      return;
    }

    batch.markAffected();
    enemy.setVelocity(direction);
    enemy.addEffect(new EffectInstance(SLOWNESS, batch.slowTicks, batch.slowAmplifier, false, true, true));
    if (batch.claimTargetFx() && batch.dirtData != null) {
      fx(enemy, FxPriority.COMBAT)
          .particle(Particles.blockCrack(batch.dirtData), 4, 0, 0.1D, 0, 0.2D, 0.05D);
    }
    batch.complete();
  }

  private Loc validTargetLocation(EarthMoverBatch batch, LivingRef enemy) {
    if (!enemy.isValid() || enemy.isDead() || isProtectedFriendly(null, enemy)
        || !AdaptationDamageTargets.allows(enemy, getConfig().ignorePassiveMobs)) {
      return null;
    }

    Loc location = enemy.location();
    if (location.world() == null || batch.origin.world() == null
        || !location.world().uid().equals(batch.origin.world().uid())
        || Math.abs(location.x() - batch.origin.x()) > batch.radius
        || Math.abs(location.y() - batch.origin.y()) > batch.verticalRange
        || Math.abs(location.z() - batch.origin.z()) > batch.radius) {
      return null;
    }
    return location;
  }

  private void renderWave(Loc center, double radius) {
    ItemRef dirt = AdaptCore.platform().host().items().create(DIRT);
    timeline(center)
        .duration(8)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(32)
        .frame((fx, tick, progress) -> {
          double r = 0.4D + ((radius - 0.4D) * progress);
          int points = 10 + (int) (progress * 14);
          fx.ring(Particles.itemCrack(dirt), r, points, 0.2D);
          fx.ring(Particles.CLOUD, r, Math.max(8, points / 2), 0.15D);
          if (tick == 0) {
            fx.chord(Sounds.BLOCK_ROOTED_DIRT_BREAK, 1.0f, 0.6f, Sounds.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.4f, 0.5f, Sounds.ENTITY_IRON_GOLEM_ATTACK, 0.5f, 0.6f);
          }
          if (tick == 7 && radius > 5.0D) {
            fx.particle(Particles.EXPLOSION, 1, 0, 0.3D, 0, 0, 0);
          }
        })
        .start();
  }

  private double getRadius(int level) {
    return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
  }

  private double getForce(int level) {
    return getConfig().forceBase + (getLevelPercent(level) * getConfig().forceFactor);
  }

  private double getDamageMultiplier(int level) {
    return Math.max(0D, getConfig().damageMultiplierBase
        + (getLevelPercent(level) * getConfig().damageMultiplierFactor));
  }

  static double shovelDamage(Id material) {
    if (!material.isMinecraft()) {
      return 0D;
    }
    return switch (material.path()) {
      case "wooden_shovel", "golden_shovel" -> 2.5D;
      case "stone_shovel", "copper_shovel" -> 3.5D;
      case "iron_shovel" -> 4.5D;
      case "diamond_shovel" -> 5.5D;
      case "netherite_shovel" -> 6.5D;
      default -> 0D;
    };
  }

  static boolean isEarthMoverTarget(EntityRef entity) {
    return entity.has(EntityTrait.ENEMY);
  }

  private int getSlowTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().slowTicksBase + (getLevelPercent(level) * getConfig().slowTicksFactor)));
  }

  private int getSlowAmplifier(int level) {
    return Math.max(0, (int) Math.round(getLevelPercent(level) * getConfig().slowAmplifierMax));
  }

  private long getCooldownMillis(int level) {
    return scaledCooldown(
        getConfig().cooldownMillisBase,
        getConfig().cooldownMillisFactor,
        getLevelPercent(level),
        getConfig().cooldownScale
    );
  }

  static long scaledCooldown(double base, double factor, double levelPercent, double scale) {
    double safeScale = Double.isFinite(scale) ? Math.max(0D, Math.min(1D, scale)) : 0.5D;
    double configured = base - (Math.max(0D, Math.min(1D, levelPercent)) * factor);
    return Math.max(500L, Math.round(configured * safeScale));
  }

  private int getCandidateLimit() {
    return Math.max(1, Math.min(getConfig().maxCandidatesPerActivation, HARD_MAX_CANDIDATES_PER_ACTIVATION));
  }

  private int getAffectedLimit() {
    return Math.max(1, Math.min(Math.min(getConfig().maxAffectedPerActivation, getCandidateLimit()), HARD_MAX_AFFECTED_PER_ACTIVATION));
  }

  private int getTargetFxLimit() {
    return Math.max(0, Math.min(getConfig().maxTargetFxPerActivation, HARD_MAX_TARGET_FX_PER_ACTIVATION));
  }


  @ConfigDescription("Sneak-right-click the air with a shovel to damage, knock back, and slow hostile mobs with damage based on shovel tier.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Exclude passive and neutral mobs from area damage and its secondary effects.", impact = "When enabled, protected mobs do not consume target limits. Direct attacks and player targeting are unchanged.")
    boolean ignorePassiveMobs = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Vertical Range for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double verticalRange = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Force Base for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double forceBase = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Force Factor for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double forceFactor = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base multiplier applied to the held shovel's attack damage for Earth Mover.", impact = "Higher values increase wave damage at every adaptation level.")
    double damageMultiplierBase = 0.75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional held-shovel damage multiplier gained across Earth Mover levels.", impact = "Higher values make wave damage scale more strongly with adaptation level.")
    double damageMultiplierFactor = 0.75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Lift Velocity for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double liftVelocity = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Slow Ticks Base for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double slowTicksBase = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Slow Ticks Factor for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double slowTicksFactor = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Slow Amplifier Max for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double slowAmplifierMax = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisBase = 16000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisFactor = 8000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Multiplier applied to Earth Mover cooldowns after level scaling.", impact = "The default 0.5 halves the cooldown at every adaptation level.")
    double cooldownScale = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hunger points consumed per earth wave.", impact = "Higher values drain more food per wave; activation fails when food is below the cost. Set to 0 to disable the hunger cost.")
    int hungerCost = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Mob Hit for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerMobHit = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum hostile mobs inspected by one earth wave.", impact = "Lower values reduce dense-entity scheduling; values are clamped to an absolute maximum of 32.")
    int maxCandidatesPerActivation = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum hostile mobs launched by one earth wave.", impact = "Lower values cap combat work and rewards in dense farms; values are clamped to an absolute maximum of 16.")
    int maxAffectedPerActivation = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum launched mobs that receive individual block-particle effects.", impact = "Lower values reduce particle dispatch while preserving the main wave effect; values are clamped to an absolute maximum of 12.")
    int maxTargetFxPerActivation = 8;

    public Config() {
      baseCost = 6;
      costFactor = 0.8;
      initialCost = 7;
    }
  }

  static final class DamageScaling {
    private DamageScaling() {
    }

    static double scaledDamage(double shovelDamage, double multiplier) {
      if (!Double.isFinite(shovelDamage) || !Double.isFinite(multiplier)) {
        return 0D;
      }
      return Math.max(0D, shovelDamage) * Math.max(0D, multiplier);
    }
  }

  private final class EarthMoverBatch {
    private final PlayerRef player;
    private final Loc origin;
    private final double radius;
    private final double verticalRange;
    private final double force;
    private final double damage;
    private final double liftVelocity;
    private final int slowTicks;
    private final int slowAmplifier;
    private final int affectedLimit;
    private final int targetFxLimit;
    private final double xpPerMobHit;
    private final BlockStateRef dirtData;
    private final AtomicInteger remaining;
    private final AtomicInteger admitted = new AtomicInteger();
    private final AtomicInteger affected = new AtomicInteger();
    private final AtomicInteger targetFx = new AtomicInteger();
    private final AtomicBoolean finalized = new AtomicBoolean();

    private EarthMoverBatch(PlayerRef player, Loc origin, double radius, double verticalRange, double force,
                            double damage, double liftVelocity, int slowTicks, int slowAmplifier, int affectedLimit,
                            int targetFxLimit, double xpPerMobHit, BlockStateRef dirtData, int candidates) {
      this.player = player;
      this.origin = origin;
      this.radius = radius;
      this.verticalRange = verticalRange;
      this.force = force;
      this.damage = damage;
      this.liftVelocity = liftVelocity;
      this.slowTicks = slowTicks;
      this.slowAmplifier = slowAmplifier;
      this.affectedLimit = affectedLimit;
      this.targetFxLimit = targetFxLimit;
      this.xpPerMobHit = xpPerMobHit;
      this.dirtData = dirtData;
      remaining = new AtomicInteger(candidates);
    }

    private synchronized boolean claimAffected() {
      if (finalized.get() || admitted.get() >= affectedLimit) {
        return false;
      }
      admitted.incrementAndGet();
      return true;
    }

    private boolean hasAffectedCapacity() {
      return admitted.get() < affectedLimit;
    }

    private void markAffected() {
      affected.incrementAndGet();
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
      Tasks.runEntity(player, this::finish);
    }

    private synchronized boolean markFinalized() {
      return finalized.compareAndSet(false, true);
    }

    private boolean isFinalized() {
      return finalized.get();
    }

    private void finishTimedOut() {
      if (markFinalized()) {
        finish();
      }
    }

    private void finish() {
      if (!player.isOnline()) {
        return;
      }

      int hit = affected.get();
      if (hit <= 0) {
        fx(origin, FxPriority.COMBAT).sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.5f);
        return;
      }

      fx(origin, FxPriority.COMBAT).sound(Sounds.ENTITY_HOSTILE_BIG_FALL, 0.4f, 0.8f);
      addStat(player, "excavation.earth-mover.mobs-launched", hit);
      xp(player, hit * xpPerMobHit);
    }
  }
}
