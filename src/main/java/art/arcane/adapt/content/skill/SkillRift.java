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

import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.SkillMessages;

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
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
import art.arcane.adapt.content.adaptation.rift.RiftVisage;
import art.arcane.adapt.content.adaptation.rift.RiftVoidMagnet;
import art.arcane.adapt.content.adaptation.rift.RiftVoidSkin;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.EnderSignal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class SkillRift extends SimpleSkill<SkillRift.Config> {
  private final Cooldowns teleportXpCooldown = cooldowns();

  public SkillRift() {
    super("rift", SkillPresentation.of(SkillMessages.RIFT_NAME, SkillMessages.RIFT_ICON, SkillMessages.RIFT_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.DARK_PURPLE);
    setInterval(1154);
    setIcon(Material.ENDER_EYE);
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
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_PEARL)
        .key("challenge_rift_50")
        .model(CustomModel.get(Material.ENDER_PEARL, "advancement", "rift", "challenge_rift_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENDER_EYE)
            .key("challenge_rift_500")
            .model(CustomModel.get(Material.ENDER_EYE, "advancement", "rift", "challenge_rift_500"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .child(AdaptAdvancement.builder()
                .icon(Material.END_CRYSTAL)
                .key("challenge_rift_5k")
                .model(CustomModel.get(Material.END_CRYSTAL, "advancement", "rift", "challenge_rift_5k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build())
            .build())
        .build());
    registerMilestone("challenge_rift_50", "rift.teleports", 50, () -> getConfig().challengeRiftReward);
    registerMilestone("challenge_rift_500", "rift.teleports", 500, () -> getConfig().challengeRiftReward * 2);
    registerMilestone("challenge_rift_5k", "rift.teleports", 5000, () -> getConfig().challengeRiftReward * 5);

    // Chain 2 - Ender Pearl Throws
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_PEARL)
        .key("challenge_rift_pearls_50")
        .model(CustomModel.get(Material.ENDER_PEARL, "advancement", "rift", "challenge_rift_pearls_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENDER_EYE)
            .key("challenge_rift_pearls_500")
            .model(CustomModel.get(Material.ENDER_EYE, "advancement", "rift", "challenge_rift_pearls_500"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_rift_pearls_50", "rift.ender.pearls", 50, () -> getConfig().challengeRiftReward);
    registerMilestone("challenge_rift_pearls_500", "rift.ender.pearls", 500, () -> getConfig().challengeRiftReward * 2);

    // Chain 3 - Enderman Damage
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_PEARL)
        .key("challenge_rift_enderman_50")
        .model(CustomModel.get(Material.ENDER_PEARL, "advancement", "rift", "challenge_rift_enderman_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.END_STONE)
            .key("challenge_rift_enderman_500")
            .model(CustomModel.get(Material.END_STONE, "advancement", "rift", "challenge_rift_enderman_500"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_rift_enderman_50", "rift.enderman.kills", 50, () -> getConfig().challengeRiftReward);
    registerMilestone("challenge_rift_enderman_500", "rift.enderman.kills", 500, () -> getConfig().challengeRiftReward * 2);

    // Chain 4 - Dragon Damage
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DRAGON_BREATH)
        .key("challenge_rift_dragon_500")
        .model(CustomModel.get(Material.DRAGON_BREATH, "advancement", "rift", "challenge_rift_dragon_500"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DRAGON_HEAD)
            .key("challenge_rift_dragon_5k")
            .model(CustomModel.get(Material.DRAGON_HEAD, "advancement", "rift", "challenge_rift_dragon_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_rift_dragon_500", "rift.dragon.damage", 500, () -> getConfig().challengeRiftReward);
    registerMilestone("challenge_rift_dragon_5k", "rift.dragon.damage", 5000, () -> getConfig().challengeRiftReward * 2);

    // Chain 5 - End Crystal Destruction
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.END_CRYSTAL)
        .key("challenge_rift_crystal_10")
        .model(CustomModel.get(Material.END_CRYSTAL, "advancement", "rift", "challenge_rift_crystal_10"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.BEACON)
            .key("challenge_rift_crystal_100")
            .model(CustomModel.get(Material.BEACON, "advancement", "rift", "challenge_rift_crystal_100"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_rift_crystal_10", "rift.crystals.destroyed", 10, () -> getConfig().challengeRiftReward);
    registerMilestone("challenge_rift_crystal_100", "rift.crystals.destroyed", 100, () -> getConfig().challengeRiftReward * 2);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerTeleportEvent e) {
    Player p = e.getPlayer();
    if (ChronosInstantRecall.isRecallTeleportSuppressed(p)) {
      return;
    }

    shouldReturnForPlayer(e.getPlayer(), e, () -> {
      addStat(p, "rift.teleports", 1);
      if (teleportXpCooldown.isReady(p.getUniqueId(), (long) getConfig().teleportXPCooldown)) {
        xpSilent(p, getConfig().teleportXP, "rift:teleport");
        teleportXpCooldown.mark(p.getUniqueId());
      }
    });
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ProjectileLaunchEvent e) {
    if (!(e.getEntity().getShooter() instanceof Player p)) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> {
      if (e.getEntity() instanceof EnderPearl) {
        xp(p, getConfig().throwEnderpearlXP, "rift:throw:ender-pearl");
        addStat(p, "rift.ender.pearls", 1);
      } else if (e.getEntity() instanceof EnderSignal) {
        xp(p, getConfig().throwEnderEyeXP, "rift:throw:ender-eye");
      }
    });
  }

  private void handleEntityDamageByEntity(Entity entity, Player p, double damage) {
    if (entity instanceof EnderCrystal) {
      rewardEndCrystalDestruction(p, entity);
      return;
    }

    if (!(entity instanceof LivingEntity living)) {
      return;
    }

    IAttribute attribute = Version.get().getAttribute(living, Attributes.MAX_HEALTH);
    double baseHealth = attribute == null ? 1 : attribute.getBaseValue();
    double multiplier = switch (entity.getType()) {
      case ENDERMAN -> getConfig().damageEndermanXPMultiplier;
      case ENDERMITE -> getConfig().damageEndermiteXPMultiplier;
      case ENDER_DRAGON -> getConfig().damageEnderdragonXPMultiplier;
      default -> 0;
    };
    double xp = multiplier * Math.min(damage, baseHealth);
    String rewardKey = switch (entity.getType()) {
      case ENDERMAN -> "rift:damage:enderman";
      case ENDERMITE -> "rift:damage:endermite";
      case ENDER_DRAGON -> "rift:damage:ender-dragon";
      default -> "rift:damage:other";
    };
    if (xp > 0) {
      xp(p, xp, rewardKey);
    }
    if (entity.getType() == EntityType.ENDER_DRAGON) {
      addStat(p, "rift.dragon.damage", damage);
    }
  }

  private void rewardEndCrystalDestruction(Player p, Entity crystal) {
    xp(p, getConfig().destroyEndCrystalXP, "rift:kill:end-crystal");
    addStat(p, "rift.crystals.destroyed", 1);
    fx(crystal.getLocation(), FxPriority.AMBIENT)
        .column(Particles.END_ROD, 6, 1.5)
        .particle(Particle.REVERSE_PORTAL, 8, 0, 0.5, 0, 0.4, 0.05)
        .chord(Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.4f, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.4f, 1.9f);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (e.getDamager() instanceof Player p) {
      shouldReturnForPlayer(p, e, () -> handleEntityDamageByEntity(e.getEntity(), p, e.getDamage()));
    } else if (e.getDamager() instanceof Projectile j && j.getShooter() instanceof Player p) {
      shouldReturnForPlayer(p, e, () -> handleEntityDamageByEntity(e.getEntity(), p, e.getDamage()));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    Player p = e.getEntity().getKiller();
    if (p == null || e.getEntityType() != EntityType.ENDERMAN) {
      return;
    }

    shouldReturnForPlayer(p, () -> addStat(p, "rift.enderman.kills", 1));
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @NoArgsConstructor
  protected static class Config {
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
  }
}
