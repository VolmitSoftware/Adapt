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
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.player.SkillProgress;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.xp.CombatXp;
import art.arcane.adapt.content.adaptation.tragoul.TragoulBloodPact;
import art.arcane.adapt.content.adaptation.tragoul.TragoulBoneHarvest;
import art.arcane.adapt.content.adaptation.tragoul.TragoulCorpseExplosion;
import art.arcane.adapt.content.adaptation.tragoul.TragoulCurseOfFrailty;
import art.arcane.adapt.content.adaptation.tragoul.TragoulDeathSense;
import art.arcane.adapt.content.adaptation.tragoul.TragoulGlobe;
import art.arcane.adapt.content.adaptation.tragoul.TragoulHealing;
import art.arcane.adapt.content.adaptation.tragoul.TragoulLance;
import art.arcane.adapt.content.adaptation.tragoul.TragoulLastRites;
import art.arcane.adapt.content.adaptation.tragoul.TragoulMarrowArmor;
import art.arcane.adapt.content.adaptation.tragoul.TragoulPlagueBearer;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSoulSiphon;
import art.arcane.adapt.content.adaptation.tragoul.TragoulThorns;
import art.arcane.adapt.content.adaptation.tragoul.blocks.TragOulBoon;
import art.arcane.adapt.content.adaptation.tragoul.blocks.TragOulFightStrike;
import art.arcane.adapt.content.adaptation.tragoul.blocks.TragOulFuel;
import art.arcane.adapt.content.adaptation.tragoul.blocks.TragOulState;
import art.arcane.adapt.content.adaptation.tragoul.blocks.TragOulTrigger;
import art.arcane.adapt.content.adaptation.tragoul.blocks.TragOulWorkStrike;
import art.arcane.adapt.content.adaptation.tragoul.upgraded.CrimsonTide;
import art.arcane.adapt.content.adaptation.tragoul.upgraded.Deathless;
import art.arcane.adapt.content.adaptation.tragoul.upgraded.PlagueNova;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.fx.ParticleData;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;
import java.util.Set;

public class SkillTragOul extends SimpleSkill<SkillTragOul.Config> {
  public static final Id ID = Id.adapt("tragoul");
  private final XpSource damageReceived = xpSource("damage-received", Text.key("adapt.xp_guide.tragoul.damage"));
  private final XpSource lowHealth = xpSource("low-health", Text.key("adapt.xp_guide.tragoul.low_health"));
  private final Cooldowns cooldowns = cooldowns();


