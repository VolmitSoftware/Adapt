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
import com.volmit.adapt.content.adaptation.taming.TamingDamage;
import com.volmit.adapt.content.adaptation.taming.TamingHealthBoost;
import com.volmit.adapt.content.adaptation.taming.TamingHealthRegeneration;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.CustomModel;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;

public class SkillTaming extends SimpleSkill<SkillTaming.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillTaming() {
        super("taming", Localizer.dLocalize("skill", "taming", "icon"));
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("skill", "taming", "description"));
        setDisplayName(Localizer.dLocalize("skill", "taming", "name"));
        setColor(C.GOLD);
        setInterval(3480);
        setIcon(Material.LEAD);
        cooldowns = new HashMap<>();
        registerAdaptation(new TamingHealthBoost());
        registerAdaptation(new TamingDamage());
        registerAdaptation(new TamingHealthRegeneration());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.LEAD)
                .key("challenge_taming_10")
                .title(Localizer.dLocalize("advancement", "challenge_taming_10", "title"))
                .description(Localizer.dLocalize("advancement", "challenge_taming_10", "description"))
                .model(CustomModel.get(Material.LEAD, "advancement", "taming", "challenge_taming_10"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.NAME_TAG)
                        .key("challenge_taming_50")
                        .title(Localizer.dLocalize("advancement", "challenge_taming_50", "title"))
                        .description(Localizer.dLocalize("advancement", "challenge_taming_50", "description"))
                        .model(CustomModel.get(Material.NAME_TAG, "advancement", "taming", "challenge_taming_50"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .child(AdaptAdvancement.builder()
                                .icon(Material.GOLDEN_APPLE)
                                .key("challenge_taming_500")
                                .title(Localizer.dLocalize("advancement", "challenge_taming_500", "title"))
                                .description(Localizer.dLocalize("advancement", "challenge_taming_500", "description"))
                                .model(CustomModel.get(Material.GOLDEN_APPLE, "advancement", "taming", "challenge_taming_500"))
                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                .build())
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_taming_10").goal(10).stat("taming.bred").reward(getConfig().challengeTamingReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_taming_50").goal(50).stat("taming.bred").reward(getConfig().challengeTamingReward * 2).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_taming_500").goal(500).stat("taming.bred").reward(getConfig().challengeTamingReward * 5).build());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityBreedEvent e) {
        if (e.isCancelled()) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            shouldReturnForPlayer(p, e, () -> {
                if (p.getWorld() == e.getEntity().getWorld() && p.getLocation().distance(e.getEntity().getLocation()) <= 15) {
                    getPlayer(p).getData().addStat("taming.bred", 1);
                    if (!isOnCooldown(p)) {
                        setCooldown(p);
                        xp(p, getConfig().tameXpBase);
                    }
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getDamager() instanceof Tameable tameable && tameable.isTamed() && tameable.getOwner() instanceof Player p) {
            shouldReturnForPlayer(p, e, () -> {
                if (!isOnCooldown(p)) {
                    setCooldown(p);
                    xp(p, e.getEntity().getLocation(), e.getDamage() * getConfig().tameDamageXPMultiplier);
                }
            });
        }
    }

    private boolean isOnCooldown(Player p) {
        Long cooldown = cooldowns.get(p);
        return cooldown != null && cooldown + getConfig().cooldownDelay > System.currentTimeMillis();
    }

    private void setCooldown(Player p) {
        cooldowns.put(p, System.currentTimeMillis());
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
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Tame Xp Base for the Taming skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double tameXpBase = 30;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Taming skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long cooldownDelay = 2250;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Tame Damage XPMultiplier for the Taming skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double tameDamageXPMultiplier = 7.85;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Taming Reward for the Taming skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeTamingReward = 500;
    }
}
