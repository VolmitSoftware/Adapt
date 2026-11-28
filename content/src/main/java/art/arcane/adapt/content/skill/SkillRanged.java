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
import art.arcane.adapt.content.adaptation.ranged.blocks.RangedBoon;
import art.arcane.adapt.content.adaptation.ranged.blocks.RangedFightStrike;
import art.arcane.adapt.content.adaptation.ranged.blocks.RangedFuel;
import art.arcane.adapt.content.adaptation.ranged.blocks.RangedState;
import art.arcane.adapt.content.adaptation.ranged.blocks.RangedTrigger;
import art.arcane.adapt.content.adaptation.ranged.blocks.RangedWorkStrike;
import art.arcane.adapt.content.adaptation.ranged.upgraded.HuntersWeb;
import art.arcane.adapt.content.adaptation.ranged.upgraded.Stormshot;
import art.arcane.adapt.content.adaptation.ranged.upgraded.Trueflight;
import art.arcane.adapt.content.golem.GolemKillCredit;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
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
import java.util.List;
import java.util.Set;

public class SkillRanged extends SimpleSkill<SkillRanged.Config> {
  public static final Id ID = Id.adapt("ranged");
  private final XpSource shoot = xpSource("shoot", Text.key("adapt.xp_guide.ranged.shoot"));
  private final XpSource combat = xpSource("combat", Text.key("adapt.xp_guide.ranged.hit"));
  private final XpSource kill = xpSource("kill", Text.key("adapt.xp_guide.ranged.kill"));
  private static final Id BOW = Id.minecraft("bow");
  private static final Id CROSSBOW = Id.minecraft("crossbow");
  private static final Id SNOWBALL = Id.minecraft("snowball");
  private static final Id FISHING_BOBBER = Id.minecraft("fishing_bobber");

  private final Cooldowns cooldowns = cooldowns();

