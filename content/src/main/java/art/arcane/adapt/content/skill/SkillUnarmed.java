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
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.BarterGesture;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.xp.CombatXp;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedBatteringCharge;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedComboChain;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedDisarm;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedGlassCannon;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedGrapple;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedIronFists;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedMeditation;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedPower;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedPressurePoint;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedSecondWind;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedShockwaveClap;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedSuckerPunch;
import art.arcane.adapt.content.adaptation.unarmed.blocks.UnarmedBoon;
import art.arcane.adapt.content.adaptation.unarmed.blocks.UnarmedFightStrike;
import art.arcane.adapt.content.adaptation.unarmed.blocks.UnarmedFuel;
import art.arcane.adapt.content.adaptation.unarmed.blocks.UnarmedState;
import art.arcane.adapt.content.adaptation.unarmed.blocks.UnarmedTrigger;
import art.arcane.adapt.content.adaptation.unarmed.blocks.UnarmedWorkStrike;
import art.arcane.adapt.content.adaptation.unarmed.upgraded.SeismicThrow;
import art.arcane.adapt.content.adaptation.unarmed.upgraded.TitanFists;
import art.arcane.adapt.content.adaptation.unarmed.upgraded.UnbrokenCombo;
import art.arcane.adapt.content.golem.GolemKillCredit;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
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

public class SkillUnarmed extends SimpleSkill<SkillUnarmed.Config> {
  public static final Id ID = Id.adapt("unarmed");
  private final XpSource combat = xpSource("combat", Text.key("adapt.xp_guide.unarmed.combat"));
  private final XpSource kill = xpSource("kill", Text.key("adapt.xp_guide.unarmed.kill"));
  private final Cooldowns cooldowns = cooldowns();

