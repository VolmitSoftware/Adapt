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

package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.unarmed.UnarmedGlassCannon;
import com.volmit.adapt.content.adaptation.unarmed.UnarmedBatteringCharge;
import com.volmit.adapt.content.adaptation.unarmed.UnarmedComboChain;
import com.volmit.adapt.content.adaptation.unarmed.UnarmedPower;
import com.volmit.adapt.content.adaptation.unarmed.UnarmedSuckerPunch;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.CustomModel;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class SkillUnarmed extends SimpleSkill<SkillUnarmed.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillUnarmed() {
        super("unarmed", Localizer.dLocalize("skill.unarmed.icon"));
        registerConfiguration(Config.class);
        cooldowns = new HashMap<>();
        setColor(C.YELLOW);
        setDescription(Localizer.dLocalize("skill.unarmed.description"));
        setDisplayName(Localizer.dLocalize("skill.unarmed.name"));
        setInterval(2579);
        registerAdaptation(new UnarmedSuckerPunch());
        registerAdaptation(new UnarmedPower());
        registerAdaptation(new UnarmedGlassCannon());
        registerAdaptation(new UnarmedBatteringCharge());
        registerAdaptation(new UnarmedComboChain());
        setIcon(Material.FIRE_CHARGE);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.FIRE_CHARGE)
                .key("challenge_unarmed_100")
                .title(Localizer.dLocalize("advancement.challenge_unarmed_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_unarmed_100.description"))
                .model(CustomModel.get(Material.FIRE_CHARGE, "advancement", "unarmed", "challenge_unarmed_100"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.BLAZE_POWDER)
                        .key("challenge_unarmed_1k")
                        .title(Localizer.dLocalize("advancement.challenge_unarmed_1k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_unarmed_1k.description"))
                        .model(CustomModel.get(Material.BLAZE_POWDER, "advancement", "unarmed", "challenge_unarmed_1k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .child(AdaptAdvancement.builder()
                                .icon(Material.NETHER_STAR)
                                .key("challenge_unarmed_10k")
                                .title(Localizer.dLocalize("advancement.challenge_unarmed_10k.title"))
                                .description(Localizer.dLocalize("advancement.challenge_unarmed_10k.description"))
                                .model(CustomModel.get(Material.NETHER_STAR, "advancement", "unarmed", "challenge_unarmed_10k"))
                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                .build())
                        .build())
                .build());
        registerMilestone("challenge_unarmed_100", "unarmed.hits", 100, getConfig().challengeUnarmedReward);
        registerMilestone("challenge_unarmed_1k", "unarmed.hits", 1000, getConfig().challengeUnarmedReward * 2);
        registerMilestone("challenge_unarmed_10k", "unarmed.hits", 10000, getConfig().challengeUnarmedReward * 5);

        // Chain 2 - Unarmed Damage
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.FIRE_CHARGE)
                .key("challenge_unarmed_dmg_1k")
                .title(Localizer.dLocalize("advancement.challenge_unarmed_dmg_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_unarmed_dmg_1k.description"))
                .model(CustomModel.get(Material.FIRE_CHARGE, "advancement", "unarmed", "challenge_unarmed_dmg_1k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.BLAZE_ROD)
                        .key("challenge_unarmed_dmg_10k")
                        .title(Localizer.dLocalize("advancement.challenge_unarmed_dmg_10k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_unarmed_dmg_10k.description"))
                        .model(CustomModel.get(Material.BLAZE_ROD, "advancement", "unarmed", "challenge_unarmed_dmg_10k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_unarmed_dmg_1k", "unarmed.damage", 1000, getConfig().challengeUnarmedDmgReward);
        registerMilestone("challenge_unarmed_dmg_10k", "unarmed.damage", 10000, getConfig().challengeUnarmedDmgReward * 3);

        // Chain 3 - Unarmed Kills
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.ROTTEN_FLESH)
                .key("challenge_unarmed_kills_25")
                .title(Localizer.dLocalize("advancement.challenge_unarmed_kills_25.title"))
                .description(Localizer.dLocalize("advancement.challenge_unarmed_kills_25.description"))
                .model(CustomModel.get(Material.ROTTEN_FLESH, "advancement", "unarmed", "challenge_unarmed_kills_25"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.ZOMBIE_HEAD)
                        .key("challenge_unarmed_kills_250")
                        .title(Localizer.dLocalize("advancement.challenge_unarmed_kills_250.title"))
                        .description(Localizer.dLocalize("advancement.challenge_unarmed_kills_250.description"))
                        .model(CustomModel.get(Material.ZOMBIE_HEAD, "advancement", "unarmed", "challenge_unarmed_kills_250"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_unarmed_kills_25", "unarmed.kills", 25, getConfig().challengeUnarmedKillsReward);
        registerMilestone("challenge_unarmed_kills_250", "unarmed.kills", 250, getConfig().challengeUnarmedKillsReward * 3);

        // Chain 4 - Unarmed Criticals
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SUGAR)
                .key("challenge_unarmed_crit_25")
                .title(Localizer.dLocalize("advancement.challenge_unarmed_crit_25.title"))
                .description(Localizer.dLocalize("advancement.challenge_unarmed_crit_25.description"))
                .model(CustomModel.get(Material.SUGAR, "advancement", "unarmed", "challenge_unarmed_crit_25"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.FERMENTED_SPIDER_EYE)
                        .key("challenge_unarmed_crit_250")
                        .title(Localizer.dLocalize("advancement.challenge_unarmed_crit_250.title"))
                        .description(Localizer.dLocalize("advancement.challenge_unarmed_crit_250.description"))
                        .model(CustomModel.get(Material.FERMENTED_SPIDER_EYE, "advancement", "unarmed", "challenge_unarmed_crit_250"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_unarmed_crit_25", "unarmed.critical", 25, getConfig().challengeUnarmedCritReward);
        registerMilestone("challenge_unarmed_crit_250", "unarmed.critical", 250, getConfig().challengeUnarmedCritReward * 3);

        // Chain 5 - Unarmed Heavy Hits
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.TNT)
                .key("challenge_unarmed_heavy_25")
                .title(Localizer.dLocalize("advancement.challenge_unarmed_heavy_25.title"))
                .description(Localizer.dLocalize("advancement.challenge_unarmed_heavy_25.description"))
                .model(CustomModel.get(Material.TNT, "advancement", "unarmed", "challenge_unarmed_heavy_25"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.END_CRYSTAL)
                        .key("challenge_unarmed_heavy_250")
                        .title(Localizer.dLocalize("advancement.challenge_unarmed_heavy_250.title"))
                        .description(Localizer.dLocalize("advancement.challenge_unarmed_heavy_250.description"))
                        .model(CustomModel.get(Material.END_CRYSTAL, "advancement", "unarmed", "challenge_unarmed_heavy_250"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_unarmed_heavy_25", "unarmed.heavy", 25, getConfig().challengeUnarmedHeavyReward);
        registerMilestone("challenge_unarmed_heavy_250", "unarmed.heavy", 250, getConfig().challengeUnarmedHeavyReward * 3);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (!(e.getDamager() instanceof Player p)) {
            return;
        }

        shouldReturnForPlayer(p, e, () -> {
            if (e.getEntity().isDead()
                    || e.getEntity().isInvulnerable()
                    || p.isInvulnerable()) {
                return;
            }

            if (!checkValidEntity(e.getEntity().getType())) {
                return;
            }

            AdaptPlayer a = getPlayer(p);
            ItemStack hand = a.getPlayer().getInventory().getItemInMainHand();

            if (!isMelee(hand)) {
                a.getData().addStat("unarmed.hits", 1);
                a.getData().addStat("unarmed.damage", e.getDamage());
                if (p.getFallDistance() > 0 && !p.isOnGround()) {
                    a.getData().addStat("unarmed.critical", 1);
                }
                if (e.getDamage() > 6) {
                    a.getData().addStat("unarmed.heavy", 1);
                }
                Long cooldown = cooldowns.get(p);
                if (cooldown != null && cooldown + getConfig().cooldownDelay > System.currentTimeMillis())
                    return;
                cooldowns.put(p, System.currentTimeMillis());
                xp(a.getPlayer(), e.getEntity().getLocation(), getConfig().damageXPMultiplier * e.getDamage());
            }
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
            }
        });
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
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage XPMultiplier for the Unarmed skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageXPMultiplier = 4.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Unarmed skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long cooldownDelay = 1250;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Unarmed Reward for the Unarmed skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeUnarmedReward = 500;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Unarmed Damage Reward for the Unarmed skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeUnarmedDmgReward = 500;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Unarmed Kills Reward for the Unarmed skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeUnarmedKillsReward = 750;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Unarmed Critical Reward for the Unarmed skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeUnarmedCritReward = 750;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Unarmed Heavy Reward for the Unarmed skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeUnarmedHeavyReward = 750;
    }
}
