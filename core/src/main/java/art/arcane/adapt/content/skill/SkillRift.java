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
import art.arcane.adapt.content.adaptation.chronos.ChronosInstantRecall;
import art.arcane.adapt.content.adaptation.rift.RiftAccess;
import art.arcane.adapt.content.adaptation.rift.RiftBlink;
import art.arcane.adapt.content.adaptation.rift.RiftConduit;
import art.arcane.adapt.content.adaptation.rift.RiftDescent;
import art.arcane.adapt.content.adaptation.rift.RiftEnderTaglock;
import art.arcane.adapt.content.adaptation.rift.RiftEnderchest;
import art.arcane.adapt.content.adaptation.rift.RiftGate;
import art.arcane.adapt.content.adaptation.rift.RiftInflatedPocketDimension;
import art.arcane.adapt.content.adaptation.rift.RiftPearlRebound;
import art.arcane.adapt.content.adaptation.rift.RiftResist;
import art.arcane.adapt.content.adaptation.rift.RiftTeleports;
import art.arcane.adapt.content.adaptation.rift.RiftVisage;
import art.arcane.adapt.content.adaptation.rift.RiftVoidMagnet;
import art.arcane.adapt.content.adaptation.rift.RiftVoidSkin;
import art.arcane.adapt.content.adaptation.rift.blocks.RiftBoon;
import art.arcane.adapt.content.adaptation.rift.blocks.RiftFightStrike;
import art.arcane.adapt.content.adaptation.rift.blocks.RiftFuel;
import art.arcane.adapt.content.adaptation.rift.blocks.RiftState;
import art.arcane.adapt.content.adaptation.rift.blocks.RiftTrigger;
import art.arcane.adapt.content.adaptation.rift.blocks.RiftWorkStrike;
import art.arcane.adapt.content.adaptation.rift.upgraded.PhaseStep;
import art.arcane.adapt.content.adaptation.rift.upgraded.PocketRealm;
import art.arcane.adapt.content.adaptation.rift.upgraded.Voidborn;
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.SkillMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import lombok.NoArgsConstructor;

public class SkillRift extends SimpleSkill<SkillRift.Config> {
  private static final Id ENDER_PEARL = Id.minecraft("ender_pearl");
  private static final Id EYE_OF_ENDER = Id.minecraft("eye_of_ender");
  private static final Id END_CRYSTAL = Id.minecraft("end_crystal");
  private static final Id ENDERMAN = Id.minecraft("enderman");
  private static final Id ENDERMITE = Id.minecraft("endermite");
  private static final Id ENDER_DRAGON = Id.minecraft("ender_dragon");
  private static final Id SHULKER = Id.minecraft("shulker");

  private final Cooldowns teleportXpCooldown = cooldowns();
  private final Cooldowns portalXpCooldown = cooldowns();
  private final Cooldowns chorusXpCooldown = cooldowns();

