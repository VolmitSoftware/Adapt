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

package com.volmit.adapt.content.adaptation.ranged;

import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

public class RangedForce extends SimpleAdaptation<RangedForce.Config> {

    public RangedForce() {
        super("ranged-force");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("ranged.force_shot.description"));
        setDisplayName(Localizer.dLocalize("ranged.force_shot.name"));
        setIcon(Material.ARROW);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(4900);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SPECTRAL_ARROW)
                .key("challenge_force_30")
                .title(Localizer.dLocalize("ranged.force_shot.advancementname"))
                .description(Localizer.dLocalize("ranged.force_shot.advancementlore"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SPECTRAL_ARROW)
                .key("challenge_ranged_force_500")
                .title(Localizer.dLocalize("advancement.challenge_ranged_force_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_ranged_force_500.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_ranged_force_500").goal(500).stat("ranged.force.long-range-hits").reward(500).build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getSpeed(getLevelPercent(level)), 0) + C.GRAY + " " + Localizer.dLocalize("ranged.force_shot.lore1"));
    }

    private double getSpeed(double factor) {
        return (factor * getConfig().speedFactor);
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getDamager() instanceof Projectile r && r.getShooter() instanceof Player p && hasAdaptation(p)) {
            Location a = e.getEntity().getLocation().clone();
            Location b = p.getLocation().clone();
            a.setY(0);
            b.setY(0);
            xp(p, 5);
            double distSq = a.distanceSquared(b);

            if (distSq > 10 && AdaptConfig.get().isAdvancements() && !getPlayer(p).getData().isGranted("challenge_force_30")) {
                getPlayer(p).getAdvancementHandler().grant("challenge_force_30");
                getSkill().xp(p, getConfig().challengeRewardLongShotReward);
            }

            if (distSq > 900) {
                getPlayer(p).getData().addStat("ranged.force.long-range-hits", 1);
            }
        }
    }

    @EventHandler
    public void on(ProjectileLaunchEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getEntity().getShooter() instanceof Player p) {
            if (hasAdaptation(p)) {
                double factor = getLevelPercent(p);
                e.getEntity().setVelocity(e.getEntity().getVelocity().clone().multiply(1 + getSpeed(factor)));
                SoundPlayer spw = SoundPlayer.of(e.getEntity().getWorld());
                spw.play(e.getEntity().getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.5f + ((float) factor * 0.25f), 0.7f + (float) (factor / 2f));
            }
        }
    }

    @Override
    public void onTick() {

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    @ConfigDescription("Shoot projectiles further and faster.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.225;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Reward Long Shot Reward for the Ranged Force adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeRewardLongShotReward = 2000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Speed Factor for the Ranged Force adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double speedFactor = 1.135;
    }
}
