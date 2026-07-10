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
import art.arcane.adapt.content.adaptation.ranged.RangedArrowRecovery;
import art.arcane.adapt.content.adaptation.ranged.RangedFetchShot;
import art.arcane.adapt.content.adaptation.ranged.RangedFloaters;
import art.arcane.adapt.content.adaptation.ranged.RangedForce;
import art.arcane.adapt.content.adaptation.ranged.RangedHeartseeker;
import art.arcane.adapt.content.adaptation.ranged.RangedHeavyDraw;
import art.arcane.adapt.content.adaptation.ranged.RangedLungeShot;
import art.arcane.adapt.content.adaptation.ranged.RangedPiercing;
import art.arcane.adapt.content.adaptation.ranged.RangedPinningShot;
import art.arcane.adapt.content.adaptation.ranged.RangedRicochetBolt;
import art.arcane.adapt.content.adaptation.ranged.RangedTrajectorySight;
import art.arcane.adapt.content.adaptation.ranged.RangedWebBomb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.reflect.registries.Particles;
import lombok.NoArgsConstructor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public class SkillRanged extends SimpleSkill<SkillRanged.Config> {
  private final Cooldowns cooldowns = cooldowns();

  public SkillRanged() {
    super("ranged", Localizer.dLocalize("skill.ranged.icon"));
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("skill.ranged.description"));
    setDisplayName(Localizer.dLocalize("skill.ranged.name"));
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
    setIcon(Material.CROSSBOW);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ARROW)
        .key("challenge_ranged_100")
        .model(CustomModel.get(Material.ARROW, "advancement", "ranged", "challenge_ranged_100"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SPECTRAL_ARROW)
            .key("challenge_ranged_1k")
            .model(CustomModel.get(Material.SPECTRAL_ARROW, "advancement", "ranged", "challenge_ranged_1k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .child(AdaptAdvancement.builder()
                .icon(Material.CROSSBOW)
                .key("challenge_ranged_10k")
                .model(CustomModel.get(Material.CROSSBOW, "advancement", "ranged", "challenge_ranged_10k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build())
            .build())
        .build());
    registerMilestone("challenge_ranged_100", "ranged.shotsfired", 100, () -> getConfig().challengeRangedReward);
    registerMilestone("challenge_ranged_1k", "ranged.shotsfired", 1000, () -> getConfig().challengeRangedReward * 2);
    registerMilestone("challenge_ranged_10k", "ranged.shotsfired", 10000, () -> getConfig().challengeRangedReward * 5);

    // Chain 2 - Ranged Damage
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BOW)
        .key("challenge_ranged_dmg_1k")
        .model(CustomModel.get(Material.BOW, "advancement", "ranged", "challenge_ranged_dmg_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.CROSSBOW)
            .key("challenge_ranged_dmg_10k")
            .model(CustomModel.get(Material.CROSSBOW, "advancement", "ranged", "challenge_ranged_dmg_10k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_ranged_dmg_1k", "ranged.damage", 1000, () -> getConfig().challengeRangedDmgReward);
    registerMilestone("challenge_ranged_dmg_10k", "ranged.damage", 10000, () -> getConfig().challengeRangedDmgReward * 3);

    // Chain 3 - Ranged Distance
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ARROW)
        .key("challenge_ranged_dist_5k")
        .model(CustomModel.get(Material.ARROW, "advancement", "ranged", "challenge_ranged_dist_5k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SPECTRAL_ARROW)
            .key("challenge_ranged_dist_50k")
            .model(CustomModel.get(Material.SPECTRAL_ARROW, "advancement", "ranged", "challenge_ranged_dist_50k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_ranged_dist_5k", "ranged.distance", 5000, () -> getConfig().challengeRangedDistReward);
    registerMilestone("challenge_ranged_dist_50k", "ranged.distance", 50000, () -> getConfig().challengeRangedDistReward * 3);

    // Chain 4 - Ranged Kills
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.TIPPED_ARROW)
        .key("challenge_ranged_kills_50")
        .model(CustomModel.get(Material.TIPPED_ARROW, "advancement", "ranged", "challenge_ranged_kills_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.TARGET)
            .key("challenge_ranged_kills_500")
            .model(CustomModel.get(Material.TARGET, "advancement", "ranged", "challenge_ranged_kills_500"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_ranged_kills_50", "ranged.kills", 50, () -> getConfig().challengeRangedKillsReward);
    registerMilestone("challenge_ranged_kills_500", "ranged.kills", 500, () -> getConfig().challengeRangedKillsReward * 3);

    // Chain 5 - Longshots
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SPYGLASS)
        .key("challenge_longshot_25")
        .model(CustomModel.get(Material.SPYGLASS, "advancement", "ranged", "challenge_longshot_25"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENDER_EYE)
            .key("challenge_longshot_250")
            .model(CustomModel.get(Material.ENDER_EYE, "advancement", "ranged", "challenge_longshot_250"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_longshot_25", "ranged.longshots", 25, () -> getConfig().challengeRangedLongshotReward);
    registerMilestone("challenge_longshot_250", "ranged.longshots", 250, () -> getConfig().challengeRangedLongshotReward * 3);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ProjectileLaunchEvent e) {
    Projectile projectile = e.getEntity();
    if (!(projectile.getShooter() instanceof Player p)
        || projectile instanceof Snowball
        || projectile instanceof FishHook) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> {
      String projectileType = projectile.getType().name().toLowerCase(Locale.ROOT);
      addStat(p, "ranged.shotsfired", 1);
      addStat(p, "ranged.shotsfired." + projectileType, 1);
      if (!cooldowns.isReady(p.getUniqueId(), getConfig().cooldownDelay)) {
        return;
      }
      cooldowns.mark(p.getUniqueId());
      xp(p, getConfig().shootXP);
    });
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Projectile projectile)
        || !(projectile.getShooter() instanceof Player p)
        || projectile instanceof Snowball
        || projectile instanceof FishHook
        || !checkValidEntity(e.getEntity().getType())) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> {
      Location targetLocation = e.getEntity().getLocation();
      Location playerLocation = p.getLocation();
      double distance = 0D;
      String projectileType = projectile.getType().name().toLowerCase(Locale.ROOT);
      if (targetLocation.getWorld() == playerLocation.getWorld()) {
        distance = targetLocation.distance(playerLocation);
        addStat(p, "ranged.distance", distance);
        addStat(p, "ranged.distance." + projectileType, distance);
        if (distance > 30) {
          addStat(p, "ranged.longshots", 1);
          fx(targetLocation, FxPriority.COMBAT)
              .dustRing(Color.fromRGB(70, 220, 60), 0.9D, 16, 1.0F)
              .burst(Particles.END_ROD, 8, 0.3D)
              .chord(Sound.ENTITY_ARROW_HIT, 0.7F, 1.6F, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5F, 2.0F);
        }
      }
      addStat(p, "ranged.damage", e.getDamage());
      addStat(p, "ranged.damage." + projectileType, e.getDamage());
      if (!cooldowns.isReady(p.getUniqueId(), getConfig().cooldownDelay)) {
        return;
      }
      cooldowns.mark(p.getUniqueId());
      xp(p, targetLocation, (getConfig().hitDamageXPMultiplier * e.getDamage()) + (distance * getConfig().hitDistanceXPMultiplier));

    });
  }


  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    Player p = e.getEntity().getKiller();
    if (p == null) {
      return;
    }
    shouldReturnForPlayer(p, () -> {
      ItemStack hand = p.getInventory().getItemInMainHand();
      if (hand.getType() == Material.BOW || hand.getType() == Material.CROSSBOW) {
        addStat(p, "ranged.kills", 1);
      }
    });
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @NoArgsConstructor
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    String skillColor = "&2";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Shoot XP for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double shootXP = 5;
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
