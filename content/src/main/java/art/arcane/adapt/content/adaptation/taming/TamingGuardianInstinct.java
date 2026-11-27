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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;
import java.util.UUID;

public class TamingGuardianInstinct extends SimpleAdaptation<TamingGuardianInstinct.Config> {
  public static final Id ID = Id.adapt("tame/guardian_instinct");
  private final Cooldowns interceptCd = cooldowns();
  private final TameInterceptor interceptor = new TameInterceptor(this);

  public TamingGuardianInstinct() {
    super(ID);
    registerConfiguration(Config.class);
    setLocalizationKey("adapt.taming.guardian_instinct");
    setIcon(Id.minecraft("shield"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("shield"))
        .key("challenge_taming_guardian_250")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_chestplate"))
            .key("challenge_taming_guardian_2500")
            .frame(AdvancementFrame.CHALLENGE)
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
    statLore(v, Format.pc(getInterceptChance(level), 0), 1);
    statLore(v, TextFormat.YELLOW, "* ", Format.pc(getPetReduction(level), 0), 2);
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

    if (!interceptor.intercept(owner, damage, getInterceptRadius(level), getPetReduction(level), getConfig().leapStrength)) {
      return;
    }

    interceptCd.mark(ownerId, getConfig().cooldownMillis);
    e.setCancelled(true);
    addStat(owner, "taming.guardian-instinct.intercepts", 1);
    xp(owner, damage * getConfig().xpPerDamageIntercepted);
  }

  static void interceptFx(Adaptation<?> owner, Loc ownerChest) {
    Fx.now(owner, ownerChest, FxPriority.COMBAT)
        .dustBurst(Rgb.of(0x8FD3FF), 4, 0.3D, 1.0F)
        .chord(Sounds.ITEM_SHIELD_BLOCK, 0.7F, 1.1F, Sounds.ENTITY_WOLF_HURT, 0.4F, 1.3F);
  }

  public static void leapFx(Adaptation<?> owner, LivingRef pet, Loc ownerChest) {
    Fx.now(owner, pet, FxPriority.COMBAT)
        .burst(Particles.CRIT, 6, 0.25D)
        .line(Particles.CRIT, ownerChest.x(), ownerChest.y(), ownerChest.z(), 6)
        .sound(Sounds.ENTITY_WOLF_GROWL, 0.6F, 1.4F);
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
    @ConfigDoc(value = "Controls Intercept Chance Base for the Taming Guardian Instinct adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double interceptChanceBase = 0.35;
    @ConfigDoc(value = "Controls Intercept Chance Factor for the Taming Guardian Instinct adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double interceptChanceFactor = 0.45;
    @ConfigDoc(value = "Controls Max Intercept Chance for the Taming Guardian Instinct adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxInterceptChance = 0.8;
    @ConfigDoc(value = "Controls Pet Reduction Base for the Taming Guardian Instinct adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double petReductionBase = 0.4;
    @ConfigDoc(value = "Controls Pet Reduction Factor for the Taming Guardian Instinct adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double petReductionFactor = 0.35;
    @ConfigDoc(value = "Controls Max Pet Reduction for the Taming Guardian Instinct adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxPetReduction = 0.7;
    @ConfigDoc(value = "Controls Radius Base for the Taming Guardian Instinct adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 8.0;
    @ConfigDoc(value = "Controls Radius Factor for the Taming Guardian Instinct adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 8.0;
    @ConfigDoc(value = "Leap velocity applied to a pet as it intercepts a projectile.", impact = "Higher values make pets lunge toward the owner more aggressively.")
    double leapStrength = 0.8;
    @ConfigDoc(value = "Cooldown in milliseconds between projectile intercepts.", impact = "Higher values stop a single pet from tanking every arrow in a barrage.")
    long cooldownMillis = 1200;
    @ConfigDoc(value = "Xp granted per point of intercepted damage.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerDamageIntercepted = 2.0;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
