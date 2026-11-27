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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.XpSource;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.BarterGesture;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.xp.CombatXp;
import art.arcane.adapt.api.xp.MobOrigin;
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
import art.arcane.adapt.content.adaptation.taming.blocks.TamingBoon;
import art.arcane.adapt.content.adaptation.taming.blocks.TamingFightStrike;
import art.arcane.adapt.content.adaptation.taming.blocks.TamingFuel;
import art.arcane.adapt.content.adaptation.taming.blocks.TamingState;
import art.arcane.adapt.content.adaptation.taming.blocks.TamingTrigger;
import art.arcane.adapt.content.adaptation.taming.blocks.TamingWorkStrike;
import art.arcane.adapt.content.adaptation.taming.upgraded.Alpha;
import art.arcane.adapt.content.adaptation.taming.upgraded.BloodBond;
import art.arcane.adapt.content.adaptation.taming.upgraded.OneMind;
import art.arcane.adapt.api.ability.Minions;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
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
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkillTaming extends SimpleSkill<SkillTaming.Config> {
  public static final Id ID = Id.adapt("taming");
  private final XpSource tame = xpSource("tame", Text.key("adapt.xp_guide.taming.tame"));
  private final XpSource breed = xpSource("breed", Text.key("adapt.xp_guide.taming.breed"));
  private final XpSource petCombat = xpSource("pet-combat", Text.key("adapt.xp_guide.taming.pet_combat"));
  private final XpSource petKill = xpSource("pet-kill", Text.key("adapt.xp_guide.taming.pet_kill"));
  private final Cooldowns xpCooldowns = cooldowns();
  private final Map<String, Long> paidBreedingPairs = new ConcurrentHashMap<>();


  public SkillTaming() {
    super(ID);
    xpNote(Text.key("adapt.xp_guide.farmed_mobs"));
    registerConfiguration(Config.class);
    setColor(Rgb.of(0xFFAA00));
    setInterval(3480);
    setIcon(Id.minecraft("lead"));
    registerAdaptation(TamingHealthBoost.ID, TamingHealthBoost::new);
    registerAdaptation(TamingDamage.ID, TamingDamage::new);
    registerAdaptation(TamingHealthRegeneration.ID, TamingHealthRegeneration::new);
    registerAdaptation(TamingPackLeaderAura.ID, TamingPackLeaderAura::new);
    registerAdaptation(TamingBeastRecall.ID, TamingBeastRecall::new);
    registerAdaptation(TamingSharedPain.ID, TamingSharedPain::new);
    registerAdaptation(TamingMountedTactics.ID, TamingMountedTactics::new);
    registerAdaptation(TamingFetch.ID, TamingFetch::new);
    registerAdaptation(TamingAlphasCommand.ID, TamingAlphasCommand::new);
    registerAdaptation(TamingGuardianInstinct.ID, TamingGuardianInstinct::new);
    registerAdaptation(TamingStableHand.ID, TamingStableHand::new);
    registerAdaptation(TamingWildEmpathy.ID, TamingWildEmpathy::new);
    registerAdaptation(TamingBattleBond.ID, TamingBattleBond::new);
    registerAdaptation(TamingLastBreath.ID, TamingLastBreath::new);
    registerMutationKit(MutationKit.builder()
        .words(Text.key("adapt.mutated.noun.taming"), Text.key("adapt.mutated.adjective.taming"))
        .trigger(TriggerKind.FIGHT, TamingTrigger::new)
        .strikes(TamingFightStrike::new, TamingWorkStrike::new)
        .boon(TamingBoon::new)
        .fuel(TamingFuel::new)
        .state(TamingState::new)
        .upgrade(Alpha.ID, Alpha::new,
            List.of(TamingDamage.ID, TamingHealthBoost.ID, TamingHealthRegeneration.ID, TamingPackLeaderAura.ID), Set.of())
        .toolUpgrade(OneMind.ID, OneMind::new, List.of(TamingAlphasCommand.ID, TamingBeastRecall.ID, TamingFetch.ID), Set.of())
        .upgrade(BloodBond.ID, BloodBond::new,
            List.of(TamingSharedPain.ID, TamingGuardianInstinct.ID, TamingLastBreath.ID), Set.of())
        .sounds(SoundPalette.of(Id.minecraft("entity.wolf.ambient"), Id.minecraft("entity.parrot.eat"),
            Id.minecraft("entity.parrot.fly"), Id.minecraft("entity.wolf.pant")))
        .tint(Rgb.of(0xcfc4b0))
        // Alpha's Command with a bone.
        .reserve(BarterGesture.SNEAK_LEFT_CLICK)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("lead"))
        .key("challenge_taming_10")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("name_tag"))
            .key("challenge_taming_50")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Id.minecraft("golden_apple"))
                .key("challenge_taming_500")
                .frame(AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bone"))
        .key("challenge_pet_dmg_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_sword"))
            .key("challenge_pet_dmg_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("lead"))
        .key("challenge_tamed_10")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("name_tag"))
            .key("challenge_tamed_100")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bone"))
        .key("challenge_pet_kills_25")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("golden_apple"))
            .key("challenge_pet_kills_250")
            .frame(AdvancementFrame.CHALLENGE)
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
    registerGroup(Text.key("adapt.skill.taming.group.care"), List.of(Id.parseAdapt("adapt:tame/wild_empathy"), Id.parseAdapt("adapt:tame/fetch"), Id.parseAdapt("adapt:tame/stable_hand"), Id.parseAdapt("adapt:tame/health"), Id.parseAdapt("adapt:tame/health_regeneration"), Id.parseAdapt("adapt:tame/pack_leader_aura"), Id.parseAdapt("adapt:tame/beast_recall")));
    registerGroup(Text.key("adapt.skill.taming.group.battle"), List.of(Id.parseAdapt("adapt:tame/damage"), Id.parseAdapt("adapt:tame/battle_bond"), Id.parseAdapt("adapt:tame/alphas_command"), Id.parseAdapt("adapt:tame/guardian_instinct"), Id.parseAdapt("adapt:tame/shared_pain"), Id.parseAdapt("adapt:tame/mounted_tactics"), Id.parseAdapt("adapt:tame/last_breath")));
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
        breed.award(p, e.child().location(), getConfig().breedXp);
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
        double creditedDamage = CombatXp.credit(getId().toString(), p, e);
        if (creditedDamage <= 0D) {
          return;
        }
        addStat(p, "taming.pet.damage", creditedDamage);
        setCooldown(p);
        petCombat.award(p, e.entity().location(), creditedDamage * getConfig().tameDamageXPMultiplier);
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
      tame.award(p, e.entity().location(), getConfig().tameSuccessXP);
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

    if (Minions.isMinion(victim) || CombatXp.isGraveMob(victim)) {
      return;
    }

    LastDamage lastDamage = victim.lastDamage();
    if (lastDamage != null && lastDamage.byEntity()) {
      PlayerRef p = onlineTamedOwner(lastDamage.damager());
      if (p != null) {
        shouldReturnForPlayer(p, () -> {
          addStat(p, "taming.pet.kills", 1);
          if (MobOrigin.paysKillReward(p, victim)) {
            petKill.award(p, victim.location(), petKillXp(getConfig().petKillXP, getConfig().petKillMaxHealthXpMultiplier,
                victim.maxHealth()));
          }
          fx(victim.location(), FxPriority.COMBAT)
              .burst(Particles.CRIT, 4, 0.25D)
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
    if (pet == null || !pet.has(EntityTrait.TAMEABLE) || !AdaptApi.platform().host().animals().isTamed(pet)) {
      return null;
    }
    UUID ownerId = pet.tameOwner();
    return ownerId == null ? null : AdaptApi.platform().host().player(ownerId);
  }

  private boolean isOnCooldown(PlayerRef p) {
    return !xpCooldowns.isReady(p.uuid(), getConfig().cooldownDelay);
  }

  private void setCooldown(PlayerRef p) {
    xpCooldowns.mark(p.uuid());
  }



  protected static class Config extends SkillConfig {
    @ConfigDoc(value = "XP for breeding two animals.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double breedXp = 40;
    @ConfigDoc(value = "Milliseconds before the same two parents can pay breeding XP again.", impact = "Higher values reduce repeated breeding XP from one pair.")
    long breedPairCooldownMs = 300000;
    @ConfigDoc(value = "Controls Cooldown Delay for the Taming skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 1500;
    @ConfigDoc(value = "Controls Tame Damage XPMultiplier for the Taming skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double tameDamageXPMultiplier = 8.0;
    @ConfigDoc(value = "Controls XP awarded for successfully taming an animal.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double tameSuccessXP = 150;
    @ConfigDoc(value = "Controls XP awarded when a pet kills a mob.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double petKillXP = 25;
    @ConfigDoc(value = "Extra pet kill XP per point of the victim's maximum health.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double petKillMaxHealthXpMultiplier = 1;
    @ConfigDoc(value = "Controls Challenge Taming Reward for the Taming skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeTamingReward = 500;
    @ConfigDoc(value = "Controls Challenge Pet Damage Reward for the Taming skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengePetDmgReward = 500;
    @ConfigDoc(value = "Controls Challenge Tamed Reward for the Taming skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeTamedReward = 500;
    @ConfigDoc(value = "Controls Challenge Pet Kills Reward for the Taming skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengePetKillsReward = 500;

    public Config() {
    }
  }
}
