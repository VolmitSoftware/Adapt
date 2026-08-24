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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.UUID;

public class RiftPearlRebound extends SimpleAdaptation<RiftPearlRebound.Config> {
  private static final double HARD_MAX_DAMAGE_REDUCTION = 0.9D;
  private static final double HARD_MAX_AIM_BIAS = 0.9D;

  private final NamespacedKey reboundedKey;
  private final NamespacedKey reboundLevelKey;

  public RiftPearlRebound() {
    super("rift-pearl-rebound");
    registerConfiguration(Config.class);
    setIcon(Material.SLIME_BALL);
    reboundedKey = new NamespacedKey(Adapt.instance, "rift_pearl_rebounded");
    reboundLevelKey = new NamespacedKey(Adapt.instance, "rift_pearl_rebound_level");
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_PEARL)
        .key("challenge_rift_rebound_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SLIME_BALL)
            .key("challenge_rift_rebound_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_rift_rebound_100", "rift.pearl-rebound.rebounds", 100, 400);
    registerMilestone("challenge_rift_rebound_1k", "rift.pearl-rebound.rebounds", 1000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, C.GREEN, "+ ", Form.pc(getDamageReduction(level), 0), 1);
    statLore(v, C.GREEN, "+ ", Form.pc(getAimBias(level), 0), 2);
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ProjectileLaunchEvent e) {
    if (!(e.getEntity() instanceof EnderPearl pearl) || !(pearl.getShooter() instanceof Player p)) {
      return;
    }
    if (!RiftPearls.isUnclaimedPearl(pearl, reboundLevelKey, reboundedKey)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level > 0) {
      pearl.getPersistentDataContainer().set(reboundLevelKey, PersistentDataType.INTEGER, level);
    }
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(ProjectileHitEvent e) {
    if (!(e.getEntity() instanceof EnderPearl pearl) || !(pearl.getShooter() instanceof Player p)) {
      return;
    }

    if (e.getHitBlock() == null || e.getHitBlockFace() == null) {
      return;
    }

    Integer level = pearl.getPersistentDataContainer().get(reboundLevelKey, PersistentDataType.INTEGER);
    if (!shouldRebound(
        level,
        pearl.getPersistentDataContainer().has(reboundedKey),
        RiftPearls.isUnclaimedPearl(pearl, reboundLevelKey, reboundedKey)
    )) {
      return;
    }

    Vector normal = e.getHitBlockFace().getDirection().clone().normalize();
    Vector incoming = pearl.getVelocity().clone();
    if (incoming.lengthSquared() <= 1.0E-6D) {
      incoming = normal.clone().multiply(-1D);
    }

    Vector reflected = reflect(incoming, normal);
    if (reflected.lengthSquared() <= 1.0E-6D) {
      reflected = normal.clone();
    }
    reflected.normalize();

    Location spawnAt = pearl.getLocation().clone().add(normal.clone().multiply(0.35));
    e.setCancelled(true);
    pearl.remove();

    Vector finalReflected = reflected;
    J.runEntity(p, () -> finishRebound(p, spawnAt, normal, finalReflected, level));
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)
        || !DamageType.ENDER_PEARL.equals(e.getDamageSource().getDamageType())) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    e.setDamage(calculateReducedDamage(e.getDamage(), getDamageReduction(level)));
  }

  private boolean launchReboundPearl(Player p, UUID playerId, Location spawnAt, Vector velocity) {
    World world = spawnAt.getWorld();
    if (world == null) {
      Adapt.error("Rift Pearl Rebound could not spawn a pearl for " + playerId
          + " because the destination world was unavailable.");
      return false;
    }
    EnderPearl pearl = null;
    try {
      pearl = world.spawn(spawnAt, EnderPearl.class);
      pearl.setShooter(p);
      pearl.getPersistentDataContainer().set(reboundedKey, PersistentDataType.BYTE, (byte) 1);
      pearl.setVelocity(velocity);
      fx(spawnAt, FxPriority.TRAIL)
          .burst(Particles.SMOKE, 4, 0.15)
          .particle(Particle.REVERSE_PORTAL, 6, 0, 0.2, 0, 0.25, 0.03)
          .chord(Sound.BLOCK_SLIME_BLOCK_HIT, 0.7f, 1.4f, Sound.ENTITY_ENDER_EYE_LAUNCH, 0.4f, 1.6f);
      return true;
    } catch (RuntimeException error) {
      if (pearl != null && pearl.isValid()) {
        pearl.remove();
      }
      Adapt.error("Rift Pearl Rebound failed to create or configure a pearl for " + playerId
          + " at " + spawnAt + ".");
      Adapt.error(error);
      return false;
    }
  }

  private void finishRebound(Player p, Location spawnAt, Vector normal, Vector reflected, int level) {
    if (!p.isOnline()) {
      return;
    }

    double bias = getAimBias(level);
    Vector aim = p.getEyeLocation().getDirection().clone();
    if (aim.lengthSquared() <= 1.0E-6D) {
      aim = reflected.clone();
    }
    aim.normalize();

    Vector direction = reflected.multiply(1D - bias).add(aim.multiply(bias));
    if (direction.lengthSquared() <= 1.0E-6D) {
      direction = normal.clone();
    }
    Vector velocity = direction.normalize().multiply(getReboundSpeed());
    UUID playerId = p.getUniqueId();
    if (!J.runAt(spawnAt, () -> completeReboundSpawn(p, playerId, spawnAt, velocity))) {
      Adapt.error("Rift Pearl Rebound could not schedule pearl creation for " + playerId
          + " at " + spawnAt + ".");
    }
  }

  private void completeReboundSpawn(Player p, UUID playerId, Location spawnAt, Vector velocity) {
    if (!isRuntimeRegistered()) {
      return;
    }
    if (!launchReboundPearl(p, playerId, spawnAt, velocity)) {
      return;
    }
    if (!J.runEntity(p, () -> rewardRebound(p))) {
      Adapt.warn("Rift Pearl Rebound created a pearl but could not schedule rewards for " + playerId + ".");
    }
  }

  private void rewardRebound(Player p) {
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

  private Vector reflect(Vector velocity, Vector normal) {
    double dot = velocity.dot(normal);
    return velocity.clone().subtract(normal.clone().multiply(2D * dot));
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
      baseCost = 5;
      costFactor = 0.35;
      maxLevel = 4;
      initialCost = 3;
    }
  }
}
