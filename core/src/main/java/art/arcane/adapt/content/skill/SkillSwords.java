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
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.golem.GolemKillCredit;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.SkillConfig;
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
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.SkillMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import lombok.NoArgsConstructor;

public class SkillSwords extends SimpleSkill<SkillSwords.Config> {
  private final Cooldowns cooldowns = cooldowns();

  public SkillSwords() {
    super("swords", SkillPresentation.of(SkillMessages.SWORDS_NAME, SkillMessages.SWORDS_ICON, SkillMessages.SWORDS_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.YELLOW);
    setInterval(2150);
    setIcon(Id.minecraft("diamond_sword"));
    registerAdaptation(new SwordsMachete());
    registerAdaptation(new SwordsPoisonedBlade());
    registerAdaptation(new SwordsBloodyBlade());
    registerAdaptation(new SwordsDualWield());
    registerAdaptation(new SwordsExecutionersEdge());
    registerAdaptation(new SwordsRiposteWindow());
    registerAdaptation(new SwordsCrimsonCyclone());
    registerAdaptation(new SwordsLungeStrike());
    registerAdaptation(new SwordsBladeFlow());
    registerAdaptation(new SwordsDuelistsFocus());
    registerAdaptation(new SwordsWhetstoneRitual());
    registerAdaptation(new SwordsCrescentGuard());
    registerAdaptation(new SwordsHamstring());
    registerAdaptation(new SwordsHeirloomEdge());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wooden_sword"))
        .key("challenge_sword_100")
        .model(CustomModel.get(Id.minecraft("wooden_sword"), "advancement", "swords", "challenge_sword_100"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("iron_sword"))
            .key("challenge_sword_1k")
            .model(CustomModel.get(Id.minecraft("iron_sword"), "advancement", "swords", "challenge_sword_1k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Id.minecraft("diamond_sword"))
                .key("challenge_sword_10k")
                .model(CustomModel.get(Id.minecraft("diamond_sword"), "advancement", "swords", "challenge_sword_10k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
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
        .model(CustomModel.get(Id.minecraft("golden_sword"), "advancement", "swords", "challenge_sword_dmg_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_sword"))
            .key("challenge_sword_dmg_10k")
            .model(CustomModel.get(Id.minecraft("netherite_sword"), "advancement", "swords", "challenge_sword_dmg_10k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_sword_dmg_1k", "sword.damage", 1000, () -> getConfig().challengeSwordDmgReward);
    registerMilestone("challenge_sword_dmg_10k", "sword.damage", 10000, () -> getConfig().challengeSwordDmgReward * 3);

    // Chain 3 - sword.kills
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_sword"))
        .key("challenge_sword_kills_50")
        .model(CustomModel.get(Id.minecraft("iron_sword"), "advancement", "swords", "challenge_sword_kills_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_sword"))
            .key("challenge_sword_kills_500")
            .model(CustomModel.get(Id.minecraft("diamond_sword"), "advancement", "swords", "challenge_sword_kills_500"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_sword_kills_50", "sword.kills", 50, () -> getConfig().challengeSwordKillsReward);
    registerMilestone("challenge_sword_kills_500", "sword.kills", 500, () -> getConfig().challengeSwordKillsReward * 3);

    // Chain 4 - sword.critical
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("golden_sword"))
        .key("challenge_sword_crit_50")
        .model(CustomModel.get(Id.minecraft("golden_sword"), "advancement", "swords", "challenge_sword_crit_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_sword"))
            .key("challenge_sword_crit_500")
            .model(CustomModel.get(Id.minecraft("netherite_sword"), "advancement", "swords", "challenge_sword_crit_500"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_sword_crit_50", "sword.critical", 50, () -> getConfig().challengeSwordCritReward);
    registerMilestone("challenge_sword_crit_500", "sword.critical", 500, () -> getConfig().challengeSwordCritReward * 3);

    // Chain 5 - sword.heavy.hits
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("diamond_sword"))
        .key("challenge_sword_heavy_25")
        .model(CustomModel.get(Id.minecraft("diamond_sword"), "advancement", "swords", "challenge_sword_heavy_25"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_sword"))
            .key("challenge_sword_heavy_250")
            .model(CustomModel.get(Id.minecraft("netherite_sword"), "advancement", "swords", "challenge_sword_heavy_250"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_sword_heavy_25", "sword.heavy.hits", 25, () -> getConfig().challengeSwordHeavyReward);
    registerMilestone("challenge_sword_heavy_250", "sword.heavy.hits", 250, () -> getConfig().challengeSwordHeavyReward * 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof PlayerRef p)
        || !checkValidEntity(e.entity())
        || !isSword(p.inventory().mainHand())) {
      return;
    }

    shouldReturnForPlayer(p, e, () -> {
      if (isOnCooldown(p)) {
        return;
      }
      double creditedDamage = CombatXp.credit(getPlayer(p).getData().getCombatXp(), getName(), p, e);
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
      xp(p, e.entity().location(), getConfig().damageXPMultiplier * creditedDamage, "swords:combat");
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
      if (isSword(hand)) {
        addStat(p, "sword.kills", 1);
        if (!e.platformCancelled() && checkValidEntity(e.entity()) && MobOrigin.paysKillReward(p, e.entity())) {
          xp(p, e.entity().location(), getConfig().killXp, "swords:kill");
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


  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @NoArgsConstructor
  protected static class Config extends SkillConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    String skillColor = "&e";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Swords skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 1250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage XPMultiplier for the Swords skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageXPMultiplier = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP for killing a mob with a sword.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double killXp = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Sword Reward for the Swords skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSwordReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Sword Damage Reward for the Swords skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSwordDmgReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Sword Kills Reward for the Swords skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSwordKillsReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Sword Critical Reward for the Swords skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSwordCritReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Sword Heavy Hits Reward for the Swords skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSwordHeavyReward = 500;
  }
}
