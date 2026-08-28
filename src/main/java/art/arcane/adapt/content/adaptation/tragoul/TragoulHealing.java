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

package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.TragoulMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class TragoulHealing extends SimpleAdaptation<TragoulHealing.Config> {
  private static final Color HEAL_CRIMSON = Color.fromRGB(150, 0, 10);
  private static final Color HEAL_PINK = Color.fromRGB(210, 40, 40);

  private final AtomicBoolean acceptingWork = new AtomicBoolean(true);

  public TragoulHealing() {
    super("tragoul-healing");
    registerConfiguration(TragoulHealing.Config.class);
    setIcon(Material.GLISTERING_MELON_SLICE);
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.REDSTONE)
        .key("challenge_tragoul_healing_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.RED_DYE)
            .key("challenge_tragoul_healing_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_tragoul_healing_500", "tragoul.healing.health-stolen", 500, 400);
    registerMilestone("challenge_tragoul_healing_10k", "tragoul.healing.health-stolen", 10000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, C.RED, "- ", Form.f(getDrainDamage(level), 1), 1);
    v.addLore(C.GREEN + AdaptLanguage.text(TragoulMessages.HEALING_LORE2));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageEvent event) {
    if (!acceptingWork.get() || TragoulReactiveDamage.isActive() || event.getFinalDamage() <= 0D
        || !(event.getEntity() instanceof Player defender)) {
      return;
    }

    LivingEntity attacker = resolveAttacker(event);
    if (attacker == null || defender.getUniqueId().equals(attacker.getUniqueId())) {
      return;
    }

    int level = getActiveLevel(defender);
    if (level <= 0 || getDrainDamage(level) <= 0D) {
      return;
    }
    J.runEntity(attacker, () -> captureAttackerOwned(defender, attacker));
  }

  @Override
  public void unregister() {
    acceptingWork.set(false);
    super.unregister();
  }

  private void captureAttackerOwned(Player defender, LivingEntity attacker) {
    if (!acceptingWork.get() || !attacker.isValid() || attacker.isDead()) {
      return;
    }

    Location location = attacker.getLocation().clone();
    AttackerSnapshot snapshot = new AttackerSnapshot(
        attacker,
        attacker.getUniqueId(),
        location,
        attacker instanceof Player,
        isProtectedAttacker(defender, attacker));
    J.runEntity(defender, () -> authorizeDrainOwnerOwned(defender, snapshot));
  }

  private void authorizeDrainOwnerOwned(Player defender, AttackerSnapshot attacker) {
    if (!acceptingWork.get() || !defender.isOnline() || attacker.protectedFriendly()
        || defender.getWorld() != attacker.location().getWorld()) {
      return;
    }

    int level = getActiveLevel(defender);
    if (level <= 0 || !canAffect(defender, attacker)) {
      return;
    }

    double damage = getDrainDamage(level);
    if (damage > 0D) {
      J.runEntity(attacker.entity(), () -> applyDrainAttackerOwned(defender, attacker, damage));
    }
  }

  private boolean canAffect(Player defender, AttackerSnapshot attacker) {
    return attacker.player() ? canPVP(defender, attacker.location()) : canPVE(defender, attacker.location());
  }

  private boolean isProtectedAttacker(Player defender, LivingEntity attacker) {
    if (TragoulSkeletalServant.isServant(attacker)) {
      UUID ownerId = TragoulSkeletalServant.getServantOwnerId(attacker);
      return ownerId == null || defender.getUniqueId().equals(ownerId);
    }
    return isProtectedFriendly(defender, attacker);
  }

  private void applyDrainAttackerOwned(Player defender, AttackerSnapshot attacker, double damage) {
    LivingEntity target = attacker.entity();
    if (!acceptingWork.get() || !target.isValid() || target.isDead()) {
      return;
    }

    double healthBefore = target.getHealth();
    Location drainOrigin = target.getLocation().clone();
    TragoulReactiveDamage.apply(() -> target.damage(damage, defender));

    double drained = Math.max(0D, healthBefore - target.getHealth());
    if (drained > 0D) {
      J.runEntity(defender, () -> completeDrainOwnerOwned(defender, drainOrigin, drained), 1);
    }
  }

  private void completeDrainOwnerOwned(Player defender, Location drainOrigin, double drained) {
    if (!acceptingWork.get() || !defender.isOnline() || defender.isDead()) {
      return;
    }

    IAttribute attribute = Version.get().getAttribute(defender, Attributes.MAX_HEALTH);
    double maximumHealth = attribute == null ? 20D : attribute.getValue();
    double currentHealth = defender.getHealth();
    double healed = restoredHealth(currentHealth, maximumHealth, drained) - currentHealth;
    if (healed <= 0D) {
      return;
    }

    defender.setHealth(currentHealth + healed);
    addStat(defender, "tragoul.healing.health-stolen", healed);
    playDrainTether(drainOrigin, defender.getLocation());
    fx(defender.getLocation().add(0, 1.0D, 0), FxPriority.TRANSITION)
        .dustBurst(HEAL_PINK, 3, 0.18D, 0.8F)
        .sound(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.25F, 1.6F);
  }

  private double getDrainDamage(int level) {
    return drainDamage(getConfig().drainDamageStart, getConfig().drainDamageEnd, level, getMaxLevel());
  }

  private void playDrainTether(Location attackerLocation, Location playerLocation) {
    Location from = attackerLocation.clone().add(0, 1.0D, 0);
    double dx = playerLocation.getX() - from.getX();
    double dy = (playerLocation.getY() + 1.0D) - from.getY();
    double dz = playerLocation.getZ() - from.getZ();
    Particle.DustTransition transition = new Particle.DustTransition(HEAL_CRIMSON, HEAL_PINK, 1.0F);
    timeline(from)
        .duration(5)
        .priority(FxPriority.TRAIL)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          fx.particle(Particle.DUST_COLOR_TRANSITION, 2, dx * progress, dy * progress, dz * progress, 0.05D, 0D, transition);
          if (tick == 0) {
            fx.sound(Sound.PARTICLE_SOUL_ESCAPE, 0.35F, 1.5F);
          }
        })
        .start();
  }

  static LivingEntity resolveAttacker(EntityDamageEvent event) {
    if (event == null) {
      return null;
    }
    LivingEntity source = resolveAttacker(event.getDamageSource());
    if (source != null) {
      return source;
    }
    return event instanceof EntityDamageByEntityEvent damage ? resolveAttacker(damage.getDamager()) : null;
  }

  static LivingEntity resolveAttacker(DamageSource source) {
    if (source == null) {
      return null;
    }
    if (source.getCausingEntity() instanceof LivingEntity living) {
      return living;
    }
    return source.getDirectEntity() instanceof LivingEntity living ? living : null;
  }

  static LivingEntity resolveAttacker(Entity source) {
    if (source instanceof LivingEntity living) {
      return living;
    }
    return source instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter
        ? shooter
        : null;
  }

  static double drainDamage(double start, double end, int level, int maxLevel) {
    if (!Double.isFinite(start) || !Double.isFinite(end)) {
      return 0D;
    }
    if (maxLevel <= 1) {
      return Math.max(0D, end);
    }
    double progress = Math.max(0D, Math.min(1D, (level - 1D) / (maxLevel - 1D)));
    return Math.max(0D, start + ((end - start) * progress));
  }

  static double restoredHealth(double currentHealth, double maximumHealth, double drained) {
    if (!Double.isFinite(currentHealth) || !Double.isFinite(maximumHealth) || !Double.isFinite(drained)) {
      return currentHealth;
    }
    return Math.max(0D, Math.min(maximumHealth, currentHealth + Math.max(0D, drained)));
  }

  @ConfigDescription("Every eligible attacker that damages you loses a small fixed amount of life, which is restored to you.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Life drained from each attacker at adaptation level one.", impact = "Higher values make the passive retaliation stronger at low levels.")
    double drainDamageStart = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Life drained from each attacker at maximum adaptation level.", impact = "Higher values make the passive retaliation stronger at high levels.")
    double drainDamageEnd = 2.0;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }

  private record AttackerSnapshot(LivingEntity entity, UUID entityId, Location location,
                                  boolean player, boolean protectedFriendly) {
  }
}
