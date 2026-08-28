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

import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.content.adaptation.excavation.ExcavationGraveDigger;
import art.arcane.adapt.content.adaptation.hunter.HunterAdrenaline;
import art.arcane.adapt.content.adaptation.hunter.HunterBigGameHunter;
import art.arcane.adapt.content.adaptation.hunter.HunterBloodTrail;
import art.arcane.adapt.content.adaptation.hunter.HunterDropToInventory;
import art.arcane.adapt.content.adaptation.hunter.HunterInvis;
import art.arcane.adapt.content.adaptation.hunter.HunterJumpBoost;
import art.arcane.adapt.content.adaptation.hunter.HunterLuck;
import art.arcane.adapt.content.adaptation.hunter.HunterPredatorFocus;
import art.arcane.adapt.content.adaptation.hunter.HunterRegen;
import art.arcane.adapt.content.adaptation.hunter.HunterResistance;
import art.arcane.adapt.content.adaptation.hunter.HunterSnareLine;
import art.arcane.adapt.content.adaptation.hunter.HunterSpeed;
import art.arcane.adapt.content.adaptation.hunter.HunterStrength;
import art.arcane.adapt.content.adaptation.hunter.HunterTrophySkinner;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.reflect.registries.Attributes;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

public class SkillHunter extends SimpleSkill<SkillHunter.Config> {
  private static final NamespacedKey SPAWNER_MOB_KEY = NamespacedKey.fromString("adapt:hunter-spawner-mob");

  private final Cooldowns cooldowns = cooldowns();

