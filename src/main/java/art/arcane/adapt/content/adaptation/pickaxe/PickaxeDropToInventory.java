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

package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.content.item.ItemListings;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.Element;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDropItemEvent;

import java.util.List;

public class PickaxeDropToInventory extends SimpleAdaptation<PickaxeDropToInventory.Config> {
    public PickaxeDropToInventory() {
        super("pickaxe-drop-to-inventory");
        registerConfiguration(PickaxeDropToInventory.Config.class);
        setDescription(Localizer.dLocalize("pickaxe.drop_to_inventory.description"));
        setDisplayName(Localizer.dLocalize("pickaxe.drop_to_inventory.name"));
        setIcon(Material.MINECART);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(7944);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.CHEST)
                .key("challenge_pickaxe_dti_25k")
                .title(Localizer.dLocalize("advancement.challenge_pickaxe_dti_25k.title"))
                .description(Localizer.dLocalize("advancement.challenge_pickaxe_dti_25k.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_pickaxe_dti_25k", "pickaxe.drop-to-inv.items-caught", 25000, 500);
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + Localizer.dLocalize("pickaxe.drop_to_inventory.lore1"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(BlockDropItemEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        SoundPlayer sp = SoundPlayer.of(p);
        if (!hasAdaptation(p)) {
            return;
        }
        if (p.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        if (!canBlockBreak(p, e.getBlock().getLocation())) {
            return;
        }
        if (ItemListings.toolPickaxes.contains(p.getInventory().getItemInMainHand().getType())) {
            List<Item> items = new KList<>(e.getItems());
            e.getItems().clear();
            int caught = 0;
            for (Item i : items) {
                sp.play(p.getLocation(), Sound.BLOCK_CALCITE_HIT, 0.05f, 0.01f);
                if (!p.getInventory().addItem(i.getItemStack()).isEmpty()) {
                    p.getWorld().dropItem(p.getLocation(), i.getItemStack());
                }
                caught++;
            }
            if (caught > 0) {
                getPlayer(p).getData().addStat("pickaxe.drop-to-inv.items-caught", caught);
            }
        }
    }


    @Override
    public void onTick() {
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    @ConfigDescription("Mined blocks drop directly into your inventory.")
    protected static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 1;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 1;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 3;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 1;
    }
}
