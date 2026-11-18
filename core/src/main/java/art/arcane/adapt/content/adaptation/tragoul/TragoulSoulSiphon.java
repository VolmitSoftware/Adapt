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
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class TragoulSoulSiphon extends SimpleAdaptation<TragoulSoulSiphon.Config> {
  private final Map<UUID, HealBudget> budgets = playerState();
  private final AtomicBoolean acceptingWork = new AtomicBoolean(true);

  public TragoulSoulSiphon() {
    super("tragoul-soul-siphon");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("soul_lantern"));
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("soul_lantern"))
        .key("challenge_tragoul_siphon_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("soul_campfire"))
            .key("challenge_tragoul_siphon_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_tragoul_siphon_500", "tragoul.soul-siphon.health-siphoned", 500, 400);
    registerMilestone("challenge_tragoul_siphon_10k", "tragoul.soul-siphon.health-siphoned", 10000, 1500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getHealPercent(level), 0), 1);
    statLore(v, C.YELLOW, "* ", Form.f(getHealCapPerSecond(level), 1), 2);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageEvent event) {
    if (!acceptingWork.get() || event.finalDamage() <= 0D
        || !(event.entity() instanceof LivingRef victim)) {
      return;
    }

    PlayerRef owner = resolveDamageOwner(event);
    if (owner == null || owner.uuid().equals(victim.uuid())) {
      return;
    }

    Loc victimLocation = victim.location();
    double acceptedDamage = acceptedDamage(event.finalDamage(), victim.health(), victim.absorption());
    if (acceptedDamage <= 0D) {
      return;
    }
    DamageSnapshot snapshot = new DamageSnapshot(
        victimLocation,
        victim instanceof PlayerRef,
        isProtectedFriendly(owner, victim),
        acceptedDamage);
    Tasks.runEntity(owner, () -> applySiphonOwnerOwned(owner, snapshot));
  }

  @Override
  public void unregister() {
    acceptingWork.set(false);
    budgets.clear();
    super.unregister();
  }

  private void applySiphonOwnerOwned(PlayerRef owner, DamageSnapshot damage) {
    if (!acceptingWork.get() || !owner.isOnline() || damage.protectedFriendly()
        || !owner.world().equals(damage.location().world())) {
      return;
    }

    int level = getActiveLevel(owner);
    if (level <= 0 || !canAffect(owner, damage)) {
      return;
    }

    long now = System.currentTimeMillis();
    HealBudget budget = budgets.computeIfAbsent(owner.uuid(), ignored -> new HealBudget());
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

    double attributeMaximum = owner.attributeValue(Attributes.MAX_HEALTH);
    double maximumHealth = Double.isNaN(attributeMaximum) ? 20D : attributeMaximum;
    double currentHealth = owner.health();
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

  private boolean canAffect(PlayerRef owner, DamageSnapshot damage) {
    return damage.player() ? canPVP(owner, damage.location()) : canPVE(owner, damage.location());
  }

  private void notifyCapped(PlayerRef owner, HealBudget budget) {
    if (budget.capNotified) {
      return;
    }
    budget.capNotified = true;
    fx(owner.location().add(0, 1.2D, 0), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 2, 0.2D)
        .sound(Sounds.BLOCK_FIRE_EXTINGUISH, 0.15F, 1.4F);
  }

  private void playSiphon(PlayerRef owner, Loc victimLocation) {
    Loc ownerChest = owner.location().add(0, 1.2D, 0);
    fx(victimLocation.add(0, 1.0D, 0), FxPriority.COMBAT)
        .particle(Particles.SOUL, 3, 0, 0, 0, 0.15D, 0.02D)
        .line(Particles.SOUL, ownerChest.x(), ownerChest.y(), ownerChest.z(), 4)
        .sound(Sounds.PARTICLE_SOUL_ESCAPE, 0.3F, 1.4F);
  }

  private double getHealPercent(int level) {
    return Math.max(0D, getConfig().healPercentBase + (getLevelPercent(level) * getConfig().healPercentFactor));
  }

  private double getHealCapPerSecond(int level) {
    return Math.max(0.5D, getConfig().healCapPerSecondBase + (getLevelPercent(level) * getConfig().healCapPerSecondFactor));
  }

  static PlayerRef resolveDamageOwner(EntityDamageEvent event) {
    if (!(event instanceof EntityDamageByEntityEvent damage)) {
      return null;
    }
    PlayerRef source = resolveDamageOwner(damage.causingEntity(), damage.damager());
    if (source != null) {
      return source;
    }
    return resolveDamageOwner(damage.damager());
  }

  static PlayerRef resolveDamageOwner(EntityRef causingEntity, EntityRef directEntity) {
    if (causingEntity instanceof PlayerRef player) {
      return player;
    }
    return directEntity instanceof PlayerRef player ? player : null;
  }

  static PlayerRef resolveDamageOwner(EntityRef source) {
    if (source instanceof PlayerRef player) {
      return player;
    }
    if (source instanceof ProjectileRef projectile) {
      return projectile.shooter() instanceof PlayerRef player ? player : null;
    }
    if (source != null && source.responsibleEntity() instanceof PlayerRef player) {
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

  private record DamageSnapshot(Loc location, boolean player, boolean protectedFriendly, double finalDamage) {
  }
}
