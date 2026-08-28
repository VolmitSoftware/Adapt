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
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedBatteringCharge;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedComboChain;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedDisarm;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedGlassCannon;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedGrapple;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedIronFists;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedMeditation;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedPower;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedPressurePoint;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedSecondWind;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedShockwaveClap;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedSuckerPunch;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.reflect.registries.Particles;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

public class SkillUnarmed extends SimpleSkill<SkillUnarmed.Config> {
  private final Cooldowns cooldowns = cooldowns();

  public SkillUnarmed() {
    super("unarmed", SkillPresentation.of(SkillMessages.UNARMED_NAME, SkillMessages.UNARMED_ICON, SkillMessages.UNARMED_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.YELLOW);
    setInterval(2579);
    registerAdaptation(new UnarmedSuckerPunch());
    registerAdaptation(new UnarmedPower());
    registerAdaptation(new UnarmedGlassCannon());
    registerAdaptation(new UnarmedBatteringCharge());
    registerAdaptation(new UnarmedComboChain());
    registerAdaptation(new UnarmedDisarm());
    registerAdaptation(new UnarmedPressurePoint());
    registerAdaptation(new UnarmedShockwaveClap());
    registerAdaptation(new UnarmedIronFists());
    registerAdaptation(new UnarmedGrapple());
    registerAdaptation(new UnarmedSecondWind());
    registerAdaptation(new UnarmedMeditation());
    setIcon(Material.FIRE_CHARGE);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.FIRE_CHARGE)
        .key("challenge_unarmed_100")
        .model(CustomModel.get(Material.FIRE_CHARGE, "advancement", "unarmed", "challenge_unarmed_100"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.BLAZE_POWDER)
            .key("challenge_unarmed_1k")
            .model(CustomModel.get(Material.BLAZE_POWDER, "advancement", "unarmed", "challenge_unarmed_1k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Material.NETHER_STAR)
                .key("challenge_unarmed_10k")
                .model(CustomModel.get(Material.NETHER_STAR, "advancement", "unarmed", "challenge_unarmed_10k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_unarmed_100", "unarmed.hits", 100, () -> getConfig().challengeUnarmedReward);
    registerMilestone("challenge_unarmed_1k", "unarmed.hits", 1000, () -> getConfig().challengeUnarmedReward * 2);
    registerMilestone("challenge_unarmed_10k", "unarmed.hits", 10000, () -> getConfig().challengeUnarmedReward * 5);

    // Chain 2 - Unarmed Damage
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.FIRE_CHARGE)
        .key("challenge_unarmed_dmg_1k")
        .model(CustomModel.get(Material.FIRE_CHARGE, "advancement", "unarmed", "challenge_unarmed_dmg_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.BLAZE_ROD)
            .key("challenge_unarmed_dmg_10k")
            .model(CustomModel.get(Material.BLAZE_ROD, "advancement", "unarmed", "challenge_unarmed_dmg_10k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_unarmed_dmg_1k", "unarmed.damage", 1000, () -> getConfig().challengeUnarmedDmgReward);
    registerMilestone("challenge_unarmed_dmg_10k", "unarmed.damage", 10000, () -> getConfig().challengeUnarmedDmgReward * 3);

    // Chain 3 - Unarmed Kills
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ROTTEN_FLESH)
        .key("challenge_unarmed_kills_25")
        .model(CustomModel.get(Material.ROTTEN_FLESH, "advancement", "unarmed", "challenge_unarmed_kills_25"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.ZOMBIE_HEAD)
            .key("challenge_unarmed_kills_250")
            .model(CustomModel.get(Material.ZOMBIE_HEAD, "advancement", "unarmed", "challenge_unarmed_kills_250"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_unarmed_kills_25", "unarmed.kills", 25, () -> getConfig().challengeUnarmedKillsReward);
    registerMilestone("challenge_unarmed_kills_250", "unarmed.kills", 250, () -> getConfig().challengeUnarmedKillsReward * 3);

    // Chain 4 - Unarmed Criticals
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SUGAR)
        .key("challenge_unarmed_crit_25")
        .model(CustomModel.get(Material.SUGAR, "advancement", "unarmed", "challenge_unarmed_crit_25"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.FERMENTED_SPIDER_EYE)
            .key("challenge_unarmed_crit_250")
            .model(CustomModel.get(Material.FERMENTED_SPIDER_EYE, "advancement", "unarmed", "challenge_unarmed_crit_250"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_unarmed_crit_25", "unarmed.critical", 25, () -> getConfig().challengeUnarmedCritReward);
    registerMilestone("challenge_unarmed_crit_250", "unarmed.critical", 250, () -> getConfig().challengeUnarmedCritReward * 3);

    // Chain 5 - Unarmed Heavy Hits
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.TNT)
        .key("challenge_unarmed_heavy_25")
        .model(CustomModel.get(Material.TNT, "advancement", "unarmed", "challenge_unarmed_heavy_25"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.END_CRYSTAL)
            .key("challenge_unarmed_heavy_250")
            .model(CustomModel.get(Material.END_CRYSTAL, "advancement", "unarmed", "challenge_unarmed_heavy_250"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_unarmed_heavy_25", "unarmed.heavy", 25, () -> getConfig().challengeUnarmedHeavyReward);
    registerMilestone("challenge_unarmed_heavy_250", "unarmed.heavy", 250, () -> getConfig().challengeUnarmedHeavyReward * 3);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Player p)
        || !checkValidEntity(e.getEntity().getType())
        || isMelee(p.getInventory().getItemInMainHand())) {
      return;
    }

    shouldReturnForPlayer(p, e, () -> {
      if (e.getEntity().isDead()
          || e.getEntity().isInvulnerable()
          || p.isInvulnerable()) {
        return;
      }

      AdaptPlayer a = getPlayer(p);
      a.getData().addStat("unarmed.hits", 1);
      a.getData().addStat("unarmed.damage", e.getDamage());
      if (p.getFallDistance() > 0 && !p.isOnGround()) {
        a.getData().addStat("unarmed.critical", 1);
      }
      if (e.getDamage() > 6) {
        a.getData().addStat("unarmed.heavy", 1);
      }
      if (!cooldowns.isReady(p.getUniqueId(), getConfig().cooldownDelay)) {
        return;
      }
      cooldowns.mark(p.getUniqueId());
      xp(p, e.getEntity().getLocation(), getConfig().damageXPMultiplier * e.getDamage());
    });
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    if (e.getEntity().getKiller() == null) {
      return;
    }
    Player p = e.getEntity().getKiller();

    shouldReturnForPlayer(p, () -> {
      AdaptPlayer a = getPlayer(p);
      ItemStack hand = a.getPlayer().getInventory().getItemInMainHand();

      if (!isMelee(hand)) {
        a.getData().addStat("unarmed.kills", 1);
        if (e.getEntity() instanceof Boss) {
          fx(e.getEntity().getLocation().add(0, 1.2D, 0), FxPriority.TRANSITION)
              .burst(Particles.TOTEM, 16, 0.5D)
              .dustHelix(1.0D, 2.4D, 12, 0D, 1.0F)
              .chord(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.1F, Sound.ENTITY_PLAYER_LEVELUP, 0.5F, 1.4F);
        }
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
    String skillColor = "&e";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage XPMultiplier for the Unarmed skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageXPMultiplier = 4.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Unarmed skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 1250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Unarmed Reward for the Unarmed skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeUnarmedReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Unarmed Damage Reward for the Unarmed skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeUnarmedDmgReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Unarmed Kills Reward for the Unarmed skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeUnarmedKillsReward = 750;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Unarmed Critical Reward for the Unarmed skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeUnarmedCritReward = 750;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Unarmed Heavy Reward for the Unarmed skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeUnarmedHeavyReward = 750;
  }
}
