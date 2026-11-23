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

package art.arcane.adapt.content.adaptation.rift;

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
import art.arcane.adapt.api.projectile.ProjectileCooperativeKeys;
import art.arcane.adapt.localization.catalog.RiftMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.data.DataValue;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RiftPearlRebound extends SimpleAdaptation<RiftPearlRebound.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> REBOUND = CommonPreferences.toggle("rebound", RiftMessages.RIFTPEARLREBOUND_PREFERENCE_REBOUND, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> DAMAGE = CommonPreferences.toggle("damage-reduction", RiftMessages.RIFTPEARLREBOUND_PREFERENCE_DAMAGE, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("sneak", RiftMessages.RIFTPEARLREBOUND_PREFERENCE_SNEAK, CommonPreferences.Toggle.OFF);

  private static final double HARD_MAX_DAMAGE_REDUCTION = 0.9D;
  private static final double HARD_MAX_AIM_BIAS = 0.9D;
  private static final double MIN_OUTWARD_REBOUND = 0.35D;
  private static final Id ENDER_PEARL = Id.minecraft("ender_pearl");

  private final DataKey<Byte> reboundedKey;
  private final DataKey<Integer> reboundLevelKey;

  public RiftPearlRebound() {
    super("rift-pearl-rebound");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("slime_ball"));
    reboundedKey = DataKey.adapt("rift_pearl_rebounded", DataCodecs.BYTE);
    reboundLevelKey = DataKey.adapt("rift_pearl_rebound_level", DataCodecs.INT);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_pearl"))
        .key("challenge_rift_rebound_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("slime_ball"))
            .key("challenge_rift_rebound_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_rebound_100", "rift.pearl-rebound.rebounds", 100, 400);
    registerMilestone("challenge_rift_rebound_1k", "rift.pearl-rebound.rebounds", 1000, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, REBOUND, DAMAGE, SNEAK);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, C.GREEN, "+ ", Form.pc(getDamageReduction(level), 0), 1);
    statLore(v, C.GREEN, "+ ", Form.pc(getAimBias(level), 0), 2);
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ProjectileLaunchEvent e) {
    ProjectileRef pearl = e.projectile();
    if (!pearl.type().equals(ENDER_PEARL) || !(pearl.shooter() instanceof PlayerRef p)) {
      return;
    }
    if (!RiftPearls.isUnclaimedPearl(pearl, reboundLevelKey.id(), reboundedKey.id())) {
      return;
    }

    int level = getActiveLevel(p);
    if (level > 0 && preferenceEnabled(p, REBOUND) && (!preferenceEnabled(p, SNEAK) || p.isSneaking())) {
      pearl.data().set(reboundLevelKey, level);
    }
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(ProjectileHitEvent e) {
    ProjectileRef pearl = e.projectile();
    if (!pearl.type().equals(ENDER_PEARL) || !(pearl.shooter() instanceof PlayerRef p)) {
      return;
    }

    if (e.hitBlock() == null || e.hitFace() == null) {
      return;
    }

    Integer level = pearl.data().get(reboundLevelKey);
    if (!shouldRebound(
        level,
        pearl.data().hasAny(reboundedKey.id()),
        RiftPearls.isUnclaimedPearl(pearl, reboundLevelKey.id(), reboundedKey.id())
    )) {
      return;
    }

    BlockFace face = e.hitFace();
    Vec3 normal = new Vec3(face.dx(), face.dy(), face.dz()).normalize();
    Vec3 incoming = pearl.velocity();
    if (incoming.lengthSquared() <= 1.0E-6D) {
      incoming = normal.multiply(-1D);
    }

    Vec3 reflected = reflect(incoming, normal);
    if (reflected.lengthSquared() <= 1.0E-6D) {
      reflected = normal;
    }
    reflected = reflected.normalize();

    Loc spawnAt = pearl.location().add(normal.multiply(0.35));
    Map<Id, DataValue> modifiers = ProjectileCooperativeKeys.snapshotCooperative(pearl.data());
    e.setCancelled(true);
    pearl.remove();

    Vec3 finalReflected = reflected;
    Tasks.runEntity(p, () -> finishRebound(p, spawnAt, normal, finalReflected, level, modifiers));
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p)
        || !ENDER_PEARL.equals(e.damageType())) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0 || !preferenceEnabled(p, DAMAGE)) {
      return;
    }

    e.setDamage(calculateReducedDamage(e.damage(), getDamageReduction(level)));
  }

  private boolean launchReboundPearl(PlayerRef p, UUID playerId, Loc spawnAt, Vec3 velocity,
                                     Map<Id, DataValue> modifiers) {
    EntityRef pearl = null;
    try {
      pearl = spawnAt.world().spawn(ENDER_PEARL, spawnAt, (EntityRef ignored) -> {
      });
      if (!(pearl instanceof ProjectileRef projectile)) {
        throw new IllegalStateException("No ender pearl was spawned");
      }
      projectile.setShooter(p);
      ProjectileCooperativeKeys.restore(projectile.data(), modifiers);
      projectile.data().set(reboundedKey, (byte) 1);
      projectile.setVelocity(velocity);
      fx(spawnAt, FxPriority.TRAIL)
          .burst(Particles.SMOKE, 4, 0.15)
          .particle(Particles.REVERSE_PORTAL, 6, 0, 0.2, 0, 0.25, 0.03)
          .chord(Sounds.BLOCK_SLIME_BLOCK_HIT, 0.7f, 1.4f, Sounds.ENTITY_ENDER_EYE_LAUNCH, 0.4f, 1.6f);
      return true;
    } catch (RuntimeException error) {
      if (pearl != null && pearl.isValid()) {
        pearl.remove();
      }
      AdaptLog.error("Rift Pearl Rebound failed to create or configure a pearl for " + playerId
          + " at " + spawnAt + ".");
      AdaptLog.error(error);
      return false;
    }
  }

  private void finishRebound(PlayerRef p, Loc spawnAt, Vec3 normal, Vec3 reflected, int level,
                             Map<Id, DataValue> modifiers) {
    if (!p.isOnline()) {
      return;
    }

    double bias = getAimBias(level);
    Vec3 aim = p.eyeLocation().direction();
    if (aim.lengthSquared() <= 1.0E-6D) {
      aim = reflected;
    }
    aim = aim.normalize();

    Vec3 velocity = reboundDirection(reflected, aim, normal, bias).multiply(getReboundSpeed());
    UUID playerId = p.uuid();
    if (!Tasks.runAt(spawnAt, () -> completeReboundSpawn(p, playerId, spawnAt, velocity, modifiers))) {
      AdaptLog.error("Rift Pearl Rebound could not schedule pearl creation for " + playerId
          + " at " + spawnAt + ".");
    }
  }

  private void completeReboundSpawn(PlayerRef p, UUID playerId, Loc spawnAt, Vec3 velocity,
                                    Map<Id, DataValue> modifiers) {
    if (!isRuntimeRegistered()) {
      return;
    }
    if (!launchReboundPearl(p, playerId, spawnAt, velocity, modifiers)) {
      return;
    }
    if (!Tasks.runEntity(p, () -> rewardRebound(p))) {
      AdaptLog.warn("Rift Pearl Rebound created a pearl but could not schedule rewards for " + playerId + ".");
    }
  }

  private void rewardRebound(PlayerRef p) {
    if (!p.isOnline() || !isRuntimeRegistered() || getActiveLevel(p) <= 0) {
      return;
    }
    addStat(p, "rift.pearl-rebound.rebounds", 1);
    xp(p, getConfig().xpOnRebound, "rift:pearl-rebound:rebound");
  }

  /**
   * Only plain pearls bounce. The launch tag is written before another adaptation can claim the
   * projectile, so the claim is re-checked on impact: a pearl carrying a foreign key belongs to a
   * feature like Ender Taglock and stealing it would drop that feature's payload and leave the
   * replacement pearl teleporting the thrower.
   */
  static boolean shouldRebound(Integer storedLevel, boolean alreadyRebounded, boolean unclaimed) {
    return storedLevel != null && storedLevel > 0 && !alreadyRebounded && unclaimed;
  }

  /**
   * Blends the reflected bounce toward the crosshair, but never back into the struck face: a player still
   * aiming at the wall would otherwise send the rebound straight into it and teleport at the first surface.
   */
  static Vec3 reboundDirection(Vec3 reflected, Vec3 aim, Vec3 normal, double bias) {
    Vec3 direction = reflected.multiply(1D - bias).add(aim.multiply(bias));
    if (direction.lengthSquared() <= 1.0E-6D) {
      return normal.normalize();
    }
    direction = direction.normalize();
    double outward = direction.dot(normal);
    if (outward < MIN_OUTWARD_REBOUND) {
      direction = direction.add(normal.multiply(MIN_OUTWARD_REBOUND - outward));
    }
    return direction.lengthSquared() <= 1.0E-6D ? normal.normalize() : direction.normalize();
  }

  private Vec3 reflect(Vec3 velocity, Vec3 normal) {
    double dot = velocity.dot(normal);
    return velocity.subtract(normal.multiply(2D * dot));
  }

  private double getDamageReduction(int level) {
    double value = getConfig().damageReductionBase + (Math.max(0, level - 1) * getConfig().damageReductionPerLevel);
    return Math.max(0D, Math.min(HARD_MAX_DAMAGE_REDUCTION, value));
  }

  private double getAimBias(int level) {
    double value = getConfig().aimBiasBase + (Math.max(0, level - 1) * getConfig().aimBiasPerLevel);
    return Math.max(0D, Math.min(HARD_MAX_AIM_BIAS, value));
  }

  private double getReboundSpeed() {
    return Math.max(0.4D, getConfig().reboundSpeed);
  }

  static double calculateReducedDamage(double damage, double reduction) {
    if (!Double.isFinite(damage) || damage <= 0D) {
      return 0D;
    }
    double safeReduction = Double.isFinite(reduction) ? Math.max(0D, Math.min(HARD_MAX_DAMAGE_REDUCTION, reduction)) : 0D;
    return damage * (1D - safeReduction);
  }

  @ConfigDescription("Plain thrown pearls bounce once off the first surface toward your crosshair, and their ender pearl landing damage is reduced.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of ender pearl teleport damage removed at level 1.", impact = "Higher values soften pearl landings more; capped at 90 percent.")
    double damageReductionBase = 0.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra pearl damage reduction fraction per adaptation level beyond the first.", impact = "Higher values make leveling reduce pearl damage faster.")
    double damageReductionPerLevel = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction the rebounded pearl steers toward your look direction at level 1.", impact = "Higher values aim the bounce more at your crosshair and less like a pure reflection; capped at 90 percent.")
    double aimBiasBase = 0.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra aim steering fraction per adaptation level beyond the first.", impact = "Higher values make leveling aim the bounce at you more precisely.")
    double aimBiasPerLevel = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Launch speed of the rebounded pearl.", impact = "Higher values throw the bounced pearl further and faster.")
    double reboundSpeed = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Skill XP granted each time a pearl rebounds.", impact = "Higher values grant more skill XP per rebound.")
    double xpOnRebound = 6;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 4;
      initialCost = 1;
    }
  }
}
