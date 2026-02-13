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

package com.volmit.adapt.content.adaptation.herbalism;

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
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class HerbalismRootedFooting extends SimpleAdaptation<HerbalismRootedFooting.Config> {
    public HerbalismRootedFooting() {
        super("herbalism-rooted-footing");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("herbalism.rooted_footing.description"));
        setDisplayName(Localizer.dLocalize("herbalism.rooted_footing.name"));
        setIcon(Material.FARMLAND);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(2050);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.FARMLAND)
                .key("challenge_herbalism_rooted_500")
                .title(Localizer.dLocalize("advancement.challenge_herbalism_rooted_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_herbalism_rooted_500.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_herbalism_rooted_500", "herbalism.rooted-footing.farmland-saved", 500, 300);
    }

    @Override
    public void addStats(int level, Element v) {
        double absorb = getFallAbsorb(level);
        v.addLore(C.GREEN + "+ " + Form.pc(absorb, 0) + C.GRAY + " " + Localizer.dLocalize("herbalism.rooted_footing.lore1"));
        v.addLore(C.YELLOW + "* " + getConfig().foodPerDamage + C.GRAY + " " + Localizer.dLocalize("herbalism.rooted_footing.lore2"));
        v.addLore(C.GREEN + "+ " + Localizer.dLocalize("herbalism.rooted_footing.lore3"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerInteractEvent e) {
        if (e.isCancelled()) {
            return;
        }

        if (e.getAction() != Action.PHYSICAL || e.getClickedBlock() == null || !(e.getPlayer() instanceof Player p)) {
            return;
        }

        if (!hasAdaptation(p)) {
            return;
        }

        if (e.getClickedBlock().getType() == Material.FARMLAND) {
            e.setCancelled(true);
            getPlayer(p).getData().addStat("herbalism.rooted-footing.farmland-saved", 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void on(EntityDamageEvent e) {
        if (e.isCancelled() || !(e.getEntity() instanceof Player p) || e.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }

        if (!hasAdaptation(p) || !isNatureGround(p)) {
            return;
        }

        double absorbCap = e.getDamage() * getFallAbsorb(getLevel(p));
        int foodRequired = (int) Math.ceil(absorbCap * getConfig().foodPerDamage);
        if (foodRequired <= 0 || p.getFoodLevel() <= 0) {
            return;
        }

        int usableFood = Math.min(p.getFoodLevel(), foodRequired);
        double absorbed = usableFood / getConfig().foodPerDamage;
        if (absorbed <= 0) {
            return;
        }

        p.setFoodLevel(Math.max(0, p.getFoodLevel() - usableFood));
        e.setDamage(Math.max(0, e.getDamage() - absorbed));
        if (e.getDamage() <= 0.01) {
            e.setCancelled(true);
        }
    }

    private boolean isNatureGround(Player p) {
        Block under = p.getLocation().clone().add(0, -1, 0).getBlock();
        Material type = under.getType();
        return type == Material.FARMLAND
                || type == Material.GRASS_BLOCK
                || type == Material.MOSS_BLOCK
                || type == Material.MYCELIUM
                || type == Material.DIRT
                || type == Material.ROOTED_DIRT;
    }

    private double getFallAbsorb(int level) {
        return Math.min(getConfig().maxAbsorbPercent, getConfig().absorbBase + (getLevelPercent(level) * getConfig().absorbFactor));
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
    @ConfigDescription("Protect farmland and convert fall damage into hunger on natural ground.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.65;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Absorb Base for the Herbalism Rooted Footing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double absorbBase = 0.28;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Absorb Factor for the Herbalism Rooted Footing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double absorbFactor = 0.12;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Absorb Percent for the Herbalism Rooted Footing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxAbsorbPercent = 0.45;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Food Per Damage for the Herbalism Rooted Footing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double foodPerDamage = 1.8;
    }
}
