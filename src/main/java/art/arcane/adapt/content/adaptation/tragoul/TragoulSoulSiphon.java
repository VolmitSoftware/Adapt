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
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class TragoulSoulSiphon extends SimpleAdaptation<TragoulSoulSiphon.Config> {
  private final Map<UUID, HealBudget> budgets = playerState();
  private final AtomicBoolean acceptingWork = new AtomicBoolean(true);

  public TragoulSoulSiphon() {
    super("tragoul-soul-siphon");
    registerConfiguration(Config.class);
    setIcon(Material.SOUL_LANTERN);
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SOUL_LANTERN)
        .key("challenge_tragoul_siphon_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.SOUL_CAMPFIRE)
            .key("challenge_tragoul_siphon_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_tragoul_siphon_500", "tragoul.soul-siphon.health-siphoned", 500, 400);
    registerMilestone("challenge_tragoul_siphon_10k", "tragoul.soul-siphon.health-siphoned", 10000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getHealPercent(level), 0), 1);
    statLore(v, C.YELLOW, "* ", Form.f(getHealCapPerSecond(level), 1), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageEvent event) {
    if (!acceptingWork.get() || event.getFinalDamage() <= 0D
        || !(event.getEntity() instanceof LivingEntity victim)) {
      return;
    }

    Player owner = resolveDamageOwner(event);
    if (owner == null || owner.getUniqueId().equals(victim.getUniqueId())) {
      return;
    }

    Location victimLocation = victim.getLocation().clone();
    double acceptedDamage = acceptedDamage(event.getFinalDamage(), victim.getHealth(), victim.getAbsorptionAmount());
    if (acceptedDamage <= 0D) {
      return;
    }
    DamageSnapshot snapshot = new DamageSnapshot(
        victimLocation,
        victim instanceof Player,
        isProtectedFriendly(owner, victim),
        acceptedDamage);
    J.runEntity(owner, () -> applySiphonOwnerOwned(owner, snapshot));
  }

  @Override
  public void unregister() {
    acceptingWork.set(false);
    budgets.clear();
    super.unregister();
  }

  private void applySiphonOwnerOwned(Player owner, DamageSnapshot damage) {
    if (!acceptingWork.get() || !owner.isOnline() || damage.protectedFriendly()
        || owner.getWorld() != damage.location().getWorld()) {
      return;
    }

    int level = getActiveLevel(owner);
    if (level <= 0 || !canAffect(owner, damage)) {
      return;
    }

    long now = System.currentTimeMillis();
    HealBudget budget = budgets.computeIfAbsent(owner.getUniqueId(), ignored -> new HealBudget());
    if (now - budget.windowStart >= 1000L) {
      budget.windowStart = now;
      budget.healed = 0D;
      budget.capNotified = false;
    }

    double remaining = getHealCapPerSecond(level) - budget.healed;
    if (remaining <= 0D) {
      notifyCapped(owner, budget);
      return;
    }

    IAttribute attribute = Version.get().getAttribute(owner, Attributes.MAX_HEALTH);
    double maximumHealth = attribute == null ? 20D : attribute.getValue();
    double currentHealth = owner.getHealth();
    double healed = healAmount(damage.finalDamage(), getHealPercent(level), remaining,
        maximumHealth - currentHealth);
    if (healed <= 0D) {
      return;
    }

    budget.healed += healed;
    owner.setHealth(currentHealth + healed);
    playSiphon(owner, damage.location());
    addStat(owner, "tragoul.soul-siphon.health-siphoned", healed);
    xp(owner, getConfig().xpPerHeal);
  }

  private boolean canAffect(Player owner, DamageSnapshot damage) {
    return damage.player() ? canPVP(owner, damage.location()) : canPVE(owner, damage.location());
  }

  private void notifyCapped(Player owner, HealBudget budget) {
    if (budget.capNotified) {
      return;
    }
    budget.capNotified = true;
    fx(owner.getLocation().add(0, 1.2D, 0), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 2, 0.2D)
        .sound(Sound.BLOCK_FIRE_EXTINGUISH, 0.15F, 1.4F);
  }

  private void playSiphon(Player owner, Location victimLocation) {
    Location ownerChest = owner.getLocation().add(0, 1.2D, 0);
    fx(victimLocation.clone().add(0, 1.0D, 0), FxPriority.COMBAT)
        .particle(Particle.SOUL, 3, 0, 0, 0, 0.15D, 0.02D)
        .line(Particle.SOUL, ownerChest.getX(), ownerChest.getY(), ownerChest.getZ(), 4)
        .sound(Sound.PARTICLE_SOUL_ESCAPE, 0.3F, 1.4F);
  }

  private double getHealPercent(int level) {
    return Math.max(0D, getConfig().healPercentBase + (getLevelPercent(level) * getConfig().healPercentFactor));
  }

  private double getHealCapPerSecond(int level) {
    return Math.max(0.5D, getConfig().healCapPerSecondBase + (getLevelPercent(level) * getConfig().healCapPerSecondFactor));
  }

  static Player resolveDamageOwner(EntityDamageEvent event) {
    if (event == null) {
      return null;
    }
    Player source = resolveDamageOwner(event.getDamageSource());
    if (source != null) {
      return source;
    }
    return event instanceof EntityDamageByEntityEvent damage ? resolveDamageOwner(damage.getDamager()) : null;
  }

  static Player resolveDamageOwner(DamageSource source) {
    if (source == null) {
      return null;
    }
    if (source.getCausingEntity() instanceof Player player) {
      return player;
    }
    return source.getDirectEntity() instanceof Player player ? player : null;
  }

  static Player resolveDamageOwner(Entity source) {
    if (source instanceof Player player) {
      return player;
    }
    if (source instanceof Projectile projectile) {
      ProjectileSource shooter = projectile.getShooter();
      return shooter instanceof Player player ? player : null;
    }
    if (source instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
      return player;
    }
    if (source instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player player) {
      return player;
    }
    if (source instanceof EvokerFangs fangs && fangs.getOwner() instanceof Player player) {
      return player;
    }
    return null;
  }

  static double healAmount(double finalDamage, double healPercent, double remainingCap, double missingHealth) {
    if (!Double.isFinite(finalDamage) || !Double.isFinite(healPercent)
        || !Double.isFinite(remainingCap) || !Double.isFinite(missingHealth)) {
      return 0D;
    }
    return Math.max(0D, Math.min(Math.min(finalDamage * Math.max(0D, healPercent), remainingCap), missingHealth));
  }

  static double acceptedDamage(double finalDamage, double health, double absorption) {
    if (!Double.isFinite(finalDamage) || !Double.isFinite(health) || !Double.isFinite(absorption)) {
      return 0D;
    }
    return Math.max(0D, Math.min(finalDamage, Math.max(0D, health) + Math.max(0D, absorption)));
  }

  private static class HealBudget {
    private long windowStart;
    private double healed;
    private boolean capNotified;
  }

  @ConfigDescription("Every valid damage source attributed to you heals you for part of its final damage, capped per second.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of final attributed damage returned as healing before level scaling.", impact = "Higher values increase baseline lifesteal across melee, projectiles, abilities, and other attributed sources.")
    double healPercentBase = 0.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional attributed-damage lifesteal fraction granted at max level.", impact = "Higher values increase level-scaled lifesteal growth for every supported damage source.")
    double healPercentFactor = 0.32;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum health restored per second before level scaling.", impact = "Lower values harden the anti-abuse healing cap across rapid and multi-target damage.")
    double healCapPerSecondBase = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional per-second healing cap granted at max level.", impact = "Higher values let high levels sustain more healing per second.")
    double healCapPerSecondFactor = 6.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per successful siphon heal.", impact = "Higher values accelerate skill progression from this adaptation.")
    double xpPerHeal = 3;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }

  private record DamageSnapshot(Location location, boolean player, boolean protectedFriendly, double finalDamage) {
  }
}
