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
import art.arcane.adapt.api.mutation.BarterGesture;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.api.text.Text;
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
import art.arcane.adapt.content.golem.GolemKillCredit;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
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
import art.arcane.adapt.platform.value.Rgb;
import java.util.List;
import java.util.Set;

public class SkillRift extends SimpleSkill<SkillRift.Config> {
  public static final Id ID = Id.adapt("rift");
  private final XpSource portal = xpSource("portal", Text.key("adapt.xp_guide.rift.portal"));
  private final XpSource chorus = xpSource("chorus", Text.key("adapt.xp_guide.rift.teleport"));
  private final XpSource teleport = xpSource("teleport", Text.key("adapt.xp_guide.rift.teleport"));
  private final XpSource thrown = xpSource("throw", Text.key("adapt.xp_guide.rift.throw"));
  private final XpSource enderDamage = xpSource("damage", Text.key("adapt.xp_guide.rift.ender"));
  private final XpSource kill = xpSource("kill", Text.key("adapt.xp_guide.rift.kill"));
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
    super(ID);
    registerConfiguration(Config.class);
    setColor(Rgb.of(0xAA00AA));
    setInterval(1154);
    setIcon(Id.minecraft("ender_eye"));
    registerAdaptation(RiftResist.ID, RiftResist::new);
    registerAdaptation(RiftAccess.ID, RiftAccess::new);
    registerAdaptation(RiftEnderchest.ID, RiftEnderchest::new);
    registerAdaptation(RiftGate.ID, RiftGate::new);
    registerAdaptation(RiftBlink.ID, RiftBlink::new);
    registerAdaptation(RiftDescent.ID, RiftDescent::new);
    registerAdaptation(RiftVisage.ID, RiftVisage::new);
    registerAdaptation(RiftEnderTaglock.ID, RiftEnderTaglock::new);
    registerAdaptation(RiftInflatedPocketDimension.ID, RiftInflatedPocketDimension::new);
    registerAdaptation(RiftVoidMagnet.ID, RiftVoidMagnet::new);
    registerAdaptation(RiftVoidSkin.ID, RiftVoidSkin::new);
    registerAdaptation(RiftPearlRebound.ID, RiftPearlRebound::new);
    registerAdaptation(RiftConduit.ID, RiftConduit::new);
    registerMutationKit(MutationKit.builder()
        .words(Text.key("adapt.mutated.noun.rift"), Text.key("adapt.mutated.adjective.rift"))
        .trigger(TriggerKind.EITHER, RiftTrigger::new)
        .strikes(RiftFightStrike::new, RiftWorkStrike::new)
        .boon(RiftBoon::new)
        .fuel(RiftFuel::new)
        .state(RiftState::new)
        .upgrade(PhaseStep.ID, PhaseStep::new, List.of(RiftBlink.ID, RiftPearlRebound.ID, RiftResist.ID), Set.of())
        .toolUpgrade(PocketRealm.ID, PocketRealm::new,
            List.of(RiftInflatedPocketDimension.ID, RiftVoidMagnet.ID, RiftEnderchest.ID), Set.of())
        .upgrade(Voidborn.ID, Voidborn::new, List.of(RiftVoidSkin.ID, RiftVisage.ID, RiftDescent.ID), Set.of())
        .sounds(SoundPalette.of(Id.minecraft("entity.enderman.hurt"), Id.minecraft("block.ender_chest.close"),
            Id.minecraft("entity.ender_pearl.throw"), Id.minecraft("entity.endermite.step")))
        .tint(Rgb.of(0xc89af0))
        // Pocket Realm.
        .reserve(BarterGesture.SNEAK_LEFT_CLICK)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_pearl"))
        .key("challenge_rift_50")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("ender_eye"))
            .key("challenge_rift_500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Id.minecraft("end_crystal"))
                .key("challenge_rift_5k")
                .frame(AdvancementFrame.CHALLENGE)
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
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("ender_eye"))
            .key("challenge_rift_pearls_500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_pearls_50", "rift.ender.pearls", 50, () -> getConfig().challengeRiftReward);
    registerMilestone("challenge_rift_pearls_500", "rift.ender.pearls", 500, () -> getConfig().challengeRiftReward * 3);