  public SkillUnarmed() {
    super(ID);
    xpNote(Text.key("adapt.xp_guide.farmed_mobs"));
    registerConfiguration(Config.class);
    setColor(Rgb.of(0xFFFF55));
    setInterval(2579);
    registerAdaptation(UnarmedSuckerPunch.ID, UnarmedSuckerPunch::new);
    registerAdaptation(UnarmedPower.ID, UnarmedPower::new);
    registerAdaptation(UnarmedGlassCannon.ID, UnarmedGlassCannon::new);
    registerAdaptation(UnarmedBatteringCharge.ID, UnarmedBatteringCharge::new);
    registerAdaptation(UnarmedComboChain.ID, UnarmedComboChain::new);
    registerAdaptation(UnarmedDisarm.ID, UnarmedDisarm::new);
    registerAdaptation(UnarmedPressurePoint.ID, UnarmedPressurePoint::new);
    registerAdaptation(UnarmedShockwaveClap.ID, UnarmedShockwaveClap::new);
    registerAdaptation(UnarmedIronFists.ID, UnarmedIronFists::new);
    registerAdaptation(UnarmedGrapple.ID, UnarmedGrapple::new);
    registerAdaptation(UnarmedSecondWind.ID, UnarmedSecondWind::new);
    registerAdaptation(UnarmedMeditation.ID, UnarmedMeditation::new);
    registerMutationKit(MutationKit.builder()
        .words(Text.key("adapt.mutated.noun.unarmed"), Text.key("adapt.mutated.adjective.unarmed"))
        .trigger(TriggerKind.FIGHT, UnarmedTrigger::new)
        .strikes(UnarmedFightStrike::new, UnarmedWorkStrike::new)
        .boon(UnarmedBoon::new)
        .fuel(UnarmedFuel::new)
        .state(UnarmedState::new)
        .upgrade(TitanFists.ID, TitanFists::new,
            List.of(UnarmedPower.ID, UnarmedIronFists.ID, UnarmedGlassCannon.ID, UnarmedSuckerPunch.ID, UnarmedBatteringCharge.ID), Set.of())
        .upgrade(UnbrokenCombo.ID, UnbrokenCombo::new,
            List.of(UnarmedComboChain.ID, UnarmedPressurePoint.ID, UnarmedDisarm.ID), Set.of())
        .upgrade(SeismicThrow.ID, SeismicThrow::new, List.of(UnarmedGrapple.ID, UnarmedShockwaveClap.ID), Set.of())
        .sounds(SoundPalette.of(Id.minecraft("entity.player.attack.strong"), Id.minecraft("block.wool.break"),
            Id.minecraft("item.armor.equip_generic"), Id.minecraft("block.wool.hit")))
        .tint(Rgb.of(0xf0b898))
        // Shockwave Clap.
        .reserve(BarterGesture.SNEAK_LEFT_CLICK)
        .build());
    setIcon(Id.minecraft("fire_charge"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("fire_charge"))
        .key("challenge_unarmed_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("blaze_powder"))
            .key("challenge_unarmed_1k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Id.minecraft("nether_star"))
                .key("challenge_unarmed_10k")
                .frame(AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_unarmed_100", "unarmed.hits", 100, () -> getConfig().challengeUnarmedReward);
    registerMilestone("challenge_unarmed_1k", "unarmed.hits", 1000, () -> getConfig().challengeUnarmedReward * 3);
    registerMilestone("challenge_unarmed_10k", "unarmed.hits", 10000, () -> getConfig().challengeUnarmedReward * 8);

    // Chain 2 - Unarmed Damage
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("fire_charge"))
        .key("challenge_unarmed_dmg_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("blaze_rod"))
            .key("challenge_unarmed_dmg_10k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_unarmed_dmg_1k", "unarmed.damage", 1000, () -> getConfig().challengeUnarmedDmgReward);
    registerMilestone("challenge_unarmed_dmg_10k", "unarmed.damage", 10000, () -> getConfig().challengeUnarmedDmgReward * 3);

    // Chain 3 - Unarmed Kills
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("rotten_flesh"))
        .key("challenge_unarmed_kills_25")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("zombie_head"))
            .key("challenge_unarmed_kills_250")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_unarmed_kills_25", "unarmed.kills", 25, () -> getConfig().challengeUnarmedKillsReward);
    registerMilestone("challenge_unarmed_kills_250", "unarmed.kills", 250, () -> getConfig().challengeUnarmedKillsReward * 3);

    // Chain 4 - Unarmed Criticals
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("sugar"))
        .key("challenge_unarmed_crit_25")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("fermented_spider_eye"))
            .key("challenge_unarmed_crit_250")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_unarmed_crit_25", "unarmed.critical", 25, () -> getConfig().challengeUnarmedCritReward);
    registerMilestone("challenge_unarmed_crit_250", "unarmed.critical", 250, () -> getConfig().challengeUnarmedCritReward * 3);

    // Chain 5 - Unarmed Heavy Hits
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("tnt"))
        .key("challenge_unarmed_heavy_25")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("end_crystal"))
            .key("challenge_unarmed_heavy_250")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_unarmed_heavy_25", "unarmed.heavy", 25, () -> getConfig().challengeUnarmedHeavyReward);
    registerMilestone("challenge_unarmed_heavy_250", "unarmed.heavy", 250, () -> getConfig().challengeUnarmedHeavyReward * 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof PlayerRef p)
        || !checkValidEntity(e.entity())
        || ItemKinds.isMelee(p.inventory().mainHand())) {
      return;
    }

    shouldReturnForPlayer(p, e, () -> {
      if (p.isInvulnerable()) {
        return;
      }

      if (!cooldowns.isReady(p.uuid(), getConfig().cooldownDelay)) {
        return;
      }
      double creditedDamage = CombatXp.credit(getId().toString(), p, e);
      if (creditedDamage <= 0D) {
        return;
      }
      PlayerProfile a = getPlayer(p);
      a.addStat("unarmed.hits", 1);
      a.addStat("unarmed.damage", creditedDamage);
      if (p.fallDistance() > 0 && !p.isOnGround()) {
        a.addStat("unarmed.critical", 1);
      }
      if (creditedDamage > 6) {
        a.addStat("unarmed.heavy", 1);
      }
      cooldowns.mark(p.uuid());
      combat.award(p, e.entity().location(), getConfig().damageXPMultiplier * creditedDamage);
    });
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDeathEvent e) {
    LivingRef victim = e.entity();
    if (e.killer() == null || GolemKillCredit.isGolemKill(victim)) {
      return;
    }
    PlayerRef p = e.killer();

    shouldReturnForPlayer(p, () -> {
      PlayerProfile a = getPlayer(p);
      ItemRef hand = a.player().inventory().mainHand();

      if (!ItemKinds.isMelee(hand)) {
        a.addStat("unarmed.kills", 1);
        if (!e.platformCancelled() && checkValidEntity(victim) && MobOrigin.paysKillReward(p, victim)) {
          kill.award(p, victim.location(), getConfig().killXp);
        }
        if (victim.has(EntityTrait.BOSS)) {
          fx(victim.location().add(0, 1.2D, 0), FxPriority.TRANSITION)
              .burst(Particles.TOTEM, 16, 0.5D)
              .dustHelix(1.0D, 2.4D, 12, 0D, 1.0F)
              .chord(Sounds.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.1F, Sounds.ENTITY_PLAYER_LEVELUP, 0.5F, 1.4F);
        }
      }
    });
  }


  protected static class Config extends SkillConfig {
    @ConfigDoc(value = "XP per health point of unarmed damage dealt; 5 pays 10 XP per heart.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageXPMultiplier = 5.0;
    @ConfigDoc(value = "XP for killing a mob while the main hand holds no weapon or tool.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double killXp = 300;
    @ConfigDoc(value = "Controls Cooldown Delay for the Unarmed skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 1250;
    @ConfigDoc(value = "Controls Challenge Unarmed Reward for the Unarmed skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeUnarmedReward = 500;
    @ConfigDoc(value = "Controls Challenge Unarmed Damage Reward for the Unarmed skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeUnarmedDmgReward = 500;
    @ConfigDoc(value = "Controls Challenge Unarmed Kills Reward for the Unarmed skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeUnarmedKillsReward = 750;
    @ConfigDoc(value = "Controls Challenge Unarmed Critical Reward for the Unarmed skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeUnarmedCritReward = 750;
    @ConfigDoc(value = "Controls Challenge Unarmed Heavy Reward for the Unarmed skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeUnarmedHeavyReward = 750;

    public Config() {
    }
  }
}
