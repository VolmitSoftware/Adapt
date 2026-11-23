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
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.api.xp.CombatXp;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.content.adaptation.ranged.RangedArrowRecovery;
import art.arcane.adapt.content.adaptation.ranged.RangedFetchShot;
import art.arcane.adapt.content.adaptation.ranged.RangedFloaters;
import art.arcane.adapt.content.adaptation.ranged.RangedForce;
import art.arcane.adapt.content.adaptation.ranged.RangedHeartseeker;
import art.arcane.adapt.content.adaptation.ranged.RangedHeavyDraw;
import art.arcane.adapt.content.adaptation.ranged.RangedLungeShot;
import art.arcane.adapt.content.adaptation.ranged.RangedPiercing;
import art.arcane.adapt.content.adaptation.ranged.RangedPinningShot;
import art.arcane.adapt.content.adaptation.ranged.RangedProjectiles;
import art.arcane.adapt.content.adaptation.ranged.RangedRicochetBolt;
import art.arcane.adapt.content.adaptation.ranged.RangedTrajectorySight;
import art.arcane.adapt.content.adaptation.ranged.RangedWebBomb;
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.SkillMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import lombok.NoArgsConstructor;

public class SkillRanged extends SimpleSkill<SkillRanged.Config> {
  private static final Id BOW = Id.minecraft("bow");
  private static final Id CROSSBOW = Id.minecraft("crossbow");
  private static final Id SNOWBALL = Id.minecraft("snowball");
  private static final Id FISHING_BOBBER = Id.minecraft("fishing_bobber");

  private final Cooldowns cooldowns = cooldowns();

