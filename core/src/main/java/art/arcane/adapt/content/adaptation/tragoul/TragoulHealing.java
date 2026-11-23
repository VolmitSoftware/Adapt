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
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.TragoulMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.fx.ParticleData;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class TragoulHealing extends SimpleAdaptation<TragoulHealing.Config> {
  private static final Rgb HEAL_CRIMSON = new Rgb(150, 0, 10);
  private static final Rgb HEAL_PINK = new Rgb(210, 40, 40);

  private final AtomicBoolean acceptingWork = new AtomicBoolean(true);

  public TragoulHealing() {
    super("tragoul-healing");
    registerConfiguration(TragoulHealing.Config.class);
    setIcon(Id.minecraft("glistering_melon_slice"));
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("redstone"))
        .key("challenge_tragoul_healing_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("red_dye"))
            .key("challenge_tragoul_healing_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_tragoul_healing_500", "tragoul.healing.health-stolen", 500, 400);
    registerMilestone("challenge_tragoul_healing_10k", "tragoul.healing.health-stolen", 10000, 1500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, C.RED, "- ", Form.f(getDrainDamage(level), 1), 1);
    v.addLore(C.GREEN + AdaptLanguage.text(TragoulMessages.HEALING_LORE2));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageEvent event) {
    if (!acceptingWork.get() || TragoulReactiveDamage.isActive() || event.finalDamage() <= 0D
        || !(event.entity() instanceof PlayerRef defender)) {
      return;
    }

    LivingRef attacker = resolveAttacker(event);
    if (attacker == null || defender.uuid().equals(attacker.uuid())) {
      return;
    }

    int level = getActiveLevel(defender);
    if (level <= 0 || getDrainDamage(level) <= 0D) {
      return;
    }
    Tasks.runEntity(attacker, () -> captureAttackerOwned(defender, attacker));
  }

  @Override
  public void unregister() {
    acceptingWork.set(false);
    super.unregister();
  }

  private void captureAttackerOwned(PlayerRef defender, LivingRef attacker) {
    if (!acceptingWork.get() || !attacker.isValid() || attacker.isDead()) {
      return;
    }

    Loc location = attacker.location();
    AttackerSnapshot snapshot = new AttackerSnapshot(
        attacker,
        attacker.uuid(),
        location,
        attacker instanceof PlayerRef,
        isProtectedAttacker(defender, attacker));
    Tasks.runEntity(defender, () -> authorizeDrainOwnerOwned(defender, snapshot));
  }

  private void authorizeDrainOwnerOwned(PlayerRef defender, AttackerSnapshot attacker) {
    if (!acceptingWork.get() || !defender.isOnline() || attacker.protectedFriendly()
        || !defender.world().equals(attacker.location().world())) {
      return;
    }

    int level = getActiveLevel(defender);
    if (level <= 0 || !canAffect(defender, attacker)) {
      return;
    }

    double damage = getDrainDamage(level);
    if (damage > 0D) {
      Tasks.runEntity(attacker.entity(), () -> applyDrainAttackerOwned(defender, attacker, damage));
    }
  }

  private boolean canAffect(PlayerRef defender, AttackerSnapshot attacker) {
    return attacker.player() ? canPVP(defender, attacker.location()) : canPVE(defender, attacker.location());
  }

  private boolean isProtectedAttacker(PlayerRef defender, LivingRef attacker) {
    if (MinionIdentity.isMinion(attacker)) {
      UUID ownerId = MinionIdentity.ownerOf(attacker);
      return ownerId == null || defender.uuid().equals(ownerId);
    }
    return isProtectedFriendly(defender, attacker);
  }

  private void applyDrainAttackerOwned(PlayerRef defender, AttackerSnapshot attacker, double damage) {
    LivingRef target = attacker.entity();
    if (!acceptingWork.get() || !target.isValid() || target.isDead()) {
      return;
    }

    double healthBefore = target.health();
    Loc drainOrigin = target.location();
    TragoulReactiveDamage.apply(() -> target.damage(damage, defender));

    double drained = Math.max(0D, healthBefore - target.health());
    if (drained > 0D) {
      Tasks.runEntity(defender, () -> completeDrainOwnerOwned(defender, drainOrigin, drained), 1);
    }
  }

  private void completeDrainOwnerOwned(PlayerRef defender, Loc drainOrigin, double drained) {
    if (!acceptingWork.get() || !defender.isOnline() || defender.isDead()) {
      return;
    }

    double attributeMaximum = defender.attributeValue(Attributes.MAX_HEALTH);
    double maximumHealth = Double.isNaN(attributeMaximum) ? 20D : attributeMaximum;
    double currentHealth = defender.health();
    double healed = restoredHealth(currentHealth, maximumHealth, drained) - currentHealth;
    if (healed <= 0D) {
      return;
    }

    defender.setHealth(currentHealth + healed);
    addStat(defender, "tragoul.healing.health-stolen", healed);
    playDrainTether(drainOrigin, defender.location());
    fx(defender.location().add(0, 1.0D, 0), FxPriority.TRANSITION)
        .dustBurst(HEAL_PINK, 3, 0.18D, 0.8F)
        .sound(Sounds.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.25F, 1.6F);
  }

  private double getDrainDamage(int level) {
    return drainDamage(getConfig().drainDamageStart, getConfig().drainDamageEnd, level, getMaxLevel());
  }

  private void playDrainTether(Loc attackerLocation, Loc playerLocation) {
    Loc from = attackerLocation.add(0, 1.0D, 0);
    double dx = playerLocation.x() - from.x();
    double dy = (playerLocation.y() + 1.0D) - from.y();
    double dz = playerLocation.z() - from.z();
    ParticleData.DustTransition transition = new ParticleData.DustTransition(HEAL_CRIMSON, HEAL_PINK, 1.0F);
    timeline(from)
        .duration(5)
        .priority(FxPriority.TRAIL)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          fx.particle(Particles.DUST_COLOR_TRANSITION, 2, dx * progress, dy * progress, dz * progress, 0.05D, 0D, transition);
          if (tick == 0) {
            fx.sound(Sounds.PARTICLE_SOUL_ESCAPE, 0.35F, 1.5F);
          }
        })
        .start();
  }

  static LivingRef resolveAttacker(EntityDamageEvent event) {
    if (!(event instanceof EntityDamageByEntityEvent damage)) {
      return null;
    }
    LivingRef source = resolveAttacker(damage.causingEntity(), damage.damager());
    if (source != null) {
      return source;
    }
    return resolveAttacker(damage.damager());
  }

  static LivingRef resolveAttacker(EntityRef causingEntity, EntityRef directEntity) {
    if (causingEntity instanceof LivingRef living) {
      return living;
    }
    return directEntity instanceof LivingRef living ? living : null;
  }

  static LivingRef resolveAttacker(EntityRef source) {
    if (source instanceof LivingRef living) {
      return living;
    }
    return source instanceof ProjectileRef projectile && projectile.shooter() instanceof LivingRef shooter
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
      baseCost = 5;
      costFactor = 0.6;
      initialCost = 3;
    }
  }

  private record AttackerSnapshot(LivingRef entity, UUID entityId, Loc location,
                                  boolean player, boolean protectedFriendly) {
  }
}
