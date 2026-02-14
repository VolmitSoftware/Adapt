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
import art.arcane.adapt.api.recipe.MaterialChar;
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

public class CraftingSkulls extends SimpleAdaptation<CraftingSkulls.Config> {

    public CraftingSkulls() {
        super("crafting-skulls");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("crafting.skulls.description"));
        setDisplayName(Localizer.dLocalize("crafting.skulls.name"));
        setIcon(Material.SKELETON_SKULL);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(17776);
        registerRecipe(AdaptRecipe.shaped()
                .key("crafting-skeletonskull")
                .ingredient(new MaterialChar('I', Material.BONE))
                .ingredient(new MaterialChar('X', Material.BONE_BLOCK))
                .shapes(List.of(
                        "III",
                        "IXI",
                        "III"))
                .result(new ItemStack(Material.SKELETON_SKULL, 1))
                .build());
        registerRecipe(AdaptRecipe.shaped()
                .key("crafting-witherskeletonskull")
                .ingredient(new MaterialChar('I', Material.NETHER_BRICK))
                .ingredient(new MaterialChar('X', Material.BONE_BLOCK))
                .shapes(List.of(
                        "III",
                        "IXI",
                        "III"))
                .result(new ItemStack(Material.WITHER_SKELETON_SKULL, 1))
                .build());
        registerRecipe(AdaptRecipe.shaped()
                .key("crafting-zombieskull")
                .ingredient(new MaterialChar('I', Material.ROTTEN_FLESH))
                .ingredient(new MaterialChar('X', Material.BONE_BLOCK))
                .shapes(List.of(
                        "III",
                        "IXI",
                        "III"))
                .result(new ItemStack(Material.ZOMBIE_HEAD, 1))
                .build());
        registerRecipe(AdaptRecipe.shaped()
                .key("crafting-creeperhead")
                .ingredient(new MaterialChar('I', Material.GUNPOWDER))
                .ingredient(new MaterialChar('X', Material.BONE_BLOCK))
                .shapes(List.of(
                        "III",
                        "IXI",
                        "III"))
                .result(new ItemStack(Material.CREEPER_HEAD, 1))
                .build());
        registerRecipe(AdaptRecipe.shaped()
                .key("crafting-dragonhead")
                .ingredient(new MaterialChar('I', Material.DRAGON_BREATH))
                .ingredient(new MaterialChar('X', Material.BONE_BLOCK))
                .shapes(List.of(
                        "III",
                        "IXI",
                        "III"))
                .result(new ItemStack(Material.DRAGON_HEAD, 1))
                .build());
        AdvancementSpec skulls100 = AdvancementSpec.challenge(
                "challenge_crafting_skulls_100",
                Material.WITHER_SKELETON_SKULL,
                Localizer.dLocalize("advancement.challenge_crafting_skulls_100.title"),
                Localizer.dLocalize("advancement.challenge_crafting_skulls_100.description")
        );
        AdvancementSpec skulls10 = AdvancementSpec.challenge(
                "challenge_crafting_skulls_10",
                Material.SKELETON_SKULL,
                Localizer.dLocalize("advancement.challenge_crafting_skulls_10.title"),
                Localizer.dLocalize("advancement.challenge_crafting_skulls_10.description")
        ).withChild(skulls100);
        registerAdvancementSpec(skulls10);
        registerStatTracker(skulls10.statTracker("crafting.skulls.skulls-crafted", 10, 300));
        registerStatTracker(skulls100.statTracker("crafting.skulls.skulls-crafted", 100, 1000));
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + C.GRAY + Localizer.dLocalize("crafting.skulls.lore1"));
        v.addLore(C.YELLOW + "- " + C.GRAY + Localizer.dLocalize("crafting.skulls.lore2"));
        v.addLore(C.YELLOW + "- " + C.GRAY + Localizer.dLocalize("crafting.skulls.lore3"));
        v.addLore(C.YELLOW + "- " + C.GRAY + Localizer.dLocalize("crafting.skulls.lore4"));
        v.addLore(C.YELLOW + "- " + C.GRAY + Localizer.dLocalize("crafting.skulls.lore5"));
        v.addLore(C.YELLOW + "- " + C.GRAY + Localizer.dLocalize("crafting.skulls.lore6"));
    }


    @EventHandler
    public void on(CraftItemEvent e) {
        if (e.isCancelled()) return;
        Player p = (Player) e.getWhoClicked();
        if (!hasAdaptation(p)) return;
        if (e.getRecipe() != null) {
            Material result = e.getRecipe().getResult().getType();
            if (result == Material.SKELETON_SKULL || result == Material.WITHER_SKELETON_SKULL
                    || result == Material.ZOMBIE_HEAD || result == Material.CREEPER_HEAD
                    || result == Material.DRAGON_HEAD) {
                getPlayer(p).getData().addStat("crafting.skulls.skulls-crafted", 1);
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
    @ConfigDescription("Craft Mob Skulls using materials surrounding a Bone Block.")
    protected static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 8;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 1;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 2;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 1;
    }
}
