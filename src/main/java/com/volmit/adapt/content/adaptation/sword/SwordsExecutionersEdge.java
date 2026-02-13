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

package com.volmit.adapt.content.adaptation.sword;

import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class SwordsExecutionersEdge extends SimpleAdaptation<SwordsExecutionersEdge.Config> {
    public SwordsExecutionersEdge() {
        super("sword-executioners-edge");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("sword.executioners_edge.description"));
        setDisplayName(Localizer.dLocalize("sword.executioners_edge.name"));
        setIcon(Material.STONE_SWORD);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(1900);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.IRON_SWORD)
                .key("challenge_swords_execute_200")
                .title(Localizer.dLocalize("advancement.challenge_swords_execute_200.title"))
                .description(Localizer.dLocalize("advancement.challenge_swords_execute_200.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.NETHERITE_SWORD)
                        .key("challenge_swords_execute_2500")
                        .title(Localizer.dLocalize("advancement.challenge_swords_execute_2500.title"))
                        .description(Localizer.dLocalize("advancement.challenge_swords_execute_2500.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_swords_execute_200", "swords.executioners-edge.executions", 200, 400);
        registerMilestone("challenge_swords_execute_2500", "swords.executioners-edge.executions", 2500, 1500);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.NETHERITE_AXE)
                .key("challenge_swords_execute_5in10")
                .title(Localizer.dLocalize("advancement.challenge_swords_execute_5in10.title"))
                .description(Localizer.dLocalize("advancement.challenge_swords_execute_5in10.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getBonusDamage(level), 0) + C.GRAY + " " + Localizer.dLocalize("sword.executioners_edge.lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getThreshold(level), 0) + C.GRAY + " " + Localizer.dLocalize("sword.executioners_edge.lore2"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled() || !(e.getDamager() instanceof Player p) || !hasAdaptation(p) || !isSword(p.getInventory().getItemInMainHand()) || !(e.getEntity() instanceof LivingEntity target)) {
            return;
        }

        if (target instanceof Player victim) {
            if (!canPVP(p, victim.getLocation())) {
                return;
            }
        } else if (!canPVE(p, target.getLocation())) {
            return;
        }

        double maxHealth = getMaxHealth(target);
        if (maxHealth <= 0) {
            return;
        }

        double hpPercent = Math.max(0, target.getHealth() / maxHealth);
        double threshold = getThreshold(getLevel(p));
        if (hpPercent > threshold) {
            return;
        }

        double multiplier = 1D + getBonusDamage(getLevel(p));
        e.setDamage(e.getDamage() * multiplier);
        xp(p, e.getDamage() * getConfig().xpPerBuffedDamage);
        getPlayer(p).getData().addStat("swords.executioners-edge.executions", 1);

        // Special achievement: execute 5 in 10 seconds
        long now = System.currentTimeMillis();
        long windowStart = getStorageLong(p, "executeWindowStart", 0L);
        int windowCount = getStorageInt(p, "executeWindowCount", 0);
        if (now - windowStart > 10000L) {
            windowStart = now;
            windowCount = 1;
        } else {
            windowCount++;
        }
        setStorage(p, "executeWindowStart", windowStart);
        setStorage(p, "executeWindowCount", windowCount);
        if (windowCount >= 5 && AdaptConfig.get().isAdvancements() && !getPlayer(p).getData().isGranted("challenge_swords_execute_5in10")) {
            getPlayer(p).getAdvancementHandler().grant("challenge_swords_execute_5in10");
        }
    }

    private double getMaxHealth(LivingEntity entity) {
        AttributeInstance attr = entity.getAttribute(Attribute.MAX_HEALTH);
        return attr == null ? entity.getHealth() : attr.getValue();
    }

    private double getBonusDamage(int level) {
        return getConfig().bonusDamageBase + (getLevelPercent(level) * getConfig().bonusDamageFactor);
    }

    private double getThreshold(int level) {
        return Math.min(getConfig().maxThreshold, getConfig().thresholdBase + (getLevelPercent(level) * getConfig().thresholdFactor));
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
    @ConfigDescription("Sword strikes deal bonus damage to low-health targets.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.65;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bonus Damage Base for the Swords Executioners Edge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bonusDamageBase = 0.08;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bonus Damage Factor for the Swords Executioners Edge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bonusDamageFactor = 0.42;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Threshold Base for the Swords Executioners Edge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double thresholdBase = 0.22;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Threshold Factor for the Swords Executioners Edge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double thresholdFactor = 0.33;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Threshold for the Swords Executioners Edge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxThreshold = 0.65;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Buffed Damage for the Swords Executioners Edge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerBuffedDamage = 1.9;
    }
}
