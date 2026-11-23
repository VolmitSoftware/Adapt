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

package art.arcane.adapt.content.skill;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.api.xp.CombatXp;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.content.adaptation.taming.TamingAlphasCommand;
import art.arcane.adapt.content.adaptation.taming.TamingBattleBond;
import art.arcane.adapt.content.adaptation.taming.TamingBeastRecall;
import art.arcane.adapt.content.adaptation.taming.TamingDamage;
import art.arcane.adapt.content.adaptation.taming.TamingFetch;
import art.arcane.adapt.content.adaptation.taming.TamingGuardianInstinct;
import art.arcane.adapt.content.adaptation.taming.TamingHealthBoost;
import art.arcane.adapt.content.adaptation.taming.TamingHealthRegeneration;
import art.arcane.adapt.content.adaptation.taming.TamingLastBreath;
import art.arcane.adapt.content.adaptation.taming.TamingMountedTactics;
import art.arcane.adapt.content.adaptation.taming.TamingPackLeaderAura;
import art.arcane.adapt.content.adaptation.taming.TamingSharedPain;
import art.arcane.adapt.content.adaptation.taming.TamingStableHand;
import art.arcane.adapt.content.adaptation.taming.TamingWildEmpathy;
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.SkillMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LastDamage;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityBreedEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityTameEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkillTaming extends SimpleSkill<SkillTaming.Config> {
  private final Cooldowns xpCooldowns = cooldowns();
  private final Map<String, Long> paidBreedingPairs = new ConcurrentHashMap<>();

  public SkillTaming() {
    super("taming", SkillPresentation.of(SkillMessages.TAMING_NAME, SkillMessages.TAMING_ICON, SkillMessages.TAMING_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.GOLD);
    setInterval(3480);
    setIcon(Id.minecraft("lead"));
    registerAdaptation(new TamingHealthBoost());
    registerAdaptation(new TamingDamage());
    registerAdaptation(new TamingHealthRegeneration());
    registerAdaptation(new TamingPackLeaderAura());
    registerAdaptation(new TamingBeastRecall());
    registerAdaptation(new TamingSharedPain());
    registerAdaptation(new TamingMountedTactics());
    registerAdaptation(new TamingFetch());
    registerAdaptation(new TamingAlphasCommand());
    registerAdaptation(new TamingGuardianInstinct());
    registerAdaptation(new TamingStableHand());
    registerAdaptation(new TamingWildEmpathy());
    registerAdaptation(new TamingBattleBond());
    registerAdaptation(new TamingLastBreath());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("lead"))
        .key("challenge_taming_10")
        .model(CustomModel.get(Id.minecraft("lead"), "advancement", "taming", "challenge_taming_10"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("name_tag"))
            .key("challenge_taming_50")
            .model(CustomModel.get(Id.minecraft("name_tag"), "advancement", "taming", "challenge_taming_50"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Id.minecraft("golden_apple"))
                .key("challenge_taming_500")
                .model(CustomModel.get(Id.minecraft("golden_apple"), "advancement", "taming", "challenge_taming_500"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bone"))
        .key("challenge_pet_dmg_500")
        .model(CustomModel.get(Id.minecraft("bone"), "advancement", "taming", "challenge_pet_dmg_500"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_sword"))
            .key("challenge_pet_dmg_5k")
            .model(CustomModel.get(Id.minecraft("diamond_sword"), "advancement", "taming", "challenge_pet_dmg_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("lead"))
        .key("challenge_tamed_10")
        .model(CustomModel.get(Id.minecraft("lead"), "advancement", "taming", "challenge_tamed_10"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("name_tag"))
            .key("challenge_tamed_100")
            .model(CustomModel.get(Id.minecraft("name_tag"), "advancement", "taming", "challenge_tamed_100"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bone"))
        .key("challenge_pet_kills_25")
        .model(CustomModel.get(Id.minecraft("bone"), "advancement", "taming", "challenge_pet_kills_25"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("golden_apple"))
            .key("challenge_pet_kills_250")
            .model(CustomModel.get(Id.minecraft("golden_apple"), "advancement", "taming", "challenge_pet_kills_250"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_taming_10", "taming.bred", 10, () -> getConfig().challengeTamingReward);
    registerMilestone("challenge_taming_50", "taming.bred", 50, () -> getConfig().challengeTamingReward * 3);
    registerMilestone("challenge_taming_500", "taming.bred", 500, () -> getConfig().challengeTamingReward * 8);
    registerMilestone("challenge_pet_dmg_500", "taming.pet.damage", 500, () -> getConfig().challengePetDmgReward);
    registerMilestone("challenge_pet_dmg_5k", "taming.pet.damage", 5000, () -> getConfig().challengePetDmgReward * 3);
    registerMilestone("challenge_tamed_10", "taming.tamed", 10, () -> getConfig().challengeTamedReward);
    registerMilestone("challenge_tamed_100", "taming.tamed", 100, () -> getConfig().challengeTamedReward * 3);
    registerMilestone("challenge_pet_kills_25", "taming.pet.kills", 25, () -> getConfig().challengePetKillsReward);
    registerMilestone("challenge_pet_kills_250", "taming.pet.kills", 250, () -> getConfig().challengePetKillsReward * 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityBreedEvent e) {
    if (!(e.breeder() instanceof PlayerRef p)) {
      return;
    }

    shouldReturnForPlayer(p, e, () -> {
      addStat(p, "taming.bred", 1);
      long now = System.currentTimeMillis();
      paidBreedingPairs.values().removeIf(paidAt -> now - paidAt >= getConfig().breedPairCooldownMs);
      if (paidBreedingPairs.putIfAbsent(pairKey(e.mother().uuid(), e.father().uuid()), now) == null) {
        xp(p, e.child().location(), getConfig().breedXp, "taming:breed");
      }
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    PlayerRef p = onlineTamedOwner(e.damager());
    if (p != null) {
      shouldReturnForPlayer(p, e, () -> {
        if (isOnCooldown(p)) {
          return;
        }
        double creditedDamage = CombatXp.credit(getPlayer(p).getData().getCombatXp(), getName(), p, e);
        if (creditedDamage <= 0D) {
          return;
        }
        addStat(p, "taming.pet.damage", creditedDamage);
        setCooldown(p);
        xp(p, e.entity().location(), creditedDamage * getConfig().tameDamageXPMultiplier, "taming:pet-combat");
      });
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityTameEvent e) {
    PlayerRef p = e.owner();
    if (p == null) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> {
      addStat(p, "taming.tamed", 1);
      xp(p, e.entity().location(), getConfig().tameSuccessXP);
      timeline(e.entity())
          .duration(8)
          .priority(FxPriority.TRANSITION)
          .cullRadius(24)
          .frame((fx, tick, progress) -> {
            fx.ring(Particles.VILLAGER_HAPPY, 0.6D - (0.5D * progress), 12, 0.4D);
            if (tick == 0) {
              fx.chord(Sounds.ENTITY_WOLF_PANT, 0.7F, 1.2F, Sounds.BLOCK_NOTE_BLOCK_BELL, 0.5F, 1.6F);
            }
            if (progress >= 1.0D) {
              fx.burst(Particles.HEART, 3, 0.15D).sound(Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.0F);
            }
          })
          .start();
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    LivingRef victim = e.entity();
    if (e.killer() != null) {
      return;
    }

    if (MinionIdentity.isMinion(victim) || CombatXp.isGraveMob(victim)) {
      return;
    }

    LastDamage lastDamage = victim.lastDamage();
    if (lastDamage != null && lastDamage.byEntity()) {
      PlayerRef p = onlineTamedOwner(lastDamage.damager());
      if (p != null) {
        shouldReturnForPlayer(p, () -> {
          addStat(p, "taming.pet.kills", 1);
          if (MobOrigin.paysKillReward(p, victim)) {
            xp(p, victim.location(), petKillXp(getConfig().petKillXP, getConfig().petKillMaxHealthXpMultiplier,
                victim.maxHealth()), "taming:pet-kill");
          }
          fx(victim.location(), FxPriority.COMBAT)
              .burst(Particles.CRIT_MAGIC, 4, 0.25D)
              .sound(Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4F, 1.4F);
        });
      }
    }
  }

  static String pairKey(UUID first, UUID second) {
    return first.compareTo(second) <= 0 ? first + ":" + second : second + ":" + first;
  }

  static double petKillXp(double baseXp, double perMaxHealth, double maxHealth) {
    double healthBonus = Double.isFinite(maxHealth) && maxHealth > 0D ? Math.max(0D, perMaxHealth) * maxHealth : 0D;
    return Math.max(0D, baseXp) + healthBonus;
  }

  private static PlayerRef onlineTamedOwner(EntityRef pet) {
    if (pet == null || !pet.has(EntityTrait.TAMEABLE) || !AdaptCore.platform().host().animals().isTamed(pet)) {
      return null;
    }
    UUID ownerId = pet.tameOwner();
    return ownerId == null ? null : AdaptCore.platform().host().player(ownerId);
  }

  private boolean isOnCooldown(PlayerRef p) {
    return !xpCooldowns.isReady(p.uuid(), getConfig().cooldownDelay);
  }

  private void setCooldown(PlayerRef p) {
    xpCooldowns.mark(p.uuid());
  }


  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @NoArgsConstructor
  protected static class Config extends SkillConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    String skillColor = "&6";
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP for breeding two animals.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double breedXp = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds before the same two parents can pay breeding XP again.", impact = "Higher values reduce repeated breeding XP from one pair.")
    long breedPairCooldownMs = 300000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Taming skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 1500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Tame Damage XPMultiplier for the Taming skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double tameDamageXPMultiplier = 8.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls XP awarded for successfully taming an animal.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double tameSuccessXP = 150;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls XP awarded when a pet kills a mob.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double petKillXP = 25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra pet kill XP per point of the victim's maximum health.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double petKillMaxHealthXpMultiplier = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Taming Reward for the Taming skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeTamingReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Pet Damage Reward for the Taming skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengePetDmgReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Tamed Reward for the Taming skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeTamedReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Pet Kills Reward for the Taming skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengePetKillsReward = 500;
  }
}
