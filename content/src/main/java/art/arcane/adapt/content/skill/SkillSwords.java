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
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.xp.CombatXp;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.content.adaptation.sword.SwordsBladeFlow;
import art.arcane.adapt.content.adaptation.sword.SwordsBloodyBlade;
import art.arcane.adapt.content.adaptation.sword.SwordsCrescentGuard;
import art.arcane.adapt.content.adaptation.sword.SwordsCrimsonCyclone;
import art.arcane.adapt.content.adaptation.sword.SwordsDualWield;
import art.arcane.adapt.content.adaptation.sword.SwordsDuelistsFocus;
import art.arcane.adapt.content.adaptation.sword.SwordsExecutionersEdge;
import art.arcane.adapt.content.adaptation.sword.SwordsHamstring;
import art.arcane.adapt.content.adaptation.sword.SwordsHeirloomEdge;
import art.arcane.adapt.content.adaptation.sword.SwordsLungeStrike;
import art.arcane.adapt.content.adaptation.sword.SwordsMachete;
import art.arcane.adapt.content.adaptation.sword.SwordsPoisonedBlade;
import art.arcane.adapt.content.adaptation.sword.SwordsRiposteWindow;
import art.arcane.adapt.content.adaptation.sword.SwordsWhetstoneRitual;
import art.arcane.adapt.content.adaptation.sword.blocks.SwordsBoon;
import art.arcane.adapt.content.adaptation.sword.blocks.SwordsFightStrike;
import art.arcane.adapt.content.adaptation.sword.blocks.SwordsFuel;
import art.arcane.adapt.content.adaptation.sword.blocks.SwordsState;
import art.arcane.adapt.content.adaptation.sword.blocks.SwordsTrigger;
import art.arcane.adapt.content.adaptation.sword.blocks.SwordsWorkStrike;
import art.arcane.adapt.content.adaptation.sword.upgraded.DuelistsVerdict;
import art.arcane.adapt.content.adaptation.sword.upgraded.TempestFlow;
import art.arcane.adapt.content.adaptation.sword.upgraded.Witherblade;
import art.arcane.adapt.content.golem.GolemKillCredit;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;
import java.util.Set;

public class SkillSwords extends SimpleSkill<SkillSwords.Config> {
  public static final Id ID = Id.adapt("swords");
  private final XpSource combat = xpSource("combat", Text.key("adapt.xp_guide.swords.combat"));
  private final XpSource kill = xpSource("kill", Text.key("adapt.xp_guide.swords.kill"));
  private final Cooldowns cooldowns = cooldowns();


