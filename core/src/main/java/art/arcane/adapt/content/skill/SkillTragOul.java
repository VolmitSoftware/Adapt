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

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationRequirements;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerSkillLine;
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
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.SkillMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.fx.ParticleData;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SkillTragOul extends SimpleSkill<SkillTragOul.Config> {
  private final Cooldowns cooldowns = cooldowns();

  public SkillTragOul() {
    super("tragoul", SkillPresentation.of(SkillMessages.TRAGOUL_NAME, SkillMessages.TRAGOUL_ICON, SkillMessages.TRAGOUL_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.AQUA);
    setInterval(2755);
    setIcon(Id.minecraft("crimson_roots"));
    registerAdaptation(new TragoulThorns());
    registerAdaptation(new TragoulGlobe());
    registerAdaptation(new TragoulHealing());
    registerAdaptation(new TragoulLance());
    registerAdaptation(new TragoulBloodPact());
    registerAdaptation(new TragoulBoneHarvest());
    registerAdaptation(new TragoulCorpseExplosion());
    registerAdaptation(new TragoulSoulSiphon());
    registerAdaptation(new TragoulSkeletalServant());
    registerAdaptation(new TragoulMarrowArmor());
    registerAdaptation(new TragoulCurseOfFrailty());
    registerAdaptation(new TragoulDeathSense());
    registerAdaptation(new TragoulPlagueBearer());
    registerAdaptation(new TragoulLastRites());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("crimson_roots"))
        .key("challenge_trag_1k")
        .model(CustomModel.get(Id.minecraft("crimson_roots"), "advancement", "tragoul", "challenge_trag_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("crimson_stem"))
            .key("challenge_trag_10k")
            .model(CustomModel.get(Id.minecraft("crimson_stem"), "advancement", "tragoul", "challenge_trag_10k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Id.minecraft("nether_star"))
                .key("challenge_trag_100k")
                .model(CustomModel.get(Id.minecraft("nether_star"), "advancement", "tragoul", "challenge_trag_100k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
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
        .model(CustomModel.get(Id.minecraft("rotten_flesh"), "advancement", "tragoul", "challenge_trag_hits_500"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("bone"))
            .key("challenge_trag_hits_5k")
            .model(CustomModel.get(Id.minecraft("bone"), "advancement", "tragoul", "challenge_trag_hits_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_trag_hits_500", "trag.hitsrecieved", 500, () -> getConfig().challengeTragReward);
    registerMilestone("challenge_trag_hits_5k", "trag.hitsrecieved", 5000, () -> getConfig().challengeTragReward * 3);

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
      double damage = CombatXp.creditDefense(getPlayer(p).getData().getCombatXp(), getName(), p, e,
          CombatXp.effectiveDamage(e.finalDamage(), p.health()));
      if (damage <= 0D) {
        return;
      }
      AdaptPlayer a = getPlayer(p);
      a.getData().addStat("trag.hitsrecieved", 1);
      a.getData().addStat("trag.damage", damage);
      cooldowns.mark(p.uuid());
      xp(a.getPlayer(), getConfig().damageReceivedXpMultiplier * damage, "tragoul:damage-received");
      if (getConfig().showParticles) {
        fx(p.location().add(0, 1.0, 0), FxPriority.AMBIENT).particle(Particles.SOUL, 2, 0, 0, 0, 0.2, 0.03);
      }
      if (p.health() - e.finalDamage() > 0 && p.health() - e.finalDamage() <= 8) {
        xp(a.getPlayer(), getConfig().lowHealthSurvivalXP);
        playSecondWind(p);
      }
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    PlayerRef p = e.player();
    shouldReturnForPlayer(p, () -> {
      AdaptPlayer a = getPlayer(p);
      if (AdaptConfig.get().isHardcoreResetOnPlayerDeath()) {
        AdaptLog.verbose(() -> "Resetting " + p.name() + "'s skills after death.");
        AdaptCore.current().getAdaptServer().resetPlayerData(p.uuid());
        return;
      }
      if (getConfig().takeAwaySkillsOnDeath) {
        playDeathDrain(p);

        if (!this.hasUsePermission(p, this)) {
          return;
        }

        PlayerSkillLine tragoul = a.getData().getSkillLineNullable("tragoul");
        if (tragoul != null) {
          double xp = tragoul.getXp();
          double loss = deathXpLossFor(xp, getConfig().deathXpLoss);
          if (loss > 0) {
            tragoul.setXp(xp - loss);
          }
          tragoul.setLastXP(xp);

          List<AdaptationRequirements.Lowering> drained = new ArrayList<>();
          for (Map.Entry<String, PlayerAdaptation> entry : tragoul.getAdaptations().entrySet()) {
            PlayerAdaptation adapt = entry.getValue();
            Adaptation<?> adaptation = AdaptationRequirements.find(entry.getKey());
            if (adaptation != null && !adapt.isRegionGranted() && adapt.getLevel() > 0) {
              drained.add(new AdaptationRequirements.Lowering(adaptation, adapt.getLevel()));
            }
            adapt.setLevel(Math.max(adapt.getLevel() - 1, 0));
          }
          AdaptationRequirements.forfeitLevels(drained, a.getData(), p.name());

          recalcTotalExp(p);
          a.getData().pruneAdaptationsForPowerBudget();
        }
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

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @NoArgsConstructor
  protected static class Config extends SkillConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Amount of tragoul XP removed on death, clamped so XP never drops below zero.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double deathXpLoss = 250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Take Away Skills On Death for the Trag Oul skill.", impact = "True enables this behavior and false disables it.")
    boolean takeAwaySkillsOnDeath = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    String skillColor = "&b";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Trag Oul skill.", impact = "True enables this behavior and false disables it.")
    boolean showParticles = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Trag Oul skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 450;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Received Xp Multiplier for the Trag Oul skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageReceivedXpMultiplier = 14.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls XP bonus for surviving a hit below 4 hearts.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double lowHealthSurvivalXP = 84;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Trag Reward for the Trag Oul skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeTragReward = 500;
  }
}
