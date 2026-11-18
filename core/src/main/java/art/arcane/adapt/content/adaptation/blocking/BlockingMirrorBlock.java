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

package art.arcane.adapt.content.adaptation.blocking;

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
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class BlockingMirrorBlock extends SimpleAdaptation<BlockingMirrorBlock.Config> {
  private static final Id SHIELD = Id.minecraft("shield");
  private static final DataKey<Byte> REFLECTED_KEY = DataKey.adapt("mirror-reflected", DataCodecs.BYTE);
  private static final DataKey<Double> DAMAGE_FACTOR_KEY = DataKey.adapt("mirror-damage-factor", DataCodecs.DOUBLE);
  private final Set<UUID> pendingDefenders = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean acceptingReflections = new AtomicBoolean(true);

  public BlockingMirrorBlock() {
    super("blocking-mirror-block");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("light_weighted_pressure_plate"));
    setInterval(1200);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("shield"))
        .key("challenge_blocking_mirror_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_blocking_mirror_100", "blocking.mirror-block.projectiles-reflected", 100, 500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("shield"))
        .key("challenge_blocking_mirror_3in5")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
  }

  @Override
  protected void onRuntimeActivated() {
    acceptingReflections.set(true);
  }

  @Override
  public void unregister() {
    acceptingReflections.set(false);
    pendingDefenders.clear();
    super.unregister();
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, BlockingPreferences.PLAYERS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getReflectChance(level), 0), 1);
    statLore(v, Form.pc(getReflectedDamageFactor(level), 0), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getReflectCooldownMillis(level), 1), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof ProjectileRef projectile)) {
      return;
    }

    applyReflectedDamageModifier(e, projectile);

    if (!(e.entity() instanceof PlayerRef defender) || !acceptingReflections.get()
        || !isMirrorReady(defender) || projectile.data().hasAny(REFLECTED_KEY.id())) {
      return;
    }

    if (projectile.shooter() instanceof PlayerRef && !preferenceEnabled(defender, BlockingPreferences.PLAYERS)) {
      return;
    }
    int level = getActiveLevel(defender);
    long now = System.currentTimeMillis();
    long next = getStorageLong(defender, "mirrorBlockNext", 0L);
    if (next > now) {
      onCooldownTell(defender);
      return;
    }

    if (ThreadLocalRandom.current().nextDouble() > getReflectChance(level)) {
      return;
    }

    UUID defenderId = defender.uuid();
    if (!pendingDefenders.add(defenderId)) {
      return;
    }

    if (!reflectProjectile(defender, projectile, level)) {
      pendingDefenders.remove(defenderId);
      return;
    }
    e.setCancelled(true);
  }

  private void mirrorFlash(PlayerRef defender) {
    Loc chest = defender.location().add(0, 1.0D, 0);
    fx(chest, FxPriority.COMBAT)
        .ring(Particles.END_ROD, 0.5D, 8, 0)
        .burst(Particles.CRIT_MAGIC, 10, 0.35D)
        .chord(Sounds.ITEM_SHIELD_BLOCK, 1.0F, 1.35F, Sounds.BLOCK_AMETHYST_BLOCK_HIT, 0.8F, 0.8F, Sounds.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5F, 1.5F);
  }

  private void reflectTrail(ProjectileRef projectile) {
    timeline(projectile)
        .duration(40)
        .priority(FxPriority.TRAIL)
        .cullRadius(24.0D)
        .frame((fxE, tick, progress) -> fxE.particle(Particles.WAX_ON, 1, 0, 0, 0, 0, 0))
        .start();
  }

  private void onCooldownTell(PlayerRef defender) {
    Vec3 look = defender.eyeLocation().direction();
    Loc front = defender.eyeLocation().add(look.x() * 0.4D, look.y() * 0.4D, look.z() * 0.4D);
    fx(front, FxPriority.AMBIENT)
        .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05D, 0.01D);
  }

  private void applyReflectedDamageModifier(EntityDamageByEntityEvent e, ProjectileRef projectile) {
    if (!(projectile.shooter() instanceof PlayerRef shooter) || !projectile.data().hasAny(DAMAGE_FACTOR_KEY.id())) {
      return;
    }

    if (!canDamageTarget(shooter, e.entity())) {
      return;
    }

    double factor = projectile.data().getOrDefault(DAMAGE_FACTOR_KEY, 1D);
    e.setDamage(e.damage() * factor);
  }

  private boolean reflectProjectile(PlayerRef defender, ProjectileRef projectile, int level) {
    Vec3 incoming = projectile.velocity();
    Vec3 reflected = incoming.multiply(-Math.max(0.01, getReflectVelocityFactor(level)));
    if (reflected.lengthSquared() < getConfig().minReflectedVelocitySquared) {
      reflected = defender.eyeLocation().direction().normalize().multiply(getConfig().fallbackReflectedSpeed);
    }

    Loc destination = defender.eyeLocation()
        .add(defender.eyeLocation().direction().multiply(0.55D));
    MirrorReflection reflection = new MirrorReflection(
        defender.uuid(),
        reflected,
        getReflectedDamageFactor(level),
        getReflectCooldownMillis(level)
    );
    CompletableFuture<Boolean> teleport;
    try {
      teleport = projectile.teleport(destination, TeleportCause.PLUGIN);
    } catch (RuntimeException error) {
      AdaptLog.error("Mirror Block could not start a projectile teleport for "
          + defender.uuid() + ".");
      AdaptLog.error(error);
      return false;
    }
    if (teleport == null) {
      return false;
    }

    teleport.whenComplete((success, failure) ->
        finishProjectileTeleport(defender, projectile, reflection, success, failure));
    return true;
  }

  private void finishProjectileTeleport(
      PlayerRef defender,
      ProjectileRef projectile,
      MirrorReflection reflection,
      Boolean success,
      Throwable failure
  ) {
    if (failure != null) {
      AdaptLog.error("Mirror Block projectile teleport failed for "
          + reflection.defenderId() + ".");
      AdaptLog.error(failure);
    }

    if (!teleportCompleted(success, failure)) {
      pendingDefenders.remove(reflection.defenderId());
      return;
    }

    Runnable completion = () -> {
      if (!acceptingReflections.get() || !projectile.isValid()) {
        pendingDefenders.remove(reflection.defenderId());
        return;
      }

      projectile.setShooter(defender);
      projectile.setVelocity(reflection.velocity());
      projectile.data().set(REFLECTED_KEY, (byte) 1);
      projectile.data().set(DAMAGE_FACTOR_KEY, reflection.damageFactor());
      reflectTrail(projectile);
      boolean scheduled = Tasks.runEntity(defender, () ->
          commitReflection(defender, reflection));
      if (!scheduled) {
        pendingDefenders.remove(reflection.defenderId());
      }
    };
    boolean scheduled = Tasks.runEntity(projectile, completion);
    if (!scheduled) {
      if (Tasks.isOwnedByCurrentRegion(projectile)) {
        completion.run();
      } else {
        pendingDefenders.remove(reflection.defenderId());
      }
    }
  }

  private void commitReflection(PlayerRef defender, MirrorReflection reflection) {
    if (!pendingDefenders.remove(reflection.defenderId())
        || !acceptingReflections.get() || !defender.isOnline()) {
      return;
    }

    long now = System.currentTimeMillis();
    setStorage(defender, "mirrorBlockNext", now + reflection.cooldownMillis());
    mirrorFlash(defender);
    xp(defender, getConfig().xpOnReflect);
    addStat(defender, "blocking.mirror-block.projectiles-reflected", 1);

    long windowStart = getStorageLong(defender, "mirrorWindowStart", 0L);
    int windowCount = getStorageInt(defender, "mirrorWindowCount", 0);
    if (now - windowStart > 5000L) {
      windowStart = now;
      windowCount = 1;
    } else {
      windowCount++;
    }
    setStorage(defender, "mirrorWindowStart", windowStart);
    setStorage(defender, "mirrorWindowCount", windowCount);
    if (windowCount >= 3 && grantOnce(defender, "challenge_blocking_mirror_3in5")) {
      fx(defender.location().add(0, 1.0D, 0), FxPriority.TRANSITION)
          .particle(Particles.REVERSE_PORTAL, 12, 0, 0.3D, 0, 0.4D, 0.02D)
          .sound(Sounds.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.0F);
    }
  }

  static boolean teleportCompleted(Boolean success, Throwable failure) {
    return failure == null && Boolean.TRUE.equals(success);
  }

  private boolean isMirrorReady(PlayerRef p) {
    return hasActiveAdaptation(p) && p.isBlocking() && hasShield(p);
  }

  private boolean hasShield(PlayerRef p) {
    ItemRef main = p.inventory().mainHand();
    ItemRef off = p.inventory().offHand();
    return (isItem(main) && SHIELD.equals(main.type())) || (isItem(off) && SHIELD.equals(off.type()));
  }

  private double getReflectChance(int level) {
    return Math.min(getConfig().maxReflectChance, getConfig().reflectChanceBase + (getLevelPercent(level) * getConfig().reflectChanceFactor));
  }

  private double getReflectedDamageFactor(int level) {
    return Math.min(getConfig().maxReflectedDamageFactor,
        getConfig().reflectedDamageFactorBase + (getLevelPercent(level) * getConfig().reflectedDamageFactorIncrease));
  }

  private double getReflectVelocityFactor(int level) {
    return Math.min(getConfig().maxReflectVelocityFactor, getConfig().reflectVelocityFactorBase + (getLevelPercent(level) * getConfig().reflectVelocityFactor));
  }

  private long getReflectCooldownMillis(int level) {
    return Math.max(100L, Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }

  private record MirrorReflection(
      UUID defenderId,
      Vec3 velocity,
      double damageFactor,
      long cooldownMillis
  ) {
  }


  @ConfigDescription("Blocking with a shield can reflect incoming projectiles at reduced force and damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reflect Chance Base for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectChanceBase = 0.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reflect Chance Factor for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectChanceFactor = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Reflect Chance for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxReflectChance = 0.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reflected Damage Factor Base for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectedDamageFactorBase = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reflected Damage Factor Increase for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectedDamageFactorIncrease = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Reflected Damage Factor for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxReflectedDamageFactor = 0.95;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reflect Velocity Factor Base for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectVelocityFactorBase = 0.42;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reflect Velocity Factor for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectVelocityFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Reflect Velocity Factor for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxReflectVelocityFactor = 1.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisBase = 2000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisFactor = 1200;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Reflected Velocity Squared for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minReflectedVelocitySquared = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fallback Reflected Speed for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fallbackReflectedSpeed = 0.95;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp On Reflect for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnReflect = 8;

    public Config() {
      costFactor = 0.7;
      initialCost = 4;
    }
  }
}