  public SkillSwords() {
    super(ID);
    registerConfiguration(Config.class);
    setColor(Rgb.of(0xFFFF55));
    setInterval(2150);
    setIcon(Id.minecraft("diamond_sword"));
    registerAdaptation(SwordsMachete.ID, SwordsMachete::new);
    registerAdaptation(SwordsPoisonedBlade.ID, SwordsPoisonedBlade::new);
    registerAdaptation(SwordsBloodyBlade.ID, SwordsBloodyBlade::new);
    registerAdaptation(SwordsDualWield.ID, SwordsDualWield::new);
    registerAdaptation(SwordsExecutionersEdge.ID, SwordsExecutionersEdge::new);
    registerAdaptation(SwordsRiposteWindow.ID, SwordsRiposteWindow::new);
    registerAdaptation(SwordsCrimsonCyclone.ID, SwordsCrimsonCyclone::new);
    registerAdaptation(SwordsLungeStrike.ID, SwordsLungeStrike::new);
    registerAdaptation(SwordsBladeFlow.ID, SwordsBladeFlow::new);
    registerAdaptation(SwordsDuelistsFocus.ID, SwordsDuelistsFocus::new);
    registerAdaptation(SwordsWhetstoneRitual.ID, SwordsWhetstoneRitual::new);
    registerAdaptation(SwordsCrescentGuard.ID, SwordsCrescentGuard::new);
    registerAdaptation(SwordsHamstring.ID, SwordsHamstring::new);
    registerAdaptation(SwordsHeirloomEdge.ID, SwordsHeirloomEdge::new);
    registerMutationKit(MutationKit.builder()
        .words(Text.key("adapt.mutated.noun.swords"), Text.key("adapt.mutated.adjective.swords"))
        .trigger(TriggerKind.FIGHT, SwordsTrigger::new)
        .strikes(SwordsFightStrike::new, SwordsWorkStrike::new)
        .boon(SwordsBoon::new)
        .fuel(SwordsFuel::new)
        .state(SwordsState::new)
        .upgrade(Witherblade.ID, Witherblade::new, List.of(SwordsPoisonedBlade.ID, SwordsBloodyBlade.ID), Set.of())
        .toolUpgrade(TempestFlow.ID, TempestFlow::new, List.of(SwordsBladeFlow.ID, SwordsCrimsonCyclone.ID), Set.of())
        .upgrade(DuelistsVerdict.ID, DuelistsVerdict::new, List.of(SwordsDuelistsFocus.ID, SwordsExecutionersEdge.ID), Set.of())
        .sounds(SoundPalette.of(Id.minecraft("entity.player.attack.sweep"), Id.minecraft("block.grass.break"),
            Id.minecraft("entity.player.attack.crit"), Id.minecraft("block.pointed_dripstone.drip_water")))
        .tint(Rgb.of(0xeef2f4))
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wooden_sword"))
        .key("challenge_sword_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("iron_sword"))
            .key("challenge_sword_1k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Id.minecraft("diamond_sword"))
                .key("challenge_sword_10k")
                .frame(AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_sword_100", "sword.hits", 100, () -> getConfig().challengeSwordReward);
    registerMilestone("challenge_sword_1k", "sword.hits", 1000, () -> getConfig().challengeSwordReward * 3);
    registerMilestone("challenge_sword_10k", "sword.hits", 10000, () -> getConfig().challengeSwordReward * 8);

    // Chain 2 - sword.damage
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("golden_sword"))
        .key("challenge_sword_dmg_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_sword"))
            .key("challenge_sword_dmg_10k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_sword_dmg_1k", "sword.damage", 1000, () -> getConfig().challengeSwordDmgReward);
    registerMilestone("challenge_sword_dmg_10k", "sword.damage", 10000, () -> getConfig().challengeSwordDmgReward * 3);

    // Chain 3 - sword.kills
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_sword"))
        .key("challenge_sword_kills_50")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_sword"))
            .key("challenge_sword_kills_500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_sword_kills_50", "sword.kills", 50, () -> getConfig().challengeSwordKillsReward);
    registerMilestone("challenge_sword_kills_500", "sword.kills", 500, () -> getConfig().challengeSwordKillsReward * 3);

    // Chain 4 - sword.critical
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("golden_sword"))
        .key("challenge_sword_crit_50")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_sword"))
            .key("challenge_sword_crit_500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_sword_crit_50", "sword.critical", 50, () -> getConfig().challengeSwordCritReward);
    registerMilestone("challenge_sword_crit_500", "sword.critical", 500, () -> getConfig().challengeSwordCritReward * 3);

    // Chain 5 - sword.heavy.hits
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("diamond_sword"))
        .key("challenge_sword_heavy_25")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_sword"))
            .key("challenge_sword_heavy_250")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_sword_heavy_25", "sword.heavy.hits", 25, () -> getConfig().challengeSwordHeavyReward);
    registerMilestone("challenge_sword_heavy_250", "sword.heavy.hits", 250, () -> getConfig().challengeSwordHeavyReward * 3);
    registerGroup(Text.key("adapt.skill.swords.group.technique"), List.of(Id.parseAdapt("adapt:sword/lunge_strike"), Id.parseAdapt("adapt:sword/riposte_window"), Id.parseAdapt("adapt:sword/blade_flow"), Id.parseAdapt("adapt:sword/duelists_focus"), Id.parseAdapt("adapt:sword/hamstring"), Id.parseAdapt("adapt:sword/executioners_edge"), Id.parseAdapt("adapt:sword/crescent_guard")));
    registerGroup(Text.key("adapt.skill.swords.group.wounds"), List.of(Id.parseAdapt("adapt:sword/bloody_blade"), Id.parseAdapt("adapt:sword/poison_blade"), Id.parseAdapt("adapt:sword/crimson_cyclone")));
    registerGroup(Text.key("adapt.skill.swords.group.blades"), List.of(Id.parseAdapt("adapt:sword/machete"), Id.parseAdapt("adapt:sword/whetstone_ritual"), Id.parseAdapt("adapt:sword/dual_wield"), Id.parseAdapt("adapt:sword/heirloom_edge")));
  }


  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof PlayerRef p)
        || !checkValidEntity(e.entity())
        || !ItemKinds.isSword(p.inventory().mainHand())) {
      return;
    }

    shouldReturnForPlayer(p, e, () -> {
      if (isOnCooldown(p)) {
        return;
      }
      double creditedDamage = CombatXp.credit(getId().toString(), p, e);
      if (creditedDamage <= 0D) {
        return;
      }
      addStat(p, "sword.hits", 1);
      addStat(p, "sword.damage", creditedDamage);
      boolean crit = p.fallDistance() > 0 && !p.isOnGround();
      boolean heavy = creditedDamage > 8;
      if (crit) {
        addStat(p, "sword.critical", 1);
      }
      if (heavy) {
        addStat(p, "sword.heavy.hits", 1);
      }
      setCooldown(p);
      combat.award(p, e.entity().location(), getConfig().damageXPMultiplier * creditedDamage);
      FxEmitter pulse = fx(e.entity().location().add(0, 1, 0), FxPriority.AMBIENT)
          .particle(Particles.CRIT, heavy ? 8 : (crit ? 5 : 3), 0, 0, 0, 0.25D, 0.05D)
          .sound(Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.2F, 1.4F);
      if (heavy) {
        pulse.sound(Sounds.ENTITY_PLAYER_ATTACK_STRONG, 0.35F, 0.9F);
      }
    });
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDeathEvent e) {
    if (e.killer() == null || GolemKillCredit.isGolemKill(e.entity())) {
      return;
    }
    PlayerRef p = e.killer();
    shouldReturnForPlayer(p, () -> {
      ItemRef hand = p.inventory().mainHand();
      if (ItemKinds.isSword(hand)) {
        addStat(p, "sword.kills", 1);
        if (!e.platformCancelled() && checkValidEntity(e.entity()) && MobOrigin.paysKillReward(p, e.entity())) {
          kill.award(p, e.entity().location(), getConfig().killXp);
        }
      }
    });
  }

  private boolean isOnCooldown(PlayerRef p) {
    return !cooldowns.isReady(p.uuid(), getConfig().cooldownDelay);
  }

  private void setCooldown(PlayerRef p) {
    cooldowns.mark(p.uuid());
  }



  protected static class Config extends SkillConfig {
    @ConfigDoc(value = "Controls Cooldown Delay for the Swords skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 1250;
    @ConfigDoc(value = "Controls Damage XPMultiplier for the Swords skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageXPMultiplier = 6;
    @ConfigDoc(value = "XP for killing a mob with a sword.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double killXp = 40;
    @ConfigDoc(value = "Controls Challenge Sword Reward for the Swords skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSwordReward = 500;
    @ConfigDoc(value = "Controls Challenge Sword Damage Reward for the Swords skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSwordDmgReward = 500;
    @ConfigDoc(value = "Controls Challenge Sword Kills Reward for the Swords skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSwordKillsReward = 500;
    @ConfigDoc(value = "Controls Challenge Sword Critical Reward for the Swords skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSwordCritReward = 500;
    @ConfigDoc(value = "Controls Challenge Sword Heavy Hits Reward for the Swords skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSwordHeavyReward = 500;

    public Config() {
    }
  }
}
