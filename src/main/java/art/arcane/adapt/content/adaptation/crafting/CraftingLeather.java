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

package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdvancementSpec;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.Element;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class CraftingLeather extends SimpleAdaptation<CraftingLeather.Config> {

    public CraftingLeather() {
        super("crafting-leather");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("crafting.leather.description"));
        setDisplayName(Localizer.dLocalize("crafting.leather.name"));
        setIcon(Material.LEATHER);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(17776);
        registerRecipe(AdaptRecipe.campfire()
                .key("crafting-leather")
                .ingredient(Material.ROTTEN_FLESH)
                .cookTime(100)
                .experience(1)
                .result(new ItemStack(Material.LEATHER, 1))
                .build());
        AdvancementSpec leatherCrafted = AdvancementSpec.challenge(
                "challenge_crafting_leather_100",
                Material.LEATHER,
                Localizer.dLocalize("advancement.challenge_crafting_leather_100.title"),
                Localizer.dLocalize("advancement.challenge_crafting_leather_100.description")
        );
        registerMilestone(leatherCrafted, "crafting.leather.leather-crafted", 100, 300);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + C.GRAY + Localizer.dLocalize("crafting.leather.lore1"));
    }

    @EventHandler
    public void on(PlayerInteractEvent e) {
        if (e.getItem() != null && e.getItem().getType() == Material.ROTTEN_FLESH && e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.CAMPFIRE) {
            if (!hasAdaptation(e.getPlayer())) {
                e.setCancelled(true);
            } else {
                getPlayer(e.getPlayer()).getData().addStat("crafting.leather.leather-crafted", 1);
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
    @ConfigDescription("Craft Leather from Rotten Flesh on a campfire.")
    protected static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 3;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 1;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 2;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 1;
    }
}
