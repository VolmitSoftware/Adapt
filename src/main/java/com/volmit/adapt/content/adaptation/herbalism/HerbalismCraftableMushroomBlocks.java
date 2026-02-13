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
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.api.recipe.MaterialChar;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class HerbalismCraftableMushroomBlocks extends SimpleAdaptation<HerbalismCraftableMushroomBlocks.Config> {

    public HerbalismCraftableMushroomBlocks() {
        super("herbalism-mushroom-blocks");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("herbalism.mushroom_blocks.description"));
        setDisplayName(Localizer.dLocalize("herbalism.mushroom_blocks.name"));
        setIcon(Material.BROWN_MUSHROOM_BLOCK);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(17772);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        registerRecipe(AdaptRecipe.shaped()
                .key("herbalism-redmushblock")
                .ingredient(new MaterialChar('I', Material.RED_MUSHROOM))
                .shapes(List.of(
                        "II",
                        "II"))
                .result(new ItemStack(Material.RED_MUSHROOM_BLOCK, 1))
                .build());
        registerRecipe(AdaptRecipe.shaped()
                .key("herbalism-brownmushblock")
                .ingredient(new MaterialChar('I', Material.BROWN_MUSHROOM))
                .shapes(List.of(
                        "II",
                        "II"))
                .result(new ItemStack(Material.BROWN_MUSHROOM_BLOCK, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("herbalism-mushstemred")
                .ingredient(Material.RED_MUSHROOM_BLOCK)
                .result(new ItemStack(Material.MUSHROOM_STEM, 1))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("herbalism-mushstembrown")
                .ingredient(Material.BROWN_MUSHROOM_BLOCK)
                .result(new ItemStack(Material.MUSHROOM_STEM, 1))
                .build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.RED_MUSHROOM_BLOCK)
                .key("challenge_herbalism_mushroom_100")
                .title(Localizer.dLocalize("advancement.challenge_herbalism_mushroom_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_herbalism_mushroom_100.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_herbalism_mushroom_100", "herbalism.mushroom-blocks.crafted", 100, 300);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + C.GRAY + Localizer.dLocalize("herbalism.mushroom_blocks.lore1"));
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void on(CraftItemEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (!(e.getWhoClicked() instanceof Player p) || !hasAdaptation(p)) {
            return;
        }
        if (e.getRecipe() instanceof org.bukkit.inventory.ShapedRecipe recipe && recipe.getKey().getNamespace().equals("adapt") && (recipe.getKey().getKey().equals("herbalism-redmushblock") || recipe.getKey().getKey().equals("herbalism-brownmushblock"))) {
            getPlayer(p).getData().addStat("herbalism.mushroom-blocks.crafted", 1);
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
    @ConfigDescription("Craft Mushroom Blocks from Mushrooms in a Crafting Table.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 1;
    }
}
