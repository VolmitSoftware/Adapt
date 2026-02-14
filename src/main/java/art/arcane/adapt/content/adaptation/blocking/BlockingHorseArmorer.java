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

package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.recipe.MaterialChar;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.Element;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class BlockingHorseArmorer extends SimpleAdaptation<BlockingHorseArmorer.Config> {

    public BlockingHorseArmorer() {
        super("blocking-horsearmorer");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("blocking.horse_armorer.description"));
        setDisplayName(Localizer.dLocalize("blocking.horse_armorer.name"));
        setIcon(Material.GOLDEN_HORSE_ARMOR);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(17774);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        registerRecipe(AdaptRecipe.shaped()
                .key("blocking-horsearmorerleather")
                .ingredient(new MaterialChar('I', Material.LEATHER))
                .ingredient(new MaterialChar('U', Material.SADDLE))
                .shapes(List.of(
                        "III",
                        "IUI",
                        "III"))
                .result(new ItemStack(Material.LEATHER_HORSE_ARMOR, 1))
                .build());
        registerRecipe(AdaptRecipe.shaped()
                .key("blocking-horsearmoreriron")
                .ingredient(new MaterialChar('I', Material.IRON_INGOT))
                .ingredient(new MaterialChar('U', Material.SADDLE))
                .shapes(List.of(
                        "III",
                        "IUI",
                        "III"))
                .result(new ItemStack(Material.IRON_HORSE_ARMOR, 1))
                .build());
        registerRecipe(AdaptRecipe.shaped()
                .key("blocking-horsearmorergold")
                .ingredient(new MaterialChar('I', Material.GOLD_INGOT))
                .ingredient(new MaterialChar('U', Material.SADDLE))
                .shapes(List.of(
                        "III",
                        "IUI",
                        "III"))
                .result(new ItemStack(Material.GOLDEN_HORSE_ARMOR, 1))
                .build());
        registerRecipe(AdaptRecipe.shaped()
                .key("blocking-horsearmorerdiamond")
                .ingredient(new MaterialChar('I', Material.DIAMOND))
                .ingredient(new MaterialChar('U', Material.SADDLE))
                .shapes(List.of(
                        "III",
                        "IUI",
                        "III"))
                .result(new ItemStack(Material.DIAMOND_HORSE_ARMOR, 1))
                .build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.IRON_HORSE_ARMOR)
                .key("challenge_blocking_horse_armor_10")
                .title(Localizer.dLocalize("advancement.challenge_blocking_horse_armor_10.title"))
                .description(Localizer.dLocalize("advancement.challenge_blocking_horse_armor_10.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_blocking_horse_armor_10", "blocking.horse-armorer.armor-crafted", 10, 400);
    }

    @EventHandler
    public void on(CraftItemEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getWhoClicked() instanceof Player p && hasAdaptation(p) && isAdaptationRecipe(e.getRecipe())) {
            getPlayer(p).getData().addStat("blocking.horse-armorer.armor-crafted", 1);
        }
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + C.GRAY + Localizer.dLocalize("blocking.horse_armorer.lore1"));
        v.addLore("XXX");
        v.addLore("XSX");
        v.addLore("XXX");

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
    @ConfigDescription("Craft Horse Armor by surrounding a saddle with material.")
    protected static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = false;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 1;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 1;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0;
    }
}
