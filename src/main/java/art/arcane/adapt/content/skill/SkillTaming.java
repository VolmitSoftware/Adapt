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
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.content.adaptation.excavation.ExcavationGraveDigger;
import art.arcane.adapt.content.adaptation.taming.*;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.reflect.registries.Particles;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;

public class SkillTaming extends SimpleSkill<SkillTaming.Config> {
  private final Cooldowns xpCooldowns = cooldowns();

  public SkillTaming() {
    super("taming", Localizer.dLocalize("skill.taming.icon"));
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("skill.taming.description"));
    setDisplayName(Localizer.dLocalize("skill.taming.name"));
    setColor(C.GOLD);
    setInterval(3480);
    setIcon(Material.LEAD);
    registerAdaptation(new TamingHealthBoost());
    registerAdaptation(new TamingDamage());
    registerAdaptation(new TamingHealthRegeneration());
    registerAdaptation(new TamingPackLeaderAura());
    registerAdaptation(new TamingBeastRecall());
    registerAdaptation(new TamingSharedPain());
    registerAdaptation(new TamingMountedTactics());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.LEAD)
        .key("challenge_taming_10")
        .model(CustomModel.get(Material.LEAD, "advancement", "taming", "challenge_taming_10"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NAME_TAG)
            .key("challenge_taming_50")
            .model(CustomModel.get(Material.NAME_TAG, "advancement", "taming", "challenge_taming_50"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .child(AdaptAdvancement.builder()
                .icon(Material.GOLDEN_APPLE)
                .key("challenge_taming_500")
                .model(CustomModel.get(Material.GOLDEN_APPLE, "advancement", "taming", "challenge_taming_500"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build())
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BONE)
        .key("challenge_pet_dmg_500")
        .model(CustomModel.get(Material.BONE, "advancement", "taming", "challenge_pet_dmg_500"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_SWORD)
            .key("challenge_pet_dmg_5k")
            .model(CustomModel.get(Material.DIAMOND_SWORD, "advancement", "taming", "challenge_pet_dmg_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.LEAD)
        .key("challenge_tamed_10")
        .model(CustomModel.get(Material.LEAD, "advancement", "taming", "challenge_tamed_10"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NAME_TAG)
            .key("challenge_tamed_100")
            .model(CustomModel.get(Material.NAME_TAG, "advancement", "taming", "challenge_tamed_100"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BONE)
        .key("challenge_pet_kills_25")
        .model(CustomModel.get(Material.BONE, "advancement", "taming", "challenge_pet_kills_25"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.GOLDEN_APPLE)
            .key("challenge_pet_kills_250")
            .model(CustomModel.get(Material.GOLDEN_APPLE, "advancement", "taming", "challenge_pet_kills_250"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GOLDEN_CARROT)
        .key("challenge_taming_2500")
        .model(CustomModel.get(Material.GOLDEN_CARROT, "advancement", "taming", "challenge_taming_2500"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENCHANTED_GOLDEN_APPLE)
            .key("challenge_taming_25k")
            .model(CustomModel.get(Material.ENCHANTED_GOLDEN_APPLE, "advancement", "taming", "challenge_taming_25k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_taming_10", "taming.bred", 10, getConfig().challengeTamingReward);
    registerMilestone("challenge_taming_50", "taming.bred", 50, getConfig().challengeTamingReward * 2);
    registerMilestone("challenge_taming_500", "taming.bred", 500, getConfig().challengeTamingReward * 5);
    registerMilestone("challenge_pet_dmg_500", "taming.pet.damage", 500, getConfig().challengePetDmgReward);
    registerMilestone("challenge_pet_dmg_5k", "taming.pet.damage", 5000, getConfig().challengePetDmgReward * 5);
    registerMilestone("challenge_tamed_10", "taming.tamed", 10, getConfig().challengeTamedReward);
    registerMilestone("challenge_tamed_100", "taming.tamed", 100, getConfig().challengeTamedReward * 5);
    registerMilestone("challenge_pet_kills_25", "taming.pet.kills", 25, getConfig().challengePetKillsReward);
    registerMilestone("challenge_pet_kills_250", "taming.pet.kills", 250, getConfig().challengePetKillsReward * 5);
    registerMilestone("challenge_taming_2500", "taming.bred", 2500, getConfig().challengeTamingReward * 10);
    registerMilestone("challenge_taming_25k", "taming.bred", 25000, getConfig().challengeTamingReward * 25);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityBreedEvent e) {
    for (Entity nearby : e.getEntity().getNearbyEntities(15, 15, 15)) {
      if (!(nearby instanceof Player p)) {
        continue;
      }
      shouldReturnForPlayer(p, e, () -> {
        addStat(p, "taming.bred", 1);
        if (!isOnCooldown(p)) {
          setCooldown(p);
          xp(p, getConfig().tameXpBase);
        }
      });
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (e.getDamager() instanceof Tameable tameable && tameable.isTamed() && tameable.getOwner() instanceof Player p) {
      shouldReturnForPlayer(p, e, () -> {
        addStat(p, "taming.pet.damage", e.getDamage());
        if (!isOnCooldown(p)) {
          setCooldown(p);
          xp(p, e.getEntity().getLocation(), e.getDamage() * getConfig().tameDamageXPMultiplier);
        }
      });
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityTameEvent e) {
    if (e.getOwner() instanceof Player p) {
      shouldReturnForPlayer(p, e, () -> {
        addStat(p, "taming.tamed", 1);
        xp(p, e.getEntity().getLocation(), getConfig().tameSuccessXP);
        timeline(e.getEntity())
            .duration(8)
            .priority(FxPriority.TRANSITION)
            .cullRadius(24)
            .frame((fx, tick, progress) -> {
              fx.ring(Particles.VILLAGER_HAPPY, 0.6D - (0.5D * progress), 12, 0.4D);
              if (tick == 0) {
                fx.chord(Sound.ENTITY_WOLF_PANT, 0.7F, 1.2F, Sound.BLOCK_NOTE_BLOCK_BELL, 0.5F, 1.6F);
              }
              if (progress >= 1.0D) {
                fx.burst(Particle.HEART, 3, 0.15D).sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.0F);
              }
            })
            .start();
      });
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    if (e.getEntity().getKiller() != null) {
      return;
    }

    if (TragoulSkeletalServant.isServant(e.getEntity()) || ExcavationGraveDigger.isGraveMob(e.getEntity())) {
      return;
    }

    if (e.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent damageEvent) {
      if (damageEvent.getDamager() instanceof Tameable tameable && tameable.isTamed() && tameable.getOwner() instanceof Player p) {
        shouldReturnForPlayer(p, () -> {
          addStat(p, "taming.pet.kills", 1);
          xp(p, e.getEntity().getLocation(), getConfig().petKillXP);
          fx(e.getEntity().getLocation(), FxPriority.COMBAT)
              .burst(Particles.CRIT_MAGIC, 4, 0.25D)
              .sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4F, 1.4F);
        });
      }
    }
  }

  private boolean isOnCooldown(Player p) {
    return !xpCooldowns.isReady(p.getUniqueId(), getConfig().cooldownDelay);
  }

  private void setCooldown(Player p) {
    xpCooldowns.mark(p.getUniqueId());
  }


  @Override
  public void onTick() {
    if (!this.isEnabled()) {
      return;
    }
    checkStatTrackersForOnlinePlayers();
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @NoArgsConstructor
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    String skillColor = "&6";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Tame Xp Base for the Taming skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double tameXpBase = 65;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Taming skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 1500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Tame Damage XPMultiplier for the Taming skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double tameDamageXPMultiplier = 8.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls XP awarded for successfully taming an animal.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double tameSuccessXP = 150;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls XP awarded when a pet kills a mob.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double petKillXP = 25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Taming Reward for the Taming skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeTamingReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Pet Damage Reward for the Taming skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengePetDmgReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Tamed Reward for the Taming skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeTamedReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Pet Kills Reward for the Taming skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengePetKillsReward = 500;
  }
}