  public SkillRift() {
    super("rift", SkillPresentation.of(SkillMessages.RIFT_NAME, SkillMessages.RIFT_ICON, SkillMessages.RIFT_DESCRIPTION,
        SkillMessages.RIFT_QUIP, SkillMessages.RIFT_UNLOCK_QUIP_1, SkillMessages.RIFT_UNLOCK_QUIP_2));
    registerConfiguration(Config.class);
    setColor(C.DARK_PURPLE);
    setInterval(1154);
    setIcon(Id.minecraft("ender_eye"));
    registerAdaptation(new RiftResist());
    registerAdaptation(new RiftAccess());
    registerAdaptation(new RiftEnderchest());
    registerAdaptation(new RiftGate());
    registerAdaptation(new RiftBlink());
    registerAdaptation(new RiftDescent());
    registerAdaptation(new RiftVisage());
    registerAdaptation(new RiftEnderTaglock());
    registerAdaptation(new RiftInflatedPocketDimension());
    registerAdaptation(new RiftVoidMagnet());
    registerAdaptation(new RiftVoidSkin());
    registerAdaptation(new RiftPearlRebound());
    registerAdaptation(new RiftConduit());
    registerUpgrades(new PhaseStep(), new PocketRealm(), new Voidborn());
    registerBlocks(new RiftTrigger(), new RiftFightStrike(), new RiftWorkStrike(), new RiftBoon(),
        new RiftFuel(), new RiftState());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_pearl"))
        .key("challenge_rift_50")
        .model(CustomModel.get(Id.minecraft("ender_pearl"), "advancement", "rift", "challenge_rift_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("ender_eye"))
            .key("challenge_rift_500")
            .model(CustomModel.get(Id.minecraft("ender_eye"), "advancement", "rift", "challenge_rift_500"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Id.minecraft("end_crystal"))
                .key("challenge_rift_5k")
                .model(CustomModel.get(Id.minecraft("end_crystal"), "advancement", "rift", "challenge_rift_5k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_rift_50", "rift.teleports", 50, () -> getConfig().challengeRiftReward);
    registerMilestone("challenge_rift_500", "rift.teleports", 500, () -> getConfig().challengeRiftReward * 3);
    registerMilestone("challenge_rift_5k", "rift.teleports", 5000, () -> getConfig().challengeRiftReward * 8);

    // Chain 2 - Ender Pearl Throws
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_pearl"))
        .key("challenge_rift_pearls_50")
        .model(CustomModel.get(Id.minecraft("ender_pearl"), "advancement", "rift", "challenge_rift_pearls_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("ender_eye"))
            .key("challenge_rift_pearls_500")
            .model(CustomModel.get(Id.minecraft("ender_eye"), "advancement", "rift", "challenge_rift_pearls_500"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_pearls_50", "rift.ender.pearls", 50, () -> getConfig().challengeRiftReward);
    registerMilestone("challenge_rift_pearls_500", "rift.ender.pearls", 500, () -> getConfig().challengeRiftReward * 3);

    // Chain 3 - Enderman Damage
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_pearl"))
        .key("challenge_rift_enderman_50")
        .model(CustomModel.get(Id.minecraft("ender_pearl"), "advancement", "rift", "challenge_rift_enderman_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("end_stone"))
            .key("challenge_rift_enderman_500")
            .model(CustomModel.get(Id.minecraft("end_stone"), "advancement", "rift", "challenge_rift_enderman_500"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_enderman_50", "rift.enderman.kills", 50, () -> getConfig().challengeRiftReward);
    registerMilestone("challenge_rift_enderman_500", "rift.enderman.kills", 500, () -> getConfig().challengeRiftReward * 3);

    // Chain 4 - Dragon Damage
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("dragon_breath"))
        .key("challenge_rift_dragon_500")
        .model(CustomModel.get(Id.minecraft("dragon_breath"), "advancement", "rift", "challenge_rift_dragon_500"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("dragon_head"))
            .key("challenge_rift_dragon_5k")
            .model(CustomModel.get(Id.minecraft("dragon_head"), "advancement", "rift", "challenge_rift_dragon_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_dragon_500", "rift.dragon.damage", 500, () -> getConfig().challengeRiftReward);
    registerMilestone("challenge_rift_dragon_5k", "rift.dragon.damage", 5000, () -> getConfig().challengeRiftReward * 3);

    // Chain 5 - End Crystal Destruction
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("end_crystal"))
        .key("challenge_rift_crystal_10")
        .model(CustomModel.get(Id.minecraft("end_crystal"), "advancement", "rift", "challenge_rift_crystal_10"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("beacon"))
            .key("challenge_rift_crystal_100")
            .model(CustomModel.get(Id.minecraft("beacon"), "advancement", "rift", "challenge_rift_crystal_100"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_crystal_10", "rift.crystals.destroyed", 10, () -> getConfig().challengeRiftReward);
    registerMilestone("challenge_rift_crystal_100", "rift.crystals.destroyed", 100, () -> getConfig().challengeRiftReward * 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerTeleportEvent e) {
    PlayerRef p = e.player();
    if (ChronosInstantRecall.isRecallTeleportSuppressed(p) || RiftTeleports.paysNoXp(e)) {
      return;
    }

    shouldReturnForPlayer(e.player(), e, () -> {
      addStat(p, "rift.teleports", 1);
      if (isPortalTrip(e.cause())) {
        if (portalXpCooldown.isReady(p.uuid(), getConfig().portalTripXpCooldown)) {
          portalXpCooldown.mark(p.uuid());
          xp(p, getConfig().portalTripXp, "rift:portal");
        }
      } else if (e.cause() == TeleportCause.CHORUS_FRUIT) {
        if (chorusXpCooldown.isReady(p.uuid(), getConfig().chorusTeleportXpCooldown)) {
          chorusXpCooldown.mark(p.uuid());
          xp(p, getConfig().chorusTeleportXp, "rift:chorus");
        }
      } else if (teleportXpCooldown.isReady(p.uuid(), (long) getConfig().teleportXPCooldown)) {
        xpSilent(p, getConfig().teleportXP, "rift:teleport");
        teleportXpCooldown.mark(p.uuid());
      }
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ProjectileLaunchEvent e) {
    if (!(e.projectile().shooter() instanceof PlayerRef p)) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> {
      if (e.projectile().type().equals(ENDER_PEARL)) {
        xp(p, getConfig().throwEnderpearlXP, "rift:throw:ender-pearl");
        addStat(p, "rift.ender.pearls", 1);
      } else if (e.projectile().type().equals(EYE_OF_ENDER)) {
        xp(p, getConfig().throwEnderEyeXP, "rift:throw:ender-eye");
      }
    });
  }

  private void handleEntityDamageByEntity(PlayerRef p, EntityDamageByEntityEvent event) {
    EntityRef entity = event.entity();
    if (entity.type().equals(END_CRYSTAL)) {
      rewardEndCrystalDestruction(p, entity);
      return;
    }

    if (!(entity instanceof LivingRef)) {
      return;
    }

    Id type = entity.type();
    double multiplier = type.equals(ENDERMAN) ? getConfig().damageEndermanXPMultiplier
        : type.equals(ENDERMITE) ? getConfig().damageEndermiteXPMultiplier
        : type.equals(ENDER_DRAGON) ? getConfig().damageEnderdragonXPMultiplier
        : 0;
    if (multiplier <= 0D) {
      return;
    }
    double damage = CombatXp.credit(getPlayer(p).getData().getCombatXp(), getName(), p, event);
    if (damage <= 0D) {
      return;
    }
    double xp = multiplier * damage;
    String rewardKey = type.equals(ENDERMAN) ? "rift:damage:enderman"
        : type.equals(ENDERMITE) ? "rift:damage:endermite"
        : type.equals(ENDER_DRAGON) ? "rift:damage:ender-dragon"
        : "rift:damage:other";
    if (xp > 0) {
      xp(p, xp, rewardKey);
    }
    if (type.equals(ENDER_DRAGON)) {
      addStat(p, "rift.dragon.damage", damage);
    }
  }

  private void rewardEndCrystalDestruction(PlayerRef p, EntityRef crystal) {
    xp(p, getConfig().destroyEndCrystalXP, "rift:kill:end-crystal");
    addStat(p, "rift.crystals.destroyed", 1);
    fx(crystal.location(), FxPriority.AMBIENT)
        .column(Particles.END_ROD, 6, 1.5)
        .particle(Particles.REVERSE_PORTAL, 8, 0, 0.5, 0, 0.4, 0.05)
        .chord(Sounds.BLOCK_BEACON_ACTIVATE, 0.5f, 1.4f, Sounds.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.4f, 1.9f);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (e.damager() instanceof PlayerRef p) {
      shouldReturnForPlayer(p, e, () -> handleEntityDamageByEntity(p, e));
    } else if (e.damager() instanceof ProjectileRef j && j.shooter() instanceof PlayerRef p) {
      shouldReturnForPlayer(p, e, () -> handleEntityDamageByEntity(p, e));
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDeathEvent e) {
    LivingRef victim = e.entity();
    PlayerRef p = victim.killer();
    if (e.platformCancelled() || p == null || GolemKillCredit.isGolemKill(victim)) {
      return;
    }
    Id type = victim.type();
    if (type.equals(ENDERMAN)) {
      shouldReturnForPlayer(p, () -> addStat(p, "rift.enderman.kills", 1));
      return;
    }
    double perMaxHealth = type.equals(SHULKER) ? getConfig().shulkerKillMaxHealthXpMultiplier
        : type.equals(ENDERMITE) ? getConfig().endermiteKillMaxHealthXpMultiplier : 0D;
    if (perMaxHealth > 0D && MobOrigin.paysKillReward(p, victim)) {
      shouldReturnForPlayer(p, () -> xp(p, victim.location(), perMaxHealth * Math.max(0D, victim.maxHealth()),
          "rift:kill"));
    }
  }

  static boolean isPortalTrip(TeleportCause cause) {
    return cause == TeleportCause.NETHER_PORTAL || cause == TeleportCause.END_PORTAL || cause == TeleportCause.END_GATEWAY;
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @NoArgsConstructor
  protected static class Config extends SkillConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    String skillColor = "&5";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Destroy End Crystal XP for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double destroyEndCrystalXP = 250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Enderman XPMultiplier for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageEndermanXPMultiplier = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Endermite XPMultiplier for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageEndermiteXPMultiplier = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Enderdragon XPMultiplier for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageEnderdragonXPMultiplier = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Throw Enderpearl XP for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double throwEnderpearlXP = 65;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Throw Ender Eye XP for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double throwEnderEyeXP = 30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Teleport XP for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double teleportXP = 15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Teleport XPCooldown for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double teleportXPCooldown = 60000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Rift Reward for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeRiftReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP for travelling through a nether portal, end portal, or end gateway.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double portalTripXp = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds between paid portal trips.", impact = "Higher values reduce how often portal hopping pays.")
    long portalTripXpCooldown = 60000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP for a chorus fruit teleport.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double chorusTeleportXp = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds between paid chorus fruit teleports.", impact = "Higher values reduce how often chorus fruit pays.")
    long chorusTeleportXpCooldown = 10000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP per point of maximum health for killing a shulker.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double shulkerKillMaxHealthXpMultiplier = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP per point of maximum health for killing an endermite.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double endermiteKillMaxHealthXpMultiplier = 3;
  }
}