    // Chain 3 - Enderman Damage
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_pearl"))
        .key("challenge_rift_enderman_50")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("end_stone"))
            .key("challenge_rift_enderman_500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_enderman_50", "rift.enderman.kills", 50, () -> getConfig().challengeRiftReward);
    registerMilestone("challenge_rift_enderman_500", "rift.enderman.kills", 500, () -> getConfig().challengeRiftReward * 3);

    // Chain 4 - Dragon Damage
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("dragon_breath"))
        .key("challenge_rift_dragon_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("dragon_head"))
            .key("challenge_rift_dragon_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_dragon_500", "rift.dragon.damage", 500, () -> getConfig().challengeRiftReward);
    registerMilestone("challenge_rift_dragon_5k", "rift.dragon.damage", 5000, () -> getConfig().challengeRiftReward * 3);

    // Chain 5 - End Crystal Destruction
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("end_crystal"))
        .key("challenge_rift_crystal_10")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("beacon"))
            .key("challenge_rift_crystal_100")
            .frame(AdvancementFrame.CHALLENGE)
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
          portal.award(p, getConfig().portalTripXp);
        }
      } else if (e.cause() == TeleportCause.CHORUS_FRUIT) {
        if (chorusXpCooldown.isReady(p.uuid(), getConfig().chorusTeleportXpCooldown)) {
          chorusXpCooldown.mark(p.uuid());
          chorus.award(p, getConfig().chorusTeleportXp);
        }
      } else if (teleportXpCooldown.isReady(p.uuid(), (long) getConfig().teleportXPCooldown)) {
        teleport.awardSilent(p, getConfig().teleportXP);
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
        thrown.award(p, getConfig().throwEnderpearlXP, "ender-pearl");
        addStat(p, "rift.ender.pearls", 1);
      } else if (e.projectile().type().equals(EYE_OF_ENDER)) {
        thrown.award(p, getConfig().throwEnderEyeXP, "ender-eye");
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
    double damage = CombatXp.credit(getId().toString(), p, event);
    if (damage <= 0D) {
      return;
    }
    double xp = multiplier * damage;
    String victim = type.equals(ENDERMAN) ? "enderman"
        : type.equals(ENDERMITE) ? "endermite"
        : type.equals(ENDER_DRAGON) ? "ender-dragon"
        : "other";
    if (xp > 0) {
      enderDamage.award(p, xp, victim);
    }
    if (type.equals(ENDER_DRAGON)) {
      addStat(p, "rift.dragon.damage", damage);
    }
  }

  private void rewardEndCrystalDestruction(PlayerRef p, EntityRef crystal) {
    kill.award(p, getConfig().destroyEndCrystalXP, "end-crystal");
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
      shouldReturnForPlayer(p, () -> kill.award(p, victim.location(), perMaxHealth * Math.max(0D, victim.maxHealth())));
    }
  }

  static boolean isPortalTrip(TeleportCause cause) {
    return cause == TeleportCause.NETHER_PORTAL || cause == TeleportCause.END_PORTAL || cause == TeleportCause.END_GATEWAY;
  }


  protected static class Config extends SkillConfig {
    @ConfigDoc(value = "Controls Destroy End Crystal XP for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double destroyEndCrystalXP = 250;
    @ConfigDoc(value = "Controls Damage Enderman XPMultiplier for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageEndermanXPMultiplier = 4;
    @ConfigDoc(value = "Controls Damage Endermite XPMultiplier for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageEndermiteXPMultiplier = 2;
    @ConfigDoc(value = "Controls Damage Enderdragon XPMultiplier for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageEnderdragonXPMultiplier = 8;
    @ConfigDoc(value = "Controls Throw Enderpearl XP for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double throwEnderpearlXP = 65;
    @ConfigDoc(value = "Controls Throw Ender Eye XP for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double throwEnderEyeXP = 30;
    @ConfigDoc(value = "Controls Teleport XP for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double teleportXP = 15;
    @ConfigDoc(value = "Controls Teleport XPCooldown for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double teleportXPCooldown = 60000;
    @ConfigDoc(value = "Controls Challenge Rift Reward for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeRiftReward = 500;
    @ConfigDoc(value = "XP for travelling through a nether portal, end portal, or end gateway.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double portalTripXp = 40;
    @ConfigDoc(value = "Milliseconds between paid portal trips.", impact = "Higher values reduce how often portal hopping pays.")
    long portalTripXpCooldown = 60000;
    @ConfigDoc(value = "XP for a chorus fruit teleport.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double chorusTeleportXp = 20;
    @ConfigDoc(value = "Milliseconds between paid chorus fruit teleports.", impact = "Higher values reduce how often chorus fruit pays.")
    long chorusTeleportXpCooldown = 10000;
    @ConfigDoc(value = "XP per point of maximum health for killing a shulker.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double shulkerKillMaxHealthXpMultiplier = 3;
    @ConfigDoc(value = "XP per point of maximum health for killing an endermite.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double endermiteKillMaxHealthXpMultiplier = 3;

    public Config() {
    }
  }
}
