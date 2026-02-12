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

package com.volmit.adapt.content.adaptation.enchanting;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;

public class EnchantingBookshelfAttunement extends SimpleAdaptation<EnchantingBookshelfAttunement.Config> {
    public EnchantingBookshelfAttunement() {
        super("enchanting-bookshelf-attunement");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("enchanting.bookshelf_attunement.description"));
        setDisplayName(Localizer.dLocalize("enchanting.bookshelf_attunement.name"));
        setIcon(Material.BOOKSHELF);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(1400);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.BOOKSHELF)
                .key("challenge_enchanting_bookshelf_100")
                .title(Localizer.dLocalize("advancement.challenge_enchanting_bookshelf_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_enchanting_bookshelf_100.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_enchanting_bookshelf_100").goal(100).stat("enchanting.bookshelf-attunement.enchants-boosted").reward(400).build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + getVirtualPower(level) + C.GRAY + " " + Localizer.dLocalize("enchanting.bookshelf_attunement.lore1"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PrepareItemEnchantEvent e) {
        Player p = e.getEnchanter();
        if (!hasAdaptation(p)) {
            return;
        }

        int power = getVirtualPower(getLevel(p));
        EnchantmentOffer[] offers = e.getOffers();
        if (offers == null) {
            return;
        }

        boolean boosted = false;
        for (EnchantmentOffer offer : offers) {
            if (offer == null) {
                continue;
            }

            int newCost = Math.min(30, offer.getCost() + power);
            int newLevel = Math.min(offer.getEnchantment().getMaxLevel(), offer.getEnchantmentLevel() + Math.max(0, power / 3));
            offer.setCost(newCost);
            offer.setEnchantmentLevel(Math.max(1, newLevel));
            boosted = true;
        }
        if (boosted) {
            getPlayer(p).getData().addStat("enchanting.bookshelf-attunement.enchants-boosted", 1);
        }
    }

    private int getVirtualPower(int level) {
        return Math.max(1, (int) Math.round(getConfig().powerBase + (getLevelPercent(level) * getConfig().powerFactor)));
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
    @ConfigDescription("Gain virtual bookshelf power to improve enchanting table offer quality.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Power Base for the Enchanting Bookshelf Attunement adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double powerBase = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Power Factor for the Enchanting Bookshelf Attunement adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double powerFactor = 5;
    }
}
