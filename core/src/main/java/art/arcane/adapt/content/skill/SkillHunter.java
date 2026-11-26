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

import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.golem.GolemKillCredit;
import art.arcane.adapt.api.skill.AdaptationGroup;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.api.xp.CombatXp;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.content.adaptation.hunter.HunterAdrenaline;
import art.arcane.adapt.content.adaptation.hunter.HunterBigGameHunter;
import art.arcane.adapt.content.adaptation.hunter.HunterBloodTrail;
import art.arcane.adapt.content.adaptation.hunter.HunterDropToInventory;
import art.arcane.adapt.content.adaptation.hunter.HunterInvis;
import art.arcane.adapt.content.adaptation.hunter.HunterJumpBoost;
import art.arcane.adapt.content.adaptation.hunter.HunterLuck;
import art.arcane.adapt.content.adaptation.hunter.HunterPredatorFocus;
import art.arcane.adapt.content.adaptation.hunter.HunterRegen;
import art.arcane.adapt.content.adaptation.hunter.HunterResistance;
import art.arcane.adapt.content.adaptation.hunter.HunterSnareLine;
import art.arcane.adapt.content.adaptation.hunter.HunterSpeed;
import art.arcane.adapt.content.adaptation.hunter.HunterStrength;
import art.arcane.adapt.content.adaptation.hunter.HunterTrophySkinner;
import art.arcane.adapt.content.adaptation.hunter.blocks.HunterBoon;
import art.arcane.adapt.content.adaptation.hunter.blocks.HunterFightStrike;
import art.arcane.adapt.content.adaptation.hunter.blocks.HunterFuel;
import art.arcane.adapt.content.adaptation.hunter.blocks.HunterState;
import art.arcane.adapt.content.adaptation.hunter.blocks.HunterTrigger;
import art.arcane.adapt.content.adaptation.hunter.blocks.HunterWorkStrike;
import art.arcane.adapt.content.adaptation.hunter.upgraded.ApexPredator;
import art.arcane.adapt.content.adaptation.hunter.upgraded.BloodFrenzy;
import art.arcane.adapt.content.adaptation.hunter.upgraded.StalkersTrail;
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.GroupMessages;
import art.arcane.adapt.localization.catalog.SkillMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SkillHunter extends SimpleSkill<SkillHunter.Config> {
  private static final Id CREEPER = Id.minecraft("creeper");
  private static final Set<Id> BOSSES = Set.of(Id.minecraft("ender_dragon"), Id.minecraft("wither"),
      Id.minecraft("elder_guardian"), Id.minecraft("warden"));

  private final Cooldowns cooldowns = cooldowns();

  static final List<AdaptationGroup> GROUPS = List.of(
      AdaptationGroup.of(GroupMessages.HUNTER_STRUCK, "hunter-adrenaline", "hunter-regen", "hunter-resistance",
          "hunter-strength", "hunter-speed", "hunter-luck", "hunter-jumpboost", "hunter-invis"),
      AdaptationGroup.of(GroupMessages.HUNTER_HUNT, "hunter-blood-trail", "hunter-predator-focus", "hunter-big-game",
          "hunter-snare-line", "hunter-trophy-skinner", "hunter-drop-to-inventory")
  );

  public SkillHunter() {
    super("hunter", SkillPresentation.of(SkillMessages.HUNTER_NAME, SkillMessages.HUNTER_ICON, SkillMessages.HUNTER_DESCRIPTION,
        SkillMessages.HUNTER_QUIP, SkillMessages.HUNTER_UNLOCK_QUIP_1, SkillMessages.HUNTER_UNLOCK_QUIP_2));
    registerConfiguration(Config.class);
    setColor(C.RED);
    setInterval(4150);
    setIcon(Id.minecraft("bone"));
    registerAdaptation(new HunterAdrenaline());
    registerAdaptation(new HunterRegen());
    registerAdaptation(new HunterInvis());
    registerAdaptation(new HunterJumpBoost());
    registerAdaptation(new HunterLuck());
    registerAdaptation(new HunterSpeed());
    registerAdaptation(new HunterStrength());
    registerAdaptation(new HunterResistance());
    registerAdaptation(new HunterDropToInventory());
    registerAdaptation(new HunterTrophySkinner());
    registerAdaptation(new HunterPredatorFocus());
    registerAdaptation(new HunterBigGameHunter());
    registerAdaptation(new HunterBloodTrail());
    registerAdaptation(new HunterSnareLine());
    registerUpgrades(new BloodFrenzy(), new ApexPredator(), new StalkersTrail());
    registerBlocks(new HunterTrigger(), new HunterFightStrike(), new HunterWorkStrike(), new HunterBoon(), new HunterFuel(),
        new HunterState());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bone"))
        .key("challenge_novice_hunter")
        .model(CustomModel.get(Id.minecraft("bone"), "advancement", "hunter", "challenge_novice_hunter"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("iron_sword"))
            .key("challenge_intermediate_hunter")
            .model(CustomModel.get(Id.minecraft("iron_sword"), "advancement", "hunter", "challenge_intermediate_hunter"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Id.minecraft("diamond_sword"))
                .key("challenge_advanced_hunter")
                .model(CustomModel.get(Id.minecraft("diamond_sword"), "advancement", "hunter", "challenge_advanced_hunter"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("creeper_head"))
        .key("challenge_creeper_conqueror")
        .model(CustomModel.get(Id.minecraft("creeper_head"), "advancement", "hunter", "challenge_creeper_conqueror"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("tnt"))
            .key("challenge_creeper_annihilator")
            .model(CustomModel.get(Id.minecraft("tnt"), "advancement", "hunter", "challenge_creeper_annihilator"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bone"))
        .key("challenge_kills_500")
        .model(CustomModel.get(Id.minecraft("bone"), "advancement", "hunter", "challenge_kills_500"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("wither_skeleton_skull"))
            .key("challenge_kills_5k")
            .model(CustomModel.get(Id.minecraft("wither_skeleton_skull"), "advancement", "hunter", "challenge_kills_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("dragon_head"))
        .key("challenge_boss_1")
        .model(CustomModel.get(Id.minecraft("dragon_head"), "advancement", "hunter", "challenge_boss_1"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("nether_star"))
            .key("challenge_boss_10")
            .model(CustomModel.get(Id.minecraft("nether_star"), "advancement", "hunter", "challenge_boss_10"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());

    registerMilestone("challenge_novice_hunter", "killed.monsters", 100, () -> getConfig().killsChallengeReward);
    registerMilestone("challenge_intermediate_hunter", "killed.monsters", 500, () -> getConfig().killsChallengeReward * 3);
    registerMilestone("challenge_advanced_hunter", "killed.monsters", 5000, () -> getConfig().killsChallengeReward * 8);
    registerMilestone("challenge_creeper_conqueror", "killed.creepers", 50, () -> getConfig().killsChallengeReward);
    registerMilestone("challenge_creeper_annihilator", "killed.creepers", 200, () -> getConfig().killsChallengeReward * 3);
    registerMilestone("challenge_kills_500", "killed.kills", 500, () -> getConfig().killsChallengeReward);
    registerMilestone("challenge_kills_5k", "killed.kills", 5000, () -> getConfig().killsChallengeReward * 3);
    registerMilestone("challenge_boss_1", "hunter.boss.kills", 1, () -> getConfig().bossKillReward);
    registerMilestone("challenge_boss_10", "hunter.boss.kills", 10, () -> getConfig().bossKillReward * 5);
  }

  @Override
  public List<AdaptationGroup> getAdaptationGroups() {
    return GROUPS;
  }

  private void handleCooldownAndXp(PlayerRef p, double xpAmount, String rewardKey) {
    if (!cooldowns.isReady(p.uuid(), getConfig().cooldownDelay)) {
      return;
    }
    cooldowns.mark(p.uuid());
    xp(p, xpAmount, rewardKey);
  }

  private void bossCelebration(Loc center) {
    timeline(center)
        .duration(12)
        .priority(FxPriority.TRANSITION)
        .cullRadius(48)
        .frame((f, tick, progress) -> {
          f.dustRing(1.0D + (4.0D * progress), 24, 1.4F);
          f.ring(Particles.SOUL, 1.0D + (4.0D * progress), 16, 0.2D);
          if (tick == 0) {
            f.chord(Sounds.BLOCK_BEACON_DEACTIVATE, 0.8F, 0.6F, Sounds.ITEM_TOTEM_USE, 0.6F, 0.8F);
          }
          if (tick == 6) {
            f.sound(Sounds.ENTITY_ENDER_DRAGON_FLAP, 0.7F, 0.7F);
          }
        })
        .start();
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    LivingRef victim = e.entity();
    if (e.killer() == null || GolemKillCredit.isGolemKill(victim)) {
      return;
    }
    PlayerRef p = e.killer();

    if (!getConfig().getXpForAttackingWithTools) {
      return;
    }

    if (MinionIdentity.isMinion(victim) || CombatXp.isGraveMob(victim)) {
      return;
    }

    shouldReturnForPlayer(p, () -> {
      recordKillStats(p, victim);
      Id type = victim.type();
      if (!p.uuid().equals(victim.tameOwner())) {
        double healthMultiplier = getConfig().killMaxHealthXPMultiplier
            * (type.equals(CREEPER) ? getConfig().creeperKillMultiplier : 1D);
        double amount = killXp(victim.maxHealth(), healthMultiplier, getConfig().killFlatXp,
            MobOrigin.isFarmed(victim) ? getConfig().spawnerMobReductionXpMultiplier : 1D);
        handleCooldownAndXp(p, amount, "hunter:kill:" + type.legacyName().toLowerCase(Locale.ROOT));
      }
      if (BOSSES.contains(type)) {
        addStat(p, "hunter.boss.kills", 1);
        bossCelebration(victim.location().add(0, 1.0D, 0));
      }
    });
  }

  private void recordKillStats(PlayerRef p, EntityRef entity) {
    addStat(p, "killed.kills", 1);
    if (entity.has(EntityTrait.MONSTER)) {
      addStat(p, "killed.monsters", 1);
    }
    if (entity.type().equals(CREEPER)) {
      addStat(p, "killed.creepers", 1);
    }
  }

  static double killXp(double maxHealth, double healthMultiplier, double flatXp, double farmedMultiplier) {
    double healthXp = Double.isFinite(maxHealth) && maxHealth > 0D ? maxHealth * Math.max(0D, healthMultiplier) : 0D;
    return (healthXp + Math.max(0D, flatXp)) * Math.max(0D, farmedMultiplier);
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @NoArgsConstructor
  protected static class Config extends SkillConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    String skillColor = "&c";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Get Xp For Attacking With Tools for the Hunter skill.", impact = "True enables this behavior and false disables it.")
    boolean getXpForAttackingWithTools = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Creeper Kill Multiplier for the Hunter skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double creeperKillMultiplier = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Kill Max Health XPMultiplier for the Hunter skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double killMaxHealthXPMultiplier = 4.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Flat XP added to every kill reward.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double killFlatXp = 30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Hunter skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 1000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Kill reward multiplier for mobs from spawners, spawn eggs, buckets, breeding, portals, and golem or snowman builds.", impact = "Lower values make mob farms pay less.")
    double spawnerMobReductionXpMultiplier = 0.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Kills Challenge Reward for the Hunter skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double killsChallengeReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Boss Kill Reward for the Hunter skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bossKillReward = 1000;
  }
}