  public SkillHunter() {
    super("hunter", SkillPresentation.of(SkillMessages.HUNTER_NAME, SkillMessages.HUNTER_ICON, SkillMessages.HUNTER_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.RED);
    setInterval(4150);
    setIcon(Material.BONE);
    registerAdaptation(new HunterAdrenaline());
    registerAdaptation(new HunterRegen());
    registerAdaptation(new HunterInvis());
    registerAdaptation(new HunterJumpBoost());
    registerAdaptation(new HunterLuck());
    registerAdaptation(new HunterSpeed());
    registerAdaptation(new HunterStrength());
    registerAdaptation(new HunterResistance());
    registerAdaptation(new HunterDropToInventory());
    registerAdaptation(new HunterTrophySkinner());
    registerAdaptation(new HunterPredatorFocus());
    registerAdaptation(new HunterBigGameHunter());
    registerAdaptation(new HunterBloodTrail());
    registerAdaptation(new HunterSnareLine());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BONE)
        .key("challenge_novice_hunter")
        .model(CustomModel.get(Material.BONE, "advancement", "hunter", "challenge_novice_hunter"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.IRON_SWORD)
            .key("challenge_intermediate_hunter")
            .model(CustomModel.get(Material.IRON_SWORD, "advancement", "hunter", "challenge_intermediate_hunter"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Material.DIAMOND_SWORD)
                .key("challenge_advanced_hunter")
                .model(CustomModel.get(Material.DIAMOND_SWORD, "advancement", "hunter", "challenge_advanced_hunter"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CREEPER_HEAD)
        .key("challenge_creeper_conqueror")
        .model(CustomModel.get(Material.CREEPER_HEAD, "advancement", "hunter", "challenge_creeper_conqueror"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.TNT)
            .key("challenge_creeper_annihilator")
            .model(CustomModel.get(Material.TNT, "advancement", "hunter", "challenge_creeper_annihilator"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BONE)
        .key("challenge_kills_500")
        .model(CustomModel.get(Material.BONE, "advancement", "hunter", "challenge_kills_500"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.WITHER_SKELETON_SKULL)
            .key("challenge_kills_5k")
            .model(CustomModel.get(Material.WITHER_SKELETON_SKULL, "advancement", "hunter", "challenge_kills_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DRAGON_HEAD)
        .key("challenge_boss_1")
        .model(CustomModel.get(Material.DRAGON_HEAD, "advancement", "hunter", "challenge_boss_1"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHER_STAR)
            .key("challenge_boss_10")
            .model(CustomModel.get(Material.NETHER_STAR, "advancement", "hunter", "challenge_boss_10"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());

    registerMilestone("challenge_novice_hunter", "killed.monsters", 100, () -> getConfig().killsChallengeReward);
    registerMilestone("challenge_intermediate_hunter", "killed.monsters", 500, () -> getConfig().killsChallengeReward * 2);
    registerMilestone("challenge_advanced_hunter", "killed.monsters", 5000, () -> getConfig().killsChallengeReward * 5);
    registerMilestone("challenge_creeper_conqueror", "killed.creepers", 50, () -> getConfig().killsChallengeReward);
    registerMilestone("challenge_creeper_annihilator", "killed.creepers", 200, () -> getConfig().killsChallengeReward * 2);
    registerMilestone("challenge_kills_500", "killed.kills", 500, () -> getConfig().killsChallengeReward);
    registerMilestone("challenge_kills_5k", "killed.kills", 5000, () -> getConfig().killsChallengeReward * 5);
    registerMilestone("challenge_boss_1", "hunter.boss.kills", 1, () -> getConfig().bossKillReward);
    registerMilestone("challenge_boss_10", "hunter.boss.kills", 10, () -> getConfig().bossKillReward * 5);
  }

  private void handleCooldownAndXp(Player p, double xpAmount) {
    handleCooldownAndXp(p, xpAmount, null);
  }

  private void handleCooldownAndXp(Player p, double xpAmount, String rewardKey) {
    if (!cooldowns.isReady(p.getUniqueId(), getConfig().cooldownDelay)) {
      return;
    }
    cooldowns.mark(p.getUniqueId());
    xp(p, xpAmount, rewardKey);
  }

  private void bossCelebration(Location center) {
    timeline(center)
        .duration(12)
        .priority(FxPriority.TRANSITION)
        .cullRadius(48)
        .frame((f, tick, progress) -> {
          f.dustRing(1.0D + (4.0D * progress), 24, 1.4F);
          f.ring(Particle.SOUL, 1.0D + (4.0D * progress), 16, 0.2D);
          if (tick == 0) {
            f.chord(Sound.BLOCK_BEACON_DEACTIVATE, 0.8F, 0.6F, Sound.ITEM_TOTEM_USE, 0.6F, 0.8F);
          }
          if (tick == 6) {
            f.sound(Sound.ENTITY_ENDER_DRAGON_FLAP, 0.7F, 0.7F);
          }
        })
        .start();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    if (e.getEntity().getKiller() == null) {
      return;
    }
    Player p = e.getEntity().getKiller();

    if (!getConfig().getXpForAttackingWithTools) {
      return;
    }

    if (TragoulSkeletalServant.isServant(e.getEntity()) || ExcavationGraveDigger.isGraveMob(e.getEntity())) {
      return;
    }

    shouldReturnForPlayer(p, () -> {
      recordKillStats(p, e.getEntity());
      if (e.getEntity().getType().equals(EntityType.CREEPER)) {
        double cmult = getConfig().creeperKillMultiplier;
        IAttribute attribute = Version.get().getAttribute(e.getEntity(), Attributes.MAX_HEALTH);
        double xpAmount = (attribute == null ? 1 : attribute.getValue()) * getConfig().killMaxHealthXPMultiplier * cmult;
        if (isSpawnerMob(e.getEntity())) {
          xpAmount *= getConfig().spawnerMobReductionXpMultiplier;
        }
        handleCooldownAndXp(p, xpAmount, "hunter:kill:creeper");
      } else {
        handleEntityKill(p, e.getEntity());
      }
      EntityType type = e.getEntity().getType();
      if (type == EntityType.ENDER_DRAGON || type == EntityType.WITHER || type == EntityType.ELDER_GUARDIAN || type == EntityType.WARDEN) {
        addStat(p, "hunter.boss.kills", 1);
        bossCelebration(e.getEntity().getLocation().add(0, 1.0D, 0));
      }
    });
  }


  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(CreatureSpawnEvent e) {
    if (!isEnabled()) {
      return;
    }
    if (e.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.SPAWNER)) {
      e.getEntity().getPersistentDataContainer().set(SPAWNER_MOB_KEY, PersistentDataType.BYTE, (byte) 1);
    }
  }

  private void recordKillStats(Player p, Entity entity) {
    addStat(p, "killed.kills", 1);
    if (entity instanceof Monster) {
      addStat(p, "killed.monsters", 1);
    }
    if (entity.getType() == EntityType.CREEPER) {
      addStat(p, "killed.creepers", 1);
    }
  }

  private boolean isSpawnerMob(Entity entity) {
    return entity.getPersistentDataContainer().has(SPAWNER_MOB_KEY, PersistentDataType.BYTE);
  }

  private void handleEntityKill(Player p, Entity entity) {
    if (entity instanceof LivingEntity livingEntity) {
      IAttribute attribute = Version.get().getAttribute(livingEntity, Attributes.MAX_HEALTH);
      double xpAmount = (attribute == null ? 1 : attribute.getValue()) * getConfig().killMaxHealthXPMultiplier;
      if (isSpawnerMob(entity)) {
        xpAmount *= getConfig().spawnerMobReductionXpMultiplier;
      }
      String rewardKey = "hunter:kill:" + entity.getType().name().toLowerCase(Locale.ROOT);
      handleCooldownAndXp(p, xpAmount, rewardKey);
    }
  }


  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @NoArgsConstructor
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    String skillColor = "&c";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Get Xp For Attacking With Tools for the Hunter skill.", impact = "True enables this behavior and false disables it.")
    boolean getXpForAttackingWithTools = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Creeper Kill Multiplier for the Hunter skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double creeperKillMultiplier = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Kill Max Health XPMultiplier for the Hunter skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double killMaxHealthXPMultiplier = 3.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Hunter skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 1000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Spawner Mob Reduction Xp Multiplier for the Hunter skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double spawnerMobReductionXpMultiplier = 0.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Kills Challenge Reward for the Hunter skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double killsChallengeReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Boss Kill Reward for the Hunter skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bossKillReward = 1000;
  }
}
