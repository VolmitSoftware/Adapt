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
import org.bukkit.event.entity.ProjectileLaunchEvent;

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
                getPlayer(p).getData().addStat("ranged.distance", e.getEntity().getLocation().distance(p.getLocation()));
                getPlayer(p).getData().addStat("ranged.distance." + e.getDamager().getType().name().toLowerCase(Locale.ROOT), e.getEntity().getLocation().distance(p.getLocation()));
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
        double hitDamageXPMultiplier = 2.125;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Hit Distance XPMultiplier for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double hitDistanceXPMultiplier = 1.7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Ranged Reward for the Ranged skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeRangedReward = 500;
    }
}