  public SkillRanged() {
    super(ID);
    registerConfiguration(Config.class);
    setColor(Rgb.of(0x00AA00));
    setInterval(3044);
    registerAdaptation(RangedForce.ID, RangedForce::new);
    registerAdaptation(RangedPiercing.ID, RangedPiercing::new);
    registerAdaptation(RangedArrowRecovery.ID, RangedArrowRecovery::new);
    registerAdaptation(RangedLungeShot.ID, RangedLungeShot::new);
    registerAdaptation(RangedWebBomb.ID, RangedWebBomb::new);
    registerAdaptation(RangedTrajectorySight.ID, RangedTrajectorySight::new);
    registerAdaptation(RangedFloaters.ID, RangedFloaters::new);
    registerAdaptation(RangedPinningShot.ID, RangedPinningShot::new);
    registerAdaptation(RangedRicochetBolt.ID, RangedRicochetBolt::new);
    registerAdaptation(RangedFetchShot.ID, RangedFetchShot::new);
    registerAdaptation(RangedHeavyDraw.ID, RangedHeavyDraw::new);
    registerAdaptation(RangedHeartseeker.ID, RangedHeartseeker::new);
    registerMutationKit(MutationKit.builder()
        .words(Text.key("adapt.mutated.noun.ranged"), Text.key("adapt.mutated.adjective.ranged"))
        .trigger(TriggerKind.FIGHT, RangedTrigger::new)
        .strikes(RangedFightStrike::new, RangedWorkStrike::new)
        .boon(RangedBoon::new)
        .fuel(RangedFuel::new)
        .state(RangedState::new)
        .toolUpgrade(Stormshot.ID, Stormshot::new, List.of(RangedForce.ID, RangedHeavyDraw.ID, RangedPiercing.ID), Set.of())
        .upgrade(HuntersWeb.ID, HuntersWeb::new, List.of(RangedWebBomb.ID, RangedPinningShot.ID, RangedFloaters.ID), Set.of(RangedWebBomb.ID))
        .upgrade(Trueflight.ID, Trueflight::new,
            List.of(RangedHeartseeker.ID, RangedRicochetBolt.ID, RangedTrajectorySight.ID), Set.of())
        .sounds(SoundPalette.of(Id.minecraft("entity.arrow.shoot"), Id.minecraft("entity.item.pickup"),
            Id.minecraft("item.crossbow.quick_charge_3"), Id.minecraft("block.tripwire.click_on")))
        .tint(Rgb.of(0x78c048))
        .build());
    setIcon(Id.minecraft("crossbow"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("arrow"))
        .key("challenge_ranged_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("spectral_arrow"))
            .key("challenge_ranged_1k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Id.minecraft("crossbow"))
                .key("challenge_ranged_10k")
                .frame(AdvancementFrame.CHALLENGE)
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
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("crossbow"))
            .key("challenge_ranged_dmg_10k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_ranged_dmg_1k", "ranged.damage", 1000, () -> getConfig().challengeRangedDmgReward);
    registerMilestone("challenge_ranged_dmg_10k", "ranged.damage", 10000, () -> getConfig().challengeRangedDmgReward * 3);

    // Chain 3 - Ranged Distance
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("arrow"))
        .key("challenge_ranged_dist_5k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("spectral_arrow"))
            .key("challenge_ranged_dist_50k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_ranged_dist_5k", "ranged.distance", 5000, () -> getConfig().challengeRangedDistReward);
    registerMilestone("challenge_ranged_dist_50k", "ranged.distance", 50000, () -> getConfig().challengeRangedDistReward * 3);

    // Chain 4 - Ranged Kills
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("tipped_arrow"))
        .key("challenge_ranged_kills_50")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("target"))
            .key("challenge_ranged_kills_500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_ranged_kills_50", "ranged.kills", 50, () -> getConfig().challengeRangedKillsReward);
    registerMilestone("challenge_ranged_kills_500", "ranged.kills", 500, () -> getConfig().challengeRangedKillsReward * 3);

    // Chain 5 - Longshots
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("spyglass"))
        .key("challenge_longshot_25")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("ender_eye"))
            .key("challenge_longshot_250")
            .frame(AdvancementFrame.CHALLENGE)
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
      shoot.award(p, getConfig().shootXP);
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
      double creditedDamage = CombatXp.credit(getId().toString(), p, e);
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
      combat.award(p, targetLocation, (getConfig().hitDamageXPMultiplier * creditedDamage) + (distance * getConfig().hitDistanceXPMultiplier));

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
          kill.award(p, e.entity().location(), getConfig().killXp);
        }
      }
    });
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityRemoveEvent e) {
    RangedProjectiles.forgetRemoved(e.entity(), e.cause());
  }


  protected static class Config extends SkillConfig {
    @ConfigDoc(value = "Controls Shoot XP for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double shootXP = 5;
    @ConfigDoc(value = "XP for a kill made with a bow or crossbow in hand.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double killXp = 40;
    @ConfigDoc(value = "Controls Cooldown Delay for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 1250;
    @ConfigDoc(value = "Controls Hit Damage XPMultiplier for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hitDamageXPMultiplier = 1.75;
    @ConfigDoc(value = "Controls Hit Distance XPMultiplier for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hitDistanceXPMultiplier = 1.2;
    @ConfigDoc(value = "Controls Challenge Ranged Reward for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeRangedReward = 500;
    @ConfigDoc(value = "Controls Challenge Ranged Damage Reward for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeRangedDmgReward = 500;
    @ConfigDoc(value = "Controls Challenge Ranged Distance Reward for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeRangedDistReward = 500;
    @ConfigDoc(value = "Controls Challenge Ranged Kills Reward for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeRangedKillsReward = 500;
    @ConfigDoc(value = "Controls Challenge Ranged Longshot Reward for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeRangedLongshotReward = 500;

    public Config() {
    }
  }
}
