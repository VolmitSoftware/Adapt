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

package com.volmit.adapt.content.adaptation.crafting;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.*;

import java.util.ArrayList;
import java.util.List;

public class CraftingDeconstruction extends SimpleAdaptation<CraftingDeconstruction.Config> {
    private final List<Integer> holds = new ArrayList<>();

    public CraftingDeconstruction() {
        super("crafting-deconstruction");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("crafting", "deconstruction", "description"));
        setDisplayName(Localizer.dLocalize("crafting", "deconstruction", "name"));
        setIcon(Material.SHEARS);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(1);
        setInterval(5590);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Localizer.dLocalize("crafting", "deconstruction", "lore1"));
        v.addLore(C.GREEN + Localizer.dLocalize("crafting", "deconstruction", "lore2"));
    }

    public ItemStack getDeconstructionOffering(ItemStack forStuff) {
        if (forStuff == null) {
            return null;
        }

        int maxPow = 0;
        Recipe sr = null;

        for (Recipe i : Bukkit.getRecipesFor(forStuff)) {
            if (i instanceof ShapelessRecipe r) {
                int mp = r.getIngredientList().stream().mapToInt(f -> f.getAmount()).sum();

                if (mp > maxPow) {
                    sr = i;
                    maxPow = mp;
                }
            } else if (i instanceof ShapedRecipe r) {
                int mp = r.getIngredientMap().values().stream().mapToInt(f -> f == null ? 0 : f.getAmount()).sum();

                if (mp > maxPow) {
                    sr = i;
                    maxPow = mp;
                }
            }
        }

        if (sr == null) {
            return null;
        }

        int v = 0;
        int outa = 1;
        ItemStack sel = null;

        if (sr instanceof ShapelessRecipe r) {
            for (ItemStack i : r.getIngredientList()) {
                if (i.getAmount() * forStuff.getAmount() > v) {
                    v = i.getAmount() * forStuff.getAmount();
                    sel = i;
                    outa = r.getResult().getAmount();
                }
            }
        } else {
            ShapedRecipe r = (ShapedRecipe) sr;
            List<ItemStack> ings = new ArrayList<>();

            r.getIngredientMap().forEach((k, vx) -> {
                if (vx == null) {
                    return;
                }

                for (ItemStack i : ings) {
                    if (vx.getType().equals(i.getType())) {
                        i.setAmount(i.getAmount() + 1);
                        return;
                    }
                }

                ings.add(vx);
            });

            for (ItemStack i : ings) {
                if (i != null && i.getAmount() * forStuff.getAmount() > v) {
                    v = i.getAmount() * forStuff.getAmount();
                    sel = i;
                    outa = r.getResult().getAmount();
                }
            }
        }

        if (sel != null && sel.getAmount() * forStuff.getAmount() > 1) {
            sel = sel.clone();

            int a = ((sel.getAmount() * forStuff.getAmount()) / outa) / 2;

            if (a > sel.getMaxStackSize()) {
                return null;
            }

            sel.setAmount(a);

            if (getValue(sel) >= getValue(forStuff)) {
                return null;
            }

            return sel;
        }

        return null;
    }

    public int getShearDamage(ItemStack forStuff) {
        return forStuff.getAmount() * 8;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.isCancelled()) {
            return;
        }
        if (!hasAdaptation((Player) e.getWhoClicked())) {
            return;
        }
        if (e.getView().getTopInventory().getType().equals(InventoryType.SMITHING)) {
            SmithingInventory s = (SmithingInventory) e.getView().getTopInventory();
            J.s(() -> {
                if (s.getItem(1) != null && s.getItem(1).getType().equals(Material.SHEARS) && s.getItem(0) != null) {
                    s.setResult(getDeconstructionOffering(s.getItem(0)));
                }
            });
        }

        if (e.getClickedInventory() != null && e.getClickedInventory().getType().equals(InventoryType.SMITHING)) {
            SmithingInventory s = (SmithingInventory) e.getClickedInventory();
            if (e.getSlotType().equals(InventoryType.SlotType.CRAFTING)) {
                J.s(() -> {
                    if (s.getItem(1) != null && s.getItem(1).getType().equals(Material.SHEARS) && s.getItem(0) != null) {
                        s.setResult(getDeconstructionOffering(s.getItem(0)));
                    }
                });
            } else if (e.getSlotType().equals(InventoryType.SlotType.RESULT)) {
                if (s.getItem(1) != null && s.getItem(1).getType().equals(Material.SHEARS) && s.getItem(0) != null) {
                    ItemStack offering = getDeconstructionOffering(s.getItem(0));

                    if (offering != null) {
                        s.setItem(1, damage(s.getItem(1), s.getItem(0).getAmount()));
                        e.setCursor(offering);
                        e.getClickedInventory().setItem(0, null);
                        e.getWhoClicked().getWorld().playSound(e.getClickedInventory().getLocation(), Sound.BLOCK_BASALT_BREAK, 1F, 0.2f);
                        e.getWhoClicked().getWorld().playSound(e.getClickedInventory().getLocation(), Sound.BLOCK_BEEHIVE_SHEAR, 1F, 0.7f);
                        getSkill().xp((Player) e.getWhoClicked(), getValue(offering));
                    }
                }
            }
        }
    }

    private void updateOffering(Inventory inventory) {
        SmithingInventory s = (SmithingInventory) inventory;

        if (s.getItem(1) != null && s.getItem(1).getType().equals(Material.SHEARS) && s.getItem(0) != null) {
            ItemStack offering = getDeconstructionOffering(s.getItem(0));
            s.setResult(offering);
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
    protected static class Config {
        boolean permanent = false;
        boolean enabled = true;
        int baseCost = 9;
        int initialCost = 8;
        double costFactor = 1.355;
    }
}
