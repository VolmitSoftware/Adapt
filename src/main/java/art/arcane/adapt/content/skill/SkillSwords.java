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
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.sword.*;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.CustomModel;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

public class SkillSwords extends SimpleSkill<SkillSwords.Config> {
  private final Cooldowns cooldowns = cooldowns();

  public SkillSwords() {
    super("swords", Localizer.dLocalize("skill.swords.icon"));
    registerConfiguration(Config.class);
    setColor(C.YELLOW);
    setDescription(Localizer.dLocalize("skill.swords.description"));
    setDisplayName(Localizer.dLocalize("skill.swords.name"));
    setInterval(2150);
    setIcon(Material.DIAMOND_SWORD);
    registerAdaptation(new SwordsMachete());
    registerAdaptation(new SwordsPoisonedBlade());
    registerAdaptation(new SwordsBloodyBlade());
    registerAdaptation(new SwordsDualWield());
    registerAdaptation(new SwordsExecutionersEdge());
    registerAdaptation(new SwordsRiposteWindow());
    registerAdaptation(new SwordsCrimsonCyclone());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.WOODEN_SWORD)
        .key("challenge_sword_100")
        .model(CustomModel.get(Material.WOODEN_SWORD, "advancement", "swords", "challenge_sword_100"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.IRON_SWORD)
            .key("challenge_sword_1k")
            .model(CustomModel.get(Material.IRON_SWORD, "advancement", "swords", "challenge_sword_1k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .child(AdaptAdvancement.builder()
                .icon(Material.DIAMOND_SWORD)
                .key("challenge_sword_10k")
                .model(CustomModel.get(Material.DIAMOND_SWORD, "advancement", "swords", "challenge_sword_10k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build())
            .build())
        .build());
    registerMilestone("challenge_sword_100", "sword.hits", 100, getConfig().challengeSwordReward);
    registerMilestone("challenge_sword_1k", "sword.hits", 1000, getConfig().challengeSwordReward * 2);
    registerMilestone("challenge_sword_10k", "sword.hits", 10000, getConfig().challengeSwordReward * 5);

    // Chain 2 - sword.damage
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GOLDEN_SWORD)
        .key("challenge_sword_dmg_1k")
        .model(CustomModel.get(Material.GOLDEN_SWORD, "advancement", "swords", "challenge_sword_dmg_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_SWORD)
            .key("challenge_sword_dmg_10k")
            .model(CustomModel.get(Material.NETHERITE_SWORD, "advancement", "swords", "challenge_sword_dmg_10k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_sword_dmg_1k", "sword.damage", 1000, getConfig().challengeSwordDmgReward);
    registerMilestone("challenge_sword_dmg_10k", "sword.damage", 10000, getConfig().challengeSwordDmgReward * 3);

    // Chain 3 - sword.kills
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_SWORD)
        .key("challenge_sword_kills_50")
        .model(CustomModel.get(Material.IRON_SWORD, "advancement", "swords", "challenge_sword_kills_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_SWORD)
            .key("challenge_sword_kills_500")
            .model(CustomModel.get(Material.DIAMOND_SWORD, "advancement", "swords", "challenge_sword_kills_500"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_sword_kills_50", "sword.kills", 50, getConfig().challengeSwordKillsReward);
    registerMilestone("challenge_sword_kills_500", "sword.kills", 500, getConfig().challengeSwordKillsReward * 3);

    // Chain 4 - sword.critical
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GOLDEN_SWORD)
        .key("challenge_sword_crit_50")
        .model(CustomModel.get(Material.GOLDEN_SWORD, "advancement", "swords", "challenge_sword_crit_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_SWORD)
            .key("challenge_sword_crit_500")
            .model(CustomModel.get(Material.NETHERITE_SWORD, "advancement", "swords", "challenge_sword_crit_500"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_sword_crit_50", "sword.critical", 50, getConfig().challengeSwordCritReward);
    registerMilestone("challenge_sword_crit_500", "sword.critical", 500, getConfig().challengeSwordCritReward * 3);

    // Chain 5 - sword.heavy.hits
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DIAMOND_SWORD)
        .key("challenge_sword_heavy_25")
        .model(CustomModel.get(Material.DIAMOND_SWORD, "advancement", "swords", "challenge_sword_heavy_25"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_SWORD)
            .key("challenge_sword_heavy_250")
            .model(CustomModel.get(Material.NETHERITE_SWORD, "advancement", "swords", "challenge_sword_heavy_250"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_sword_heavy_25", "sword.heavy.hits", 25, getConfig().challengeSwordHeavyReward);
    registerMilestone("challenge_sword_heavy_250", "sword.heavy.hits", 250, getConfig().challengeSwordHeavyReward * 3);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (e.getDamager() instanceof Player p && checkValidEntity(e.getEntity().getType())) {
      shouldReturnForPlayer(p, e, () -> {
        AdaptPlayer a = getPlayer(p);
        ItemStack hand = a.getPlayer().getInventory().getItemInMainHand();
        if (isSword(hand)) {
          addStat(p, "sword.hits", 1);
          addStat(p, "sword.damage", e.getDamage());
          boolean crit = p.getFallDistance() > 0 && !p.isOnGround();
          boolean heavy = e.getDamage() > 8;
          if (crit) {
            addStat(p, "sword.critical", 1);
          }
          if (heavy) {
            addStat(p, "sword.heavy.hits", 1);
          }
          if (!isOnCooldown(p)) {
            setCooldown(p);
            xp(a.getPlayer(), e.getEntity().getLocation(), getConfig().damageXPMultiplier * e.getDamage());
            FxEmitter pulse = fx(e.getEntity().getLocation().add(0, 1, 0), FxPriority.AMBIENT)
                .particle(Particle.CRIT, heavy ? 8 : (crit ? 5 : 3), 0, 0, 0, 0.25D, 0.05D)
                .sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.2F, 1.4F);
            if (heavy) {
              pulse.sound(Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.35F, 0.9F);
            }
          }
        }
      });
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    if (e.getEntity().getKiller() == null) {
      return;
    }
    Player p = e.getEntity().getKiller();
    shouldReturnForPlayer(p, () -> {
      ItemStack hand = p.getInventory().getItemInMainHand();
      if (isSword(hand)) {
        addStat(p, "sword.kills", 1);
      }
    });
  }

  private boolean isOnCooldown(Player p) {
    return !cooldowns.isReady(p.getUniqueId(), getConfig().cooldownDelay);
  }

  private void setCooldown(Player p) {
    cooldowns.mark(p.getUniqueId());
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
    String skillColor = "&e";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Swords skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 1250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage XPMultiplier for the Swords skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageXPMultiplier = 4.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Sword Reward for the Swords skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSwordReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Sword Damage Reward for the Swords skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSwordDmgReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Sword Kills Reward for the Swords skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSwordKillsReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Sword Critical Reward for the Swords skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSwordCritReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Sword Heavy Hits Reward for the Swords skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSwordHeavyReward = 500;
  }
}
