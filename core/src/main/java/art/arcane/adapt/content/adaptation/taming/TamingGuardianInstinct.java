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

package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
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
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.UUID;

public class TamingGuardianInstinct extends SimpleAdaptation<TamingGuardianInstinct.Config> {
  private final Cooldowns interceptCd = cooldowns();

  public TamingGuardianInstinct() {
    super("tame-guardian-instinct");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.guardian_instinct");
    setIcon(Id.minecraft("shield"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("shield"))
        .key("challenge_taming_guardian_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_chestplate"))
            .key("challenge_taming_guardian_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_taming_guardian_250", "taming.guardian-instinct.intercepts", 250, 400);
    registerMilestone("challenge_taming_guardian_2500", "taming.guardian-instinct.intercepts", 2500, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TamingPreferences.PETS, TamingPreferences.HEALTH);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getInterceptChance(level), 0), 1);
    statLore(v, C.YELLOW, "* ", Form.pc(getPetReduction(level), 0), 2);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof PlayerRef owner) || !(e.damager() instanceof ProjectileRef)) {
      return;
    }

    int level = getActiveLevel(owner);
    if (level <= 0) {
      return;
    }

    double damage = e.finalDamage();
    if (damage <= 0) {
      return;
    }

    if (Math.random() > getInterceptChance(level)) {
      return;
    }

    UUID ownerId = owner.uuid();
    if (!interceptCd.isReady(ownerId, getConfig().cooldownMillis)) {
      return;
    }

    LivingRef pet = findNearestPet(owner, ownerId, getInterceptRadius(level), Math.max(0D, damage * (1D - getPetReduction(level))));
    if (pet == null) {
      return;
    }

    interceptCd.mark(ownerId, getConfig().cooldownMillis);
    double petDamage = Math.max(0.0, damage * (1.0 - getPetReduction(level)));
    e.setCancelled(true);

    Loc ownerChest = owner.location().add(0, 1, 0);
    double leapStrength = getConfig().leapStrength;
    Tasks.runEntity(pet, () -> applyIntercept(pet, ownerChest, petDamage, leapStrength));

    fx(ownerChest, FxPriority.COMBAT)
        .dustBurst(Rgb.of(0x8FD3FF), 4, 0.3D, 1.0F)
        .chord(Sounds.ITEM_SHIELD_BLOCK, 0.7F, 1.1F, Sounds.ENTITY_WOLF_HURT, 0.4F, 1.3F);
    addStat(owner, "taming.guardian-instinct.intercepts", 1);
    xp(owner, damage * getConfig().xpPerDamageIntercepted);
  }

  private LivingRef findNearestPet(PlayerRef owner, UUID ownerId, double radius, double petDamage) {
    LivingRef nearest = null;
    double nearestSquared = Double.MAX_VALUE;
    Loc origin = owner.location();
    for (EntityRef entity : owner.nearbyEntities(radius, radius, radius)) {
      if (!(entity instanceof LivingRef living) || !entity.has(EntityTrait.TAMEABLE)) {
        continue;
      }
      if (!preference(owner, TamingPreferences.PETS).accepts(living.type())
          || (preference(owner, TamingPreferences.HEALTH) != TamingPreferences.Health.DEFAULT
          && living.health() - petDamage < preference(owner, TamingPreferences.HEALTH).floor(living.maxHealth(), 0D))
          || !AdaptCore.platform().host().animals().isTamed(living) || !living.isValid() || living.isDead() || !isOwnedBy(living, ownerId)) {
        continue;
      }

      double distanceSquared = living.location().distanceSquared(origin);
      if (distanceSquared < nearestSquared) {
        nearestSquared = distanceSquared;
        nearest = living;
      }
    }
    return nearest;
  }

  private void applyIntercept(LivingRef pet, Loc ownerChest, double petDamage, double leapStrength) {
    if (!pet.isValid() || pet.isDead()) {
      return;
    }

    if (pet.location().sameWorld(ownerChest) && leapStrength > 0) {
      Vec3 leap = ownerChest.position().subtract(pet.location().position());
      if (leap.lengthSquared() > 1.0E-4) {
        Vec3 scaled = leap.normalize().multiply(leapStrength);
        pet.setVelocity(scaled.withY(Math.max(0.25, scaled.y())));
      }
    }

    fx(pet, FxPriority.COMBAT)
        .burst(Particles.CRIT, 6, 0.25D)
        .line(Particles.CRIT, ownerChest.x(), ownerChest.y(), ownerChest.z(), 6)
        .sound(Sounds.ENTITY_WOLF_GROWL, 0.6F, 1.4F);
    if (petDamage > 0.01) {
      pet.damage(petDamage, (EntityRef) null);
    }
  }

  private boolean isOwnedBy(LivingRef tameable, UUID ownerId) {
    UUID owner = tameable.tameOwner();
    return owner != null && ownerId.equals(owner);
  }

  private double getInterceptChance(int level) {
    return Math.min(getConfig().maxInterceptChance, getConfig().interceptChanceBase + (getLevelPercent(level) * getConfig().interceptChanceFactor));
  }

  private double getPetReduction(int level) {
    return Math.min(getConfig().maxPetReduction, getConfig().petReductionBase + (getLevelPercent(level) * getConfig().petReductionFactor));
  }

  private double getInterceptRadius(int level) {
    return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
  }


  @ConfigDescription("Nearby pets leap to intercept projectiles aimed at you, taking reduced damage themselves.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Intercept Chance Base for the Taming Guardian Instinct adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double interceptChanceBase = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Intercept Chance Factor for the Taming Guardian Instinct adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double interceptChanceFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Intercept Chance for the Taming Guardian Instinct adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxInterceptChance = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Pet Reduction Base for the Taming Guardian Instinct adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double petReductionBase = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Pet Reduction Factor for the Taming Guardian Instinct adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double petReductionFactor = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Pet Reduction for the Taming Guardian Instinct adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxPetReduction = 0.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Taming Guardian Instinct adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 8.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Taming Guardian Instinct adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 8.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Leap velocity applied to a pet as it intercepts a projectile.", impact = "Higher values make pets lunge toward the owner more aggressively.")
    double leapStrength = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown in milliseconds between projectile intercepts.", impact = "Higher values stop a single pet from tanking every arrow in a barrage.")
    long cooldownMillis = 1200;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Xp granted per point of intercepted damage.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerDamageIntercepted = 2.0;

    public Config() {
      costFactor = 0.7;
      initialCost = 4;
    }
  }
}
