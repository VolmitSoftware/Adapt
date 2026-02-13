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
import com.volmit.adapt.content.item.ItemListings;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.reflect.registries.Materials;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public class HerbalismLuck extends SimpleAdaptation<HerbalismLuck.Config> {

    public HerbalismLuck() {
        super("herbalism-luck");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("herbalism.luck.description"));
        setDisplayName(Localizer.dLocalize("herbalism.luck.name"));
        setIcon(Material.EMERALD);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(8121);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.RABBIT_FOOT)
                .key("challenge_herbalism_luck_100")
                .title(Localizer.dLocalize("advancement.challenge_herbalism_luck_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_herbalism_luck_100.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.EMERALD)
                        .key("challenge_herbalism_luck_2500")
                        .title(Localizer.dLocalize("advancement.challenge_herbalism_luck_2500.title"))
                        .description(Localizer.dLocalize("advancement.challenge_herbalism_luck_2500.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_herbalism_luck_100", "herbalism.luck.lucky-drops", 100, 300);
        registerMilestone("challenge_herbalism_luck_2500", "herbalism.luck.lucky-drops", 2500, 1000);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + C.GRAY + Localizer.dLocalize("herbalism.luck.lore0"));
        v.addLore(C.GREEN + "+ (" + (getEffectiveness(level)) + C.GRAY + "%) + " + Localizer.dLocalize("herbalism.luck.lore1"));
        v.addLore(C.GREEN + "+ (" + (getEffectiveness(level)) + C.GRAY + "%) + " + Localizer.dLocalize("herbalism.luck.lore2"));
    }

    private double getEffectiveness(double factor) {
        return Math.min(getConfig().highChance, factor * factor + getConfig().lowChance);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void on(BlockDropItemEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        if (!hasAdaptation(p)) {
            return;
        }

        Block broken = e.getBlock();
        if (broken.getType() == Materials.GRASS || broken.getType() == Material.TALL_GRASS) {
            var d = ThreadLocalRandom.current().nextDouble(100D);
            Material m = ItemListings.getHerbalLuckSeeds().getRandom();
            if (d < getEffectiveness(getLevel(p))) {
                xp(p, 100);
                getPlayer(p).getData().addStat("herbalism.luck.lucky-drops", 1);
                ItemStack luckDrop = new ItemStack(m, 1);
                e.getBlock().getWorld().dropItem(e.getBlock().getLocation(), luckDrop);
            }
        }

        if (ItemListings.getFlowers().contains(broken.getType())) {
            var d = ThreadLocalRandom.current().nextDouble(100D);
            Material m = ItemListings.getHerbalLuckFood().getRandom();
            if (d < getEffectiveness(getLevel(p))) {
                xp(p, 100);
                getPlayer(p).getData().addStat("herbalism.luck.lucky-drops", 1);
                ItemStack luckDrop = new ItemStack(m, 1);
                e.getBlock().getWorld().dropItem(e.getBlock().getLocation(), luckDrop);
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
    @ConfigDescription("Breaking Grass or Flowers has a chance to drop random items.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.75;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Low Chance for the Herbalism Luck adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double lowChance = 0.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls High Chance for the Herbalism Luck adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double highChance = 90;
    }
}
