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

import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.ranged.*;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
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
        super("ranged", Localizer.dLocalize("skill", "ranged", "icon"));
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("skill", "ranged", "description"));
        setDisplayName(Localizer.dLocalize("skill", "ranged", "name"));
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
                .icon(Material.BOW)
                .key("challenge_ranged_1k")
                .title(Localizer.dLocalize("advancement", "challenge_ranged_1k", "title"))
                .description(Localizer.dLocalize("advancement", "challenge_ranged_1k", "description"))
                .frame(AdvancementFrameType.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.CROSSBOW)
                        .key("challenge_ranged_5k")
                        .title(Localizer.dLocalize("advancement", "challenge_ranged_5k", "title"))
                        .description(Localizer.dLocalize("advancement", "challenge_ranged_5k", "description"))
                        .frame(AdvancementFrameType.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .child(AdaptAdvancement.builder()
                                .icon(Material.TIPPED_ARROW)
                                .key("challenge_ranged_50k")
                                .title(Localizer.dLocalize("advancement", "challenge_ranged_50k", "title"))
                                .description(Localizer.dLocalize("advancement", "challenge_ranged_50k", "description"))
                                .frame(AdvancementFrameType.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                .child(AdaptAdvancement.builder()
                                        .icon(Material.SPECTRAL_ARROW)
                                        .key("challenge_ranged_500k")
                                        .title(Localizer.dLocalize("advancement", "challenge_ranged_500k", "title"))
                                        .description(Localizer.dLocalize("advancement", "challenge_ranged_500k", "description"))
                                        .frame(AdvancementFrameType.CHALLENGE)
                                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                                        .build())
                                .build())
                        .build())
                .build());

        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_ranged_1k").goal(1000).stat("ranged.shotsfired").reward(getConfig().challengeRanged1kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_ranged_5k").goal(5000).stat("ranged.shotsfired").reward(getConfig().challengeRanged5kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_ranged_50k").goal(50000).stat("ranged.shotsfired").reward(getConfig().challengeRanged50kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_ranged_500k").goal(500000).stat("ranged.shotsfired").reward(getConfig().challengeRanged500kReward).build());
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

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        double shootXP = 5;
        long cooldownDelay = 1250;
        double hitDamageXPMultiplier = 2.125;
        double hitDistanceXPMultiplier = 1.7;
        double challengeRanged1kReward = 1000;
        double challengeRanged5kReward = 5000;
        double challengeRanged50kReward = 50000;
        double challengeRanged500kReward = 500000;
    }
}
