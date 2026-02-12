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
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.ranged.*;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.CustomModel;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SkillRanged extends SimpleSkill<SkillRanged.Config> {
    private final Map<Player, Long> cooldowns;

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
        setIcon(Material.CROSSBOW);
        cooldowns = new HashMap<>();
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.ARROW)
                .key("challenge_ranged_100")
                .title(Localizer.dLocalize("advancement.challenge_ranged_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_ranged_100.description"))
                .model(CustomModel.get(Material.ARROW, "advancement", "ranged", "challenge_ranged_100"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.SPECTRAL_ARROW)
                        .key("challenge_ranged_1k")
                        .title(Localizer.dLocalize("advancement.challenge_ranged_1k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_ranged_1k.description"))
                        .model(CustomModel.get(Material.SPECTRAL_ARROW, "advancement", "ranged", "challenge_ranged_1k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .child(AdaptAdvancement.builder()
                                .icon(Material.CROSSBOW)
                                .key("challenge_ranged_10k")
                                .title(Localizer.dLocalize("advancement.challenge_ranged_10k.title"))
                                .description(Localizer.dLocalize("advancement.challenge_ranged_10k.description"))
                                .model(CustomModel.get(Material.CROSSBOW, "advancement", "ranged", "challenge_ranged_10k"))
                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                .build())
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_ranged_100").goal(100).stat("ranged.shotsfired").reward(getConfig().challengeRangedReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_ranged_1k").goal(1000).stat("ranged.shotsfired").reward(getConfig().challengeRangedReward * 2).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_ranged_10k").goal(10000).stat("ranged.shotsfired").reward(getConfig().challengeRangedReward * 5).build());

        // Chain 2 - Ranged Damage
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.BOW)
                .key("challenge_ranged_dmg_1k")
                .title(Localizer.dLocalize("advancement.challenge_ranged_dmg_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_ranged_dmg_1k.description"))
                .model(CustomModel.get(Material.BOW, "advancement", "ranged", "challenge_ranged_dmg_1k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.CROSSBOW)
                        .key("challenge_ranged_dmg_10k")
                        .title(Localizer.dLocalize("advancement.challenge_ranged_dmg_10k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_ranged_dmg_10k.description"))
                        .model(CustomModel.get(Material.CROSSBOW, "advancement", "ranged", "challenge_ranged_dmg_10k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_ranged_dmg_1k").goal(1000).stat("ranged.damage").reward(getConfig().challengeRangedDmgReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_ranged_dmg_10k").goal(10000).stat("ranged.damage").reward(getConfig().challengeRangedDmgReward * 3).build());

        // Chain 3 - Ranged Distance
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.ARROW)
                .key("challenge_ranged_dist_5k")
                .title(Localizer.dLocalize("advancement.challenge_ranged_dist_5k.title"))
                .description(Localizer.dLocalize("advancement.challenge_ranged_dist_5k.description"))
                .model(CustomModel.get(Material.ARROW, "advancement", "ranged", "challenge_ranged_dist_5k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.SPECTRAL_ARROW)
                        .key("challenge_ranged_dist_50k")
                        .title(Localizer.dLocalize("advancement.challenge_ranged_dist_50k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_ranged_dist_50k.description"))
                        .model(CustomModel.get(Material.SPECTRAL_ARROW, "advancement", "ranged", "challenge_ranged_dist_50k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_ranged_dist_5k").goal(5000).stat("ranged.distance").reward(getConfig().challengeRangedDistReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_ranged_dist_50k").goal(50000).stat("ranged.distance").reward(getConfig().challengeRangedDistReward * 3).build());

        // Chain 4 - Ranged Kills
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.TIPPED_ARROW)
                .key("challenge_ranged_kills_50")
                .title(Localizer.dLocalize("advancement.challenge_ranged_kills_50.title"))
                .description(Localizer.dLocalize("advancement.challenge_ranged_kills_50.description"))
                .model(CustomModel.get(Material.TIPPED_ARROW, "advancement", "ranged", "challenge_ranged_kills_50"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.TARGET)
                        .key("challenge_ranged_kills_500")
                        .title(Localizer.dLocalize("advancement.challenge_ranged_kills_500.title"))
                        .description(Localizer.dLocalize("advancement.challenge_ranged_kills_500.description"))
                        .model(CustomModel.get(Material.TARGET, "advancement", "ranged", "challenge_ranged_kills_500"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_ranged_kills_50").goal(50).stat("ranged.kills").reward(getConfig().challengeRangedKillsReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_ranged_kills_500").goal(500).stat("ranged.kills").reward(getConfig().challengeRangedKillsReward * 3).build());

        // Chain 5 - Longshots
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SPYGLASS)
                .key("challenge_longshot_25")
                .title(Localizer.dLocalize("advancement.challenge_longshot_25.title"))
                .description(Localizer.dLocalize("advancement.challenge_longshot_25.description"))
                .model(CustomModel.get(Material.SPYGLASS, "advancement", "ranged", "challenge_longshot_25"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.ENDER_EYE)
                        .key("challenge_longshot_250")
                        .title(Localizer.dLocalize("advancement.challenge_longshot_250.title"))
                        .description(Localizer.dLocalize("advancement.challenge_longshot_250.description"))
                        .model(CustomModel.get(Material.ENDER_EYE, "advancement", "ranged", "challenge_longshot_250"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_longshot_25").goal(25).stat("ranged.longshots").reward(getConfig().challengeRangedLongshotReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_longshot_250").goal(250).stat("ranged.longshots").reward(getConfig().challengeRangedLongshotReward * 3).build());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(ProjectileLaunchEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (!(e.getEntity().getShooter() instanceof Player p)) {
            return;
        }
        shouldReturnForPlayer(p, e, () -> {
            if (e.getEntity() instanceof Snowball || e.getEntity().getType().name().toLowerCase(Locale.ROOT).contains("hook")) {
                return; // Ignore snowballs and fishing hooks
            }

            getPlayer(p).getData().addStat("ranged.shotsfired", 1);
            getPlayer(p).getData().addStat("ranged.shotsfired." + e.getEntity().getType().name().toLowerCase(Locale.ROOT), 1);
            Long cooldown = cooldowns.get(p);
            if (cooldown != null && cooldown + getConfig().cooldownDelay > System.currentTimeMillis())
                return;
            cooldowns.put(p, System.currentTimeMillis());
            xp(p, getConfig().shootXP);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (!(e.getDamager() instanceof Projectile) || !(((Projectile) e.getDamager()).getShooter() instanceof Player p) || !checkValidEntity(e.getEntity().getType())) {
            return;
        }
        shouldReturnForPlayer(p, e, () -> {
            if (e.getEntity() instanceof Snowball || e.getEntity() instanceof FishHook) {
                return; // Ignore snowballs and fishing hooks
            }
            if (e.getEntity().getLocation().getWorld().equals(p.getLocation().getWorld())) {
                double distance = e.getEntity().getLocation().distance(p.getLocation());
                getPlayer(p).getData().addStat("ranged.distance", distance);
                getPlayer(p).getData().addStat("ranged.distance." + e.getDamager().getType().name().toLowerCase(Locale.ROOT), distance);
                if (distance > 30) {
                    getPlayer(p).getData().addStat("ranged.longshots", 1);
                }
            }
            getPlayer(p).getData().addStat("ranged.damage", e.getDamage());
            getPlayer(p).getData().addStat("ranged.damage." + e.getDamager().getType().name().toLowerCase(Locale.ROOT), e.getDamage());
            Long cooldown = cooldowns.get(p);
            if (cooldown != null && cooldown + getConfig().cooldownDelay > System.currentTimeMillis())
                return;
            cooldowns.put(p, System.currentTimeMillis());
            xp(p, e.getEntity().getLocation(), (getConfig().hitDamageXPMultiplier * e.getDamage()) + (e.getEntity().getLocation().distance(p.getLocation()) * getConfig().hitDistanceXPMultiplier));

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
                getPlayer(p).getData().addStat("ranged.kills", 1);
            }
        });
    }

    @Override
    public void onTick() {
        if (!this.isEnabled()) {
            return;
        }
        for (Player i : Bukkit.getOnlinePlayers()) {
            shouldReturnForPlayer(i, () -> checkStatTrackers(getPlayer(i)));
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Shoot XP for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double shootXP = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long cooldownDelay = 1250;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Hit Damage XPMultiplier for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double hitDamageXPMultiplier = 1.75;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Hit Distance XPMultiplier for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double hitDistanceXPMultiplier = 1.2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Ranged Reward for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeRangedReward = 500;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Ranged Damage Reward for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeRangedDmgReward = 500;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Ranged Distance Reward for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeRangedDistReward = 500;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Ranged Kills Reward for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeRangedKillsReward = 500;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Ranged Longshot Reward for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeRangedLongshotReward = 500;
    }
}