  public SkillRanged() {
    super("ranged", SkillPresentation.of(SkillMessages.RANGED_NAME, SkillMessages.RANGED_ICON, SkillMessages.RANGED_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.DARK_GREEN);
    setInterval(3044);
    registerAdaptation(new RangedForce());
    registerAdaptation(new RangedPiercing());
    registerAdaptation(new RangedArrowRecovery());
    registerAdaptation(new RangedLungeShot());
    registerAdaptation(new RangedWebBomb());
    registerAdaptation(new RangedTrajectorySight());
    registerAdaptation(new RangedFloaters());
    registerAdaptation(new RangedPinningShot());
    registerAdaptation(new RangedRicochetBolt());
    registerAdaptation(new RangedFetchShot());
    registerAdaptation(new RangedHeavyDraw());
    registerAdaptation(new RangedHeartseeker());
    setIcon(Id.minecraft("crossbow"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("arrow"))
        .key("challenge_ranged_100")
        .model(CustomModel.get(Id.minecraft("arrow"), "advancement", "ranged", "challenge_ranged_100"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("spectral_arrow"))
            .key("challenge_ranged_1k")
            .model(CustomModel.get(Id.minecraft("spectral_arrow"), "advancement", "ranged", "challenge_ranged_1k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Id.minecraft("crossbow"))
                .key("challenge_ranged_10k")
                .model(CustomModel.get(Id.minecraft("crossbow"), "advancement", "ranged", "challenge_ranged_10k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_ranged_100", "ranged.shotsfired", 100, () -> getConfig().challengeRangedReward);
    registerMilestone("challenge_ranged_1k", "ranged.shotsfired", 1000, () -> getConfig().challengeRangedReward * 3);
    registerMilestone("challenge_ranged_10k", "ranged.shotsfired", 10000, () -> getConfig().challengeRangedReward * 8);

    // Chain 2 - Ranged Damage
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bow"))
        .key("challenge_ranged_dmg_1k")
        .model(CustomModel.get(Id.minecraft("bow"), "advancement", "ranged", "challenge_ranged_dmg_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("crossbow"))
            .key("challenge_ranged_dmg_10k")
            .model(CustomModel.get(Id.minecraft("crossbow"), "advancement", "ranged", "challenge_ranged_dmg_10k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_ranged_dmg_1k", "ranged.damage", 1000, () -> getConfig().challengeRangedDmgReward);
    registerMilestone("challenge_ranged_dmg_10k", "ranged.damage", 10000, () -> getConfig().challengeRangedDmgReward * 3);

    // Chain 3 - Ranged Distance
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("arrow"))
        .key("challenge_ranged_dist_5k")
        .model(CustomModel.get(Id.minecraft("arrow"), "advancement", "ranged", "challenge_ranged_dist_5k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("spectral_arrow"))
            .key("challenge_ranged_dist_50k")
            .model(CustomModel.get(Id.minecraft("spectral_arrow"), "advancement", "ranged", "challenge_ranged_dist_50k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_ranged_dist_5k", "ranged.distance", 5000, () -> getConfig().challengeRangedDistReward);
    registerMilestone("challenge_ranged_dist_50k", "ranged.distance", 50000, () -> getConfig().challengeRangedDistReward * 3);

    // Chain 4 - Ranged Kills
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("tipped_arrow"))
        .key("challenge_ranged_kills_50")
        .model(CustomModel.get(Id.minecraft("tipped_arrow"), "advancement", "ranged", "challenge_ranged_kills_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("target"))
            .key("challenge_ranged_kills_500")
            .model(CustomModel.get(Id.minecraft("target"), "advancement", "ranged", "challenge_ranged_kills_500"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_ranged_kills_50", "ranged.kills", 50, () -> getConfig().challengeRangedKillsReward);
    registerMilestone("challenge_ranged_kills_500", "ranged.kills", 500, () -> getConfig().challengeRangedKillsReward * 3);

    // Chain 5 - Longshots
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("spyglass"))
        .key("challenge_longshot_25")
        .model(CustomModel.get(Id.minecraft("spyglass"), "advancement", "ranged", "challenge_longshot_25"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("ender_eye"))
            .key("challenge_longshot_250")
            .model(CustomModel.get(Id.minecraft("ender_eye"), "advancement", "ranged", "challenge_longshot_250"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_longshot_25", "ranged.longshots", 25, () -> getConfig().challengeRangedLongshotReward);
    registerMilestone("challenge_longshot_250", "ranged.longshots", 250, () -> getConfig().challengeRangedLongshotReward * 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ProjectileLaunchEvent e) {
    ProjectileRef projectile = e.projectile();
    if (!RangedProjectiles.isArrowLike(projectile)
        || !(projectile.shooter() instanceof PlayerRef p)) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> {
      String projectileType = projectile.type().path();
      addStat(p, "ranged.shotsfired", 1);
      addStat(p, "ranged.shotsfired." + projectileType, 1);
      if (!cooldowns.isReady(p.uuid(), getConfig().cooldownDelay)) {
        return;
      }
      cooldowns.mark(p.uuid());
      xp(p, getConfig().shootXP);
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof ProjectileRef projectile)
        || !(projectile.shooter() instanceof PlayerRef p)
        || projectile.type().equals(SNOWBALL)
        || projectile.type().equals(FISHING_BOBBER)
        || !checkValidEntity(e.entity())) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> {
      if (!cooldowns.isReady(p.uuid(), getConfig().cooldownDelay)) {
        return;
      }
      double creditedDamage = CombatXp.credit(getPlayer(p).getData().getCombatXp(), getName(), p, e);
      if (creditedDamage <= 0D) {
        return;
      }
      Loc targetLocation = e.entity().location();
      Loc playerLocation = p.location();
      double distance = 0D;
      String projectileType = projectile.type().path();
      if (targetLocation.sameWorld(playerLocation)) {
        distance = targetLocation.distance(playerLocation);
        addStat(p, "ranged.distance", distance);
        addStat(p, "ranged.distance." + projectileType, distance);
        if (distance > 30) {
          addStat(p, "ranged.longshots", 1);
          fx(targetLocation, FxPriority.COMBAT)
              .dustRing(new Rgb(70, 220, 60), 0.9D, 16, 1.0F)
              .burst(Particles.END_ROD, 8, 0.3D)
              .chord(Sounds.ENTITY_ARROW_HIT, 0.7F, 1.6F, Sounds.BLOCK_NOTE_BLOCK_CHIME, 0.5F, 2.0F);
        }
      }
      addStat(p, "ranged.damage", creditedDamage);
      addStat(p, "ranged.damage." + projectileType, creditedDamage);
      cooldowns.mark(p.uuid());
      xp(p, targetLocation, (getConfig().hitDamageXPMultiplier * creditedDamage) + (distance * getConfig().hitDistanceXPMultiplier), "ranged:combat");

    });
  }


  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDeathEvent e) {
    PlayerRef p = e.entity().killer();
    if (e.platformCancelled() || p == null || GolemKillCredit.isGolemKill(e.entity())) {
      return;
    }
    shouldReturnForPlayer(p, () -> {
      ItemRef hand = p.inventory().mainHand();
      if (hand.type().equals(BOW) || hand.type().equals(CROSSBOW)) {
        addStat(p, "ranged.kills", 1);
        if (checkValidEntity(e.entity()) && MobOrigin.paysKillReward(p, e.entity())) {
          xp(p, e.entity().location(), getConfig().killXp, "ranged:kill");
        }
      }
    });
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityRemoveEvent e) {
    RangedProjectiles.forgetRemoved(e.entity(), e.cause());
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @NoArgsConstructor
  protected static class Config extends SkillConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    String skillColor = "&2";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Shoot XP for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double shootXP = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP for a kill made with a bow or crossbow in hand.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double killXp = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 1250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Hit Damage XPMultiplier for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hitDamageXPMultiplier = 1.75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Hit Distance XPMultiplier for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hitDistanceXPMultiplier = 1.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Ranged Reward for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeRangedReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Ranged Damage Reward for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeRangedDmgReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Ranged Distance Reward for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeRangedDistReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Ranged Kills Reward for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeRangedKillsReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Ranged Longshot Reward for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeRangedLongshotReward = 500;
  }
}
