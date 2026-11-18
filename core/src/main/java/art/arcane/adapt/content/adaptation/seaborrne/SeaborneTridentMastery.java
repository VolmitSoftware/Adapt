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

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
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
import art.arcane.adapt.content.adaptation.chronos.TemporalFreezeRegistry;
import art.arcane.adapt.localization.catalog.SeabornMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SeaborneTridentMastery extends SimpleAdaptation<SeaborneTridentMastery.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> RECALL = CommonPreferences.toggle("recall", SeabornMessages.SEABORNETRIDENTMASTERY_PREFERENCE_RECALL, CommonPreferences.Toggle.ON);

  private static final int MAX_RECALL_TICKS = 120;
  private static final double PICKUP_DISTANCE = 1.6;
  private static final Id TRIDENT = Id.minecraft("trident");
  private static final DataKey<Integer> MASTERY_LEVEL_KEY = DataKey.adapt("seaborne_trident_mastery_level", DataCodecs.INT);

  public SeaborneTridentMastery() {
    super("seaborne-trident-mastery");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.trident_mastery");
    setIcon(Id.minecraft("trident"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("trident"))
        .key("challenge_seaborne_trident_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("heart_of_the_sea"))
            .key("challenge_seaborne_trident_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_seaborne_trident_250", "seaborne.trident-mastery.trident-hits", 250, 400);
    registerMilestone("challenge_seaborne_trident_2500", "seaborne.trident-mastery.trident-hits", 2500, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, RECALL);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getDamageBonus(level), 0), 1);
    statLore(v, Form.f(getRecallSpeed(level), 1), 2);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    DamageContext context = resolveDamageContext(e);
    if (context == null) {
      return;
    }

    e.setDamage(e.damage() * (1D + getDamageBonus(context.level())));
    fx(e.entity().location().add(0D, 1.0D, 0D), FxPriority.COMBAT)
        .particle(Particles.CRIT, 8, 0D, 0D, 0D, 0.35D, 0.1D)
        .particle(Particles.BUBBLE, 6, 0D, 0D, 0D, 0.3D, 0.05D)
        .sound(Sounds.ITEM_TRIDENT_HIT, 0.6F, 1.1F);
    Tasks.runEntity(context.attacker(), () -> rewardHitOwned(context.attacker()));
  }

  private DamageContext resolveDamageContext(EntityDamageByEntityEvent e) {
    EntityRef damager = e.damager();
    if (damager instanceof ProjectileRef trident && TRIDENT.equals(trident.type())
        && trident.shooter() instanceof PlayerRef shooter) {
      Integer level = trident.data().get(MASTERY_LEVEL_KEY);
      return level == null || level <= 0 ? null : new DamageContext(shooter, level);
    }

    if (damager instanceof PlayerRef melee && melee.inventory().mainHand().type().equals(TRIDENT)) {
      int level = getActiveLevel(melee);
      return level <= 0 ? null : new DamageContext(melee, level);
    }

    return null;
  }

  private void rewardHitOwned(PlayerRef attacker) {
    if (!attacker.isOnline() || getActiveLevel(attacker) <= 0) {
      return;
    }
    addStat(attacker, "seaborne.trident-mastery.trident-hits", 1);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ProjectileLaunchEvent e) {
    ProjectileRef trident = e.projectile();
    if (!TRIDENT.equals(trident.type()) || !(trident.shooter() instanceof PlayerRef p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }
    trident.data().set(MASTERY_LEVEL_KEY, level);

    if (!getConfig().enableRecall || !preferenceEnabled(p, RECALL)) {
      return;
    }

    int delay = Math.max(2, (int) Math.round(getConfig().recallDelayTicks));
    Tasks.runEntity(trident, () -> recallTick(trident, p, level, 0), delay);
  }

  private void recallTick(ProjectileRef trident, PlayerRef p, int level, int ticksLived) {
    if (!trident.isValid() || trident.isDead() || ticksLived > MAX_RECALL_TICKS) {
      return;
    }
    UUID tridentId = trident.uuid();
    if (!Tasks.runEntity(p, () -> captureRecallTargetOwned(trident, tridentId, p, level, ticksLived))) {
      AdaptLog.warn("Trident Mastery could not schedule recall owner snapshot for " + tridentId + ".");
    }
  }

  private void captureRecallTargetOwned(ProjectileRef trident, UUID tridentId, PlayerRef p, int level, int ticksLived) {
    if (!p.isOnline() || getActiveLevel(p) <= 0 || !preferenceEnabled(p, RECALL)) {
      return;
    }
    Loc playerLocation = p.location().add(0D, 1.0D, 0D);
    if (!Tasks.runEntity(trident,
        () -> applyRecallTargetOwned(trident, tridentId, p, level, ticksLived, playerLocation))) {
      AdaptLog.warn("Trident Mastery could not schedule recall continuation for " + tridentId + ".");
    }
  }

  private void applyRecallTargetOwned(ProjectileRef trident, UUID tridentId, PlayerRef p, int level, int ticksLived,
                                      Loc playerLocation) {
    if (!trident.isValid() || trident.isDead() || ticksLived > MAX_RECALL_TICKS) {
      return;
    }
    if (TemporalFreezeRegistry.isFrozen(trident)) {
      Tasks.runEntity(trident, () -> recallTick(trident, p, level, ticksLived), 1);
      return;
    }

    Loc tridentLocation = trident.location();
    if (!tridentLocation.sameWorld(playerLocation)) {
      return;
    }

    boolean stuck = trident.isInBlock() || trident.isOnGround();
    if (!stuck && ticksLived < getFlightGraceTicks(level)) {
      scheduleNextRecall(trident, p, level, ticksLived);
      return;
    }

    Vec3 toPlayer = playerLocation.position().subtract(tridentLocation.position());
    double distance = toPlayer.length();
    if (distance <= PICKUP_DISTANCE) {
      return;
    }

    if (stuck) {
      Loc freed = tridentLocation
          .add(toPlayer.normalize().multiply(0.4D))
          .add(0D, 0.2D, 0D);
      beginReleaseTeleport(trident, tridentId, p, level, ticksLived, freed);
      return;
    }

    continueRecallOwned(trident, p, level, ticksLived, tridentLocation, toPlayer);
  }

  private void beginReleaseTeleport(ProjectileRef trident, UUID tridentId, PlayerRef p, int level, int ticksLived,
                                    Loc freed) {
    CompletableFuture<Boolean> teleport;
    try {
      teleport = trident.teleport(freed, TeleportCause.PLUGIN);
    } catch (RuntimeException error) {
      AdaptLog.error("Trident Mastery could not start release teleport for " + tridentId + ".");
      AdaptLog.error(error);
      return;
    }
    if (teleport == null) {
      return;
    }
    teleport.whenComplete((success, failure) ->
        completeReleaseTeleport(trident, tridentId, p, level, ticksLived, success, failure));
  }

  private void completeReleaseTeleport(ProjectileRef trident, UUID tridentId, PlayerRef p, int level, int ticksLived,
                                       Boolean success, Throwable failure) {
    if (failure != null) {
      AdaptLog.error("Trident Mastery release teleport failed for " + tridentId + ".");
      AdaptLog.error(failure);
    }
    if (!successfulReleaseTeleport(success, failure)) {
      return;
    }
    if (!Tasks.runEntity(trident, () -> recallTick(trident, p, level, ticksLived))) {
      AdaptLog.warn("Trident Mastery completed release teleport but could not schedule completion for "
          + tridentId + ".");
    }
  }

  private void continueRecallOwned(ProjectileRef trident, PlayerRef p, int level, int ticksLived,
                                   Loc tridentLocation, Vec3 toPlayer) {
    trident.setVelocity(toPlayer.normalize().multiply(getRecallSpeed(level)));
    if ((ticksLived & 3) == 0) {
      fx(tridentLocation, FxPriority.TRAIL)
          .particle(Particles.BUBBLE, 2, 0D, 0D, 0D, 0.1D, 0.02D)
          .sound(Sounds.ITEM_TRIDENT_RETURN, 0.25F, 1.4F);
    }

    scheduleNextRecall(trident, p, level, ticksLived);
  }

  private void scheduleNextRecall(ProjectileRef trident, PlayerRef p, int level, int ticksLived) {
    Tasks.runEntity(trident, () -> recallTick(trident, p, level, ticksLived + 1), 1);
  }

  static boolean successfulReleaseTeleport(Boolean success, Throwable failure) {
    return failure == null && Boolean.TRUE.equals(success);
  }

  private int getFlightGraceTicks(int level) {
    return Math.max(10, (int) Math.round(getConfig().flightGraceTicksBase - (getLevelPercent(level) * getConfig().flightGraceTicksReduction)));
  }

  private double getDamageBonus(int level) {
    return damageBonus(getConfig().damageBonusBase, getConfig().damageBonusFactor, getLevelPercent(level));
  }

  private double getRecallSpeed(int level) {
    return recallSpeed(getConfig().recallSpeedBase, getConfig().recallSpeedFactor, getLevelPercent(level));
  }

  static double damageBonus(double base, double factor, double levelPercent) {
    return Math.max(0D, base + (levelPercent * factor));
  }

  static double recallSpeed(double base, double factor, double levelPercent) {
    return base + (levelPercent * factor);
  }

  private record DamageContext(PlayerRef attacker, int level) {
  }

  @ConfigDescription("Tridents deal bonus damage and home back to you faster after a throw.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base bonus trident damage as a fraction.", impact = "Higher values increase trident damage at low levels.")
    double damageBonusBase = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional bonus trident damage fraction gained across levels.", impact = "Higher values greatly increase trident damage at higher levels.")
    double damageBonusFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base velocity applied when a thrown trident homes back to you.", impact = "Higher values pull thrown tridents back faster at low levels.")
    double recallSpeedBase = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional recall velocity gained across levels.", impact = "Higher values recover tridents much faster at higher levels.")
    double recallSpeedFactor = 1.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Ticks a thrown trident is allowed to fly before recall forces its return.", impact = "Higher values let tridents travel farther before homing back at low levels.")
    double flightGraceTicksBase = 50;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Flight grace ticks removed at max level.", impact = "Higher values make higher levels begin the return sooner.")
    double flightGraceTicksReduction = 30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Delay in ticks before recall first evaluates a thrown trident.", impact = "Higher values wait longer before the trident may begin returning.")
    double recallDelayTicks = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables the trident recall/homing return behavior.", impact = "Set to false to keep only the bonus damage without homing return.")
    boolean enableRecall = true;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 4;
    }
  }
}
