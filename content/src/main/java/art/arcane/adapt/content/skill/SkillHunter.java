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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.XpSource;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.xp.CombatXp;
import art.arcane.adapt.api.xp.MobOrigin;
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
import art.arcane.adapt.content.golem.GolemKillCredit;
import art.arcane.adapt.api.ability.Minions;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SkillHunter extends SimpleSkill<SkillHunter.Config> {
  public static final Id ID = Id.adapt("hunter");
  private final XpSource kill = xpSource("kill", Text.key("adapt.xp_guide.hunter.kill"));
  private static final Id CREEPER = Id.minecraft("creeper");
  private static final Set<Id> BOSSES = Set.of(Id.minecraft("ender_dragon"), Id.minecraft("wither"),
      Id.minecraft("elder_guardian"), Id.minecraft("warden"));

  private final Cooldowns cooldowns = cooldowns();


  public SkillHunter() {
    super(ID);
    registerConfiguration(Config.class);
    setColor(Rgb.of(0xFF5555));
    setInterval(4150);
    setIcon(Id.minecraft("bone"));
    registerAdaptation(HunterAdrenaline.ID, HunterAdrenaline::new);
    registerAdaptation(HunterRegen.ID, HunterRegen::new);
    registerAdaptation(HunterInvis.ID, HunterInvis::new);
    registerAdaptation(HunterJumpBoost.ID, HunterJumpBoost::new);
    registerAdaptation(HunterLuck.ID, HunterLuck::new);
    registerAdaptation(HunterSpeed.ID, HunterSpeed::new);
    registerAdaptation(HunterStrength.ID, HunterStrength::new);
    registerAdaptation(HunterResistance.ID, HunterResistance::new);
    registerAdaptation(HunterDropToInventory.ID, HunterDropToInventory::new);
    registerAdaptation(HunterTrophySkinner.ID, HunterTrophySkinner::new);
    registerAdaptation(HunterPredatorFocus.ID, HunterPredatorFocus::new);
    registerAdaptation(HunterBigGameHunter.ID, HunterBigGameHunter::new);
    registerAdaptation(HunterBloodTrail.ID, HunterBloodTrail::new);
    registerAdaptation(HunterSnareLine.ID, HunterSnareLine::new);
    registerMutationKit(MutationKit.builder()
        .words(Text.key("adapt.mutated.noun.hunter"), Text.key("adapt.mutated.adjective.hunter"))
        .trigger(TriggerKind.FIGHT, HunterTrigger::new)
        .strikes(HunterFightStrike::new, HunterWorkStrike::new)
        .boon(HunterBoon::new)
        .fuel(HunterFuel::new)
        .state(HunterState::new)
        .upgrade(BloodFrenzy.ID, BloodFrenzy::new,
            List.of(HunterAdrenaline.ID, HunterStrength.ID, HunterSpeed.ID, HunterRegen.ID), Set.of())
        .toolUpgrade(ApexPredator.ID, ApexPredator::new, List.of(HunterPredatorFocus.ID, HunterBigGameHunter.ID), Set.of())
        .upgrade(StalkersTrail.ID, StalkersTrail::new,
            List.of(HunterBloodTrail.ID, HunterSnareLine.ID, HunterTrophySkinner.ID), Set.of())
        .sounds(SoundPalette.of(Id.minecraft("entity.fox.bite"), Id.minecraft("entity.rabbit.hurt"),
            Id.minecraft("entity.goat.ram_impact"), Id.minecraft("block.note_block.basedrum")))
        .tint(Rgb.of(0xe8604c))
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bone"))
        .key("challenge_novice_hunter")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("iron_sword"))
            .key("challenge_intermediate_hunter")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Id.minecraft("diamond_sword"))
                .key("challenge_advanced_hunter")
                .frame(AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("creeper_head"))
        .key("challenge_creeper_conqueror")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("tnt"))
            .key("challenge_creeper_annihilator")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bone"))
        .key("challenge_kills_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("wither_skeleton_skull"))
            .key("challenge_kills_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("dragon_head"))
        .key("challenge_boss_1")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("nether_star"))
            .key("challenge_boss_10")
            .frame(AdvancementFrame.CHALLENGE)
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
    registerGroup(Text.key("adapt.skill.hunter.group.struck"), List.of(Id.parseAdapt("adapt:hunter/adrenaline"), Id.parseAdapt("adapt:hunter/regen"), Id.parseAdapt("adapt:hunter/resistance"), Id.parseAdapt("adapt:hunter/strength"), Id.parseAdapt("adapt:hunter/speed"), Id.parseAdapt("adapt:hunter/luck"), Id.parseAdapt("adapt:hunter/jumpboost"), Id.parseAdapt("adapt:hunter/invis")));
    registerGroup(Text.key("adapt.skill.hunter.group.hunt"), List.of(Id.parseAdapt("adapt:hunter/blood_trail"), Id.parseAdapt("adapt:hunter/predator_focus"), Id.parseAdapt("adapt:hunter/big_game"), Id.parseAdapt("adapt:hunter/snare_line"), Id.parseAdapt("adapt:hunter/trophy_skinner"), Id.parseAdapt("adapt:hunter/drop_to_inventory")));
  }


  private void handleCooldownAndXp(PlayerRef p, double xpAmount, String mob) {
    if (!cooldowns.isReady(p.uuid(), getConfig().cooldownDelay)) {
      return;
    }
    cooldowns.mark(p.uuid());
    kill.award(p, xpAmount, mob);
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

    if (Minions.isMinion(victim) || CombatXp.isGraveMob(victim)) {
      return;
    }

    shouldReturnForPlayer(p, () -> {
      recordKillStats(p, victim);
      Id type = victim.type();
      if (MobOrigin.paysKillReward(p, victim)) {
        double healthMultiplier = getConfig().killMaxHealthXPMultiplier
            * (type.equals(CREEPER) ? getConfig().creeperKillMultiplier : 1D);
        double amount = killXp(victim.maxHealth(), healthMultiplier, getConfig().killFlatXp);
        handleCooldownAndXp(p, amount, type.legacyName().toLowerCase(Locale.ROOT));
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

  static double killXp(double maxHealth, double healthMultiplier, double flatXp) {
    double healthXp = Double.isFinite(maxHealth) && maxHealth > 0D ? maxHealth * Math.max(0D, healthMultiplier) : 0D;
    return healthXp + Math.max(0D, flatXp);
  }


  protected static class Config extends SkillConfig {
    @ConfigDoc(value = "Controls Get Xp For Attacking With Tools for the Hunter skill.", impact = "True enables this behavior and false disables it.")
    boolean getXpForAttackingWithTools = true;
    @ConfigDoc(value = "Controls Creeper Kill Multiplier for the Hunter skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double creeperKillMultiplier = 2;
    @ConfigDoc(value = "Controls Kill Max Health XPMultiplier for the Hunter skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double killMaxHealthXPMultiplier = 4.0;
    @ConfigDoc(value = "Flat XP added to every kill reward.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double killFlatXp = 30;
    @ConfigDoc(value = "Controls Cooldown Delay for the Hunter skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 1000;
    @ConfigDoc(value = "Controls Kills Challenge Reward for the Hunter skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double killsChallengeReward = 500;
    @ConfigDoc(value = "Controls Boss Kill Reward for the Hunter skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bossKillReward = 1000;

    public Config() {
    }
  }
}