  public SkillTragOul() {
    super(ID);
    registerConfiguration(Config.class);
    setColor(Rgb.of(0x55FFFF));
    setInterval(2755);
    setIcon(Id.minecraft("crimson_roots"));
    registerAdaptation(TragoulThorns.ID, TragoulThorns::new);
    registerAdaptation(TragoulGlobe.ID, TragoulGlobe::new);
    registerAdaptation(TragoulHealing.ID, TragoulHealing::new);
    registerAdaptation(TragoulLance.ID, TragoulLance::new);
    registerAdaptation(TragoulBloodPact.ID, TragoulBloodPact::new);
    registerAdaptation(TragoulBoneHarvest.ID, TragoulBoneHarvest::new);
    registerAdaptation(TragoulCorpseExplosion.ID, TragoulCorpseExplosion::new);
    registerAdaptation(TragoulSoulSiphon.ID, TragoulSoulSiphon::new);
    registerAdaptation(TragoulSkeletalServant.ID, TragoulSkeletalServant::new);
    registerAdaptation(TragoulMarrowArmor.ID, TragoulMarrowArmor::new);
    registerAdaptation(TragoulCurseOfFrailty.ID, TragoulCurseOfFrailty::new);
    registerAdaptation(TragoulDeathSense.ID, TragoulDeathSense::new);
    registerAdaptation(TragoulPlagueBearer.ID, TragoulPlagueBearer::new);
    registerAdaptation(TragoulLastRites.ID, TragoulLastRites::new);
    registerMutationKit(MutationKit.builder()
        .words(Text.key("adapt.mutated.noun.tragoul"), Text.key("adapt.mutated.adjective.tragoul"))
        .trigger(TriggerKind.FIGHT, TragOulTrigger::new)
        .strikes(TragOulFightStrike::new, TragOulWorkStrike::new)
        .boon(TragOulBoon::new)
        .fuel(TragOulFuel::new)
        .state(TragOulState::new)
        .upgrade(CrimsonTide.ID, CrimsonTide::new, List.of(TragoulSoulSiphon.ID, TragoulHealing.ID, TragoulThorns.ID), Set.of())
        .upgrade(PlagueNova.ID, PlagueNova::new,
            List.of(TragoulCorpseExplosion.ID, TragoulPlagueBearer.ID, TragoulLance.ID), Set.of())
        .upgrade(Deathless.ID, Deathless::new, List.of(TragoulLastRites.ID, TragoulMarrowArmor.ID), Set.of())
        .sounds(SoundPalette.of(Id.minecraft("entity.vex.hurt"), Id.minecraft("block.sculk.spread"),
            Id.minecraft("block.sculk.charge"), Id.minecraft("block.pointed_dripstone.drip_lava")))
        .tint(Rgb.of(0xc8282c))
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("crimson_roots"))
        .key("challenge_trag_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("crimson_stem"))
            .key("challenge_trag_10k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Id.minecraft("nether_star"))
                .key("challenge_trag_100k")
                .frame(AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_trag_1k", "trag.damage", 1000, () -> getConfig().challengeTragReward);
    registerMilestone("challenge_trag_10k", "trag.damage", 10000, () -> getConfig().challengeTragReward * 3);
    registerMilestone("challenge_trag_100k", "trag.damage", 100000, () -> getConfig().challengeTragReward * 8);

    // Chain 2 - Hits Received
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("rotten_flesh"))
        .key("challenge_trag_hits_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("bone"))
            .key("challenge_trag_hits_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_trag_hits_500", "trag.hitsrecieved", 500, () -> getConfig().challengeTragReward);
    registerMilestone("challenge_trag_hits_5k", "trag.hitsrecieved", 5000, () -> getConfig().challengeTragReward * 3);
    registerGroup(Text.key("adapt.skill.tragoul.group.pain"), List.of(Id.parseAdapt("adapt:tragoul/thorns"), Id.parseAdapt("adapt:tragoul/curse_of_frailty"), Id.parseAdapt("adapt:tragoul/blood_pact"), Id.parseAdapt("adapt:tragoul/marrow_armor"), Id.parseAdapt("adapt:tragoul/healing"), Id.parseAdapt("adapt:tragoul/last_rites")));
    registerGroup(Text.key("adapt.skill.tragoul.group.corpses"), List.of(Id.parseAdapt("adapt:tragoul/corpse_explosion"), Id.parseAdapt("adapt:tragoul/lance"), Id.parseAdapt("adapt:tragoul/bone_harvest"), Id.parseAdapt("adapt:tragoul/plague_bearer"), Id.parseAdapt("adapt:tragoul/skeletal_servant")));
    registerGroup(Text.key("adapt.skill.tragoul.group.blood"), List.of(Id.parseAdapt("adapt:tragoul/death_sense"), Id.parseAdapt("adapt:tragoul/globe"), Id.parseAdapt("adapt:tragoul/soul_siphon")));
  }


  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> {
      if (e.entity().isDead()
          || e.entity().isInvulnerable()
          || p.isInvulnerable()
          || p.isBlocking()
          || !checkValidEntity(e.entity())) {
        return;
      }
      if (!cooldowns.isReady(p.uuid(), getConfig().cooldownDelay)) {
        return;
      }
      double damage = CombatXp.creditDefense(getId().toString(), p, e,
          CombatXp.effectiveDamage(e.finalDamage(), p.health()));
      if (damage <= 0D) {
        return;
      }
      PlayerProfile a = getPlayer(p);
      a.addStat("trag.hitsrecieved", 1);
      a.addStat("trag.damage", damage);
      cooldowns.mark(p.uuid());
      damageReceived.award(a.player(), getConfig().damageReceivedXpMultiplier * damage);
      if (getConfig().showParticles) {
        fx(p.location().add(0, 1.0, 0), FxPriority.AMBIENT).particle(Particles.SOUL, 2, 0, 0, 0, 0.2, 0.03);
      }
      if (p.health() - e.finalDamage() > 0 && p.health() - e.finalDamage() <= 8) {
        lowHealth.award(a.player(), getConfig().lowHealthSurvivalXP);
        playSecondWind(p);
      }
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    PlayerRef p = e.player();
    shouldReturnForPlayer(p, () -> {
      PlayerProfile a = getPlayer(p);
      if (AdaptApi.settings().hardcoreResetOnDeath()) {
        AdaptApi.log().verbose("Resetting " + p.name() + "'s skills after death.");
        a.erase();
        return;
      }
      if (getConfig().takeAwaySkillsOnDeath) {
        playDeathDrain(p);

        if (!this.hasUsePermission(p)) {
          return;
        }

        SkillProgress tragoul = a.skill(ID);
        tragoul.loseXp(deathXpLossFor(tragoul.xp(), getConfig().deathXpLoss));
        tragoul.lowerAdaptations(1);
      }
    });
  }

  static double deathXpLossFor(double currentXp, double configuredLoss) {
    return Math.min(Math.max(currentXp, 0D), Math.abs(configuredLoss));
  }

  private void playSecondWind(PlayerRef p) {
    ParticleData.DustTransition trans = new ParticleData.DustTransition(new Rgb(120, 0, 0), new Rgb(0, 190, 200), 1.2F);
    timeline(p)
        .duration(8)
        .priority(FxPriority.COMBAT)
        .cullRadius(32)
        .frame((fx, tick, progress) -> {
          if (getConfig().showParticles) {
            fx.ring(Particles.DUST_COLOR_TRANSITION, 1.4 - (1.0 * progress), 12, 1.0, trans);
          }
          if (tick == 0) {
            if (getConfig().showParticles) {
              fx.particle(Particles.DAMAGE_INDICATOR, 2, 0, 0.5, 0, 0.2, 0.02);
            }
            fx.chord(Sounds.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5F, 0.8F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.6F);
          }
        })
        .start();
  }

  private void playDeathDrain(PlayerRef p) {
    timeline(p)
        .duration(20)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          double radius = 2.5 * (1.0 - progress);
          if (getConfig().showParticles) {
            fx.dome(Particles.ASH, radius, 15);
            fx.particle(Particles.SCULK_SOUL, 2, 0, 0.3, 0, radius * 0.5, 0.02);
          }
          if (tick == 0) {
            fx.sound(Sounds.ENTITY_BLAZE_DEATH, 1.0F, 1.0F);
          } else if (tick == 10) {
            fx.sound(Sounds.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.7F, 0.7F);
          }
        })
        .start();
  }


  protected static class Config extends SkillConfig {
    @ConfigDoc(value = "Amount of tragoul XP removed on death, clamped so XP never drops below zero.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double deathXpLoss = 250;
    @ConfigDoc(value = "Controls Take Away Skills On Death for the Trag Oul skill.", impact = "True enables this behavior and false disables it.")
    boolean takeAwaySkillsOnDeath = false;
    @ConfigDoc(value = "Controls Show Particles for the Trag Oul skill.", impact = "True enables this behavior and false disables it.")
    boolean showParticles = true;
    @ConfigDoc(value = "Controls Cooldown Delay for the Trag Oul skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 450;
    @ConfigDoc(value = "Controls Damage Received Xp Multiplier for the Trag Oul skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageReceivedXpMultiplier = 14.4;
    @ConfigDoc(value = "Controls XP bonus for surviving a hit below 4 hearts.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double lowHealthSurvivalXP = 84;
    @ConfigDoc(value = "Controls Challenge Trag Reward for the Trag Oul skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeTragReward = 500;

    public Config() {
    }
  }
}
