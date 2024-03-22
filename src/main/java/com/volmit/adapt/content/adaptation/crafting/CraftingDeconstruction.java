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
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.util.RayTraceResult;

import java.util.*;

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
        if (forStuff == null) return null;

        int maxPow = 0;
        Recipe selectedRecipe = null;

        for (Recipe recipe : Bukkit.getRecipesFor(forStuff)) {
            int currentPower = 0;
            if (recipe instanceof ShapelessRecipe r) {
                currentPower = r.getIngredientList().stream().mapToInt(ItemStack::getAmount).sum();
            } else if (recipe instanceof ShapedRecipe r) {
                currentPower = r.getIngredientMap().values().stream().mapToInt(f -> f == null ? 0 : f.getAmount()).sum();
            }
            if (currentPower > maxPow) {
                selectedRecipe = recipe;
                maxPow = currentPower;
            }
        }

        if (selectedRecipe == null) return null;

        int v = 0;
        int outa = 1;
        ItemStack sel = null;

        if (selectedRecipe instanceof ShapelessRecipe r) {
            for (ItemStack i : r.getIngredientList()) {
                int amount = i.getAmount() * forStuff.getAmount();
                if (amount > v) {
                    v = amount;
                    sel = i;
                    outa = r.getResult().getAmount();
                }
            }
        } else {
            ShapedRecipe r = (ShapedRecipe) selectedRecipe;
            Map<Material, Integer> ings = new HashMap<>();
            r.getIngredientMap().values().stream().filter(Objects::nonNull).forEach(i -> ings.merge(i.getType(), i.getAmount(), Integer::sum));

            for (Map.Entry<Material, Integer> entry : ings.entrySet()) {
                int amount = entry.getValue() * forStuff.getAmount();
                if (amount > v) {
                    v = amount;
                    sel = new ItemStack(entry.getKey(), entry.getValue());
                    outa = r.getResult().getAmount();
                }
            }
        }

        if (sel != null && sel.getAmount() * forStuff.getAmount() > 1) {
            int a = ((sel.getAmount() * forStuff.getAmount()) / outa) / 2;
            if (a <= sel.getMaxStackSize() && getValue(sel) < getValue(forStuff)) {
                sel.setAmount(a);
                return sel.clone();
            }
        }

        return null;
    }


    @EventHandler
    public void on(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        if (!hasAdaptation(player)) {
            return;
        }

        if (!player.isSneaking() || mainHandItem.getType() != Material.SHEARS) {
            return;
        }

        // Perform a ray trace for 6 blocks looking for an item
        RayTraceResult rayTrace = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getLocation().getDirection(), 6, entity -> entity instanceof Item);
        if (rayTrace != null && rayTrace.getHitEntity() instanceof Item itemEntity) {
            processItemInteraction(player, mainHandItem, itemEntity);
        }
    }

    private void processItemInteraction(Player player, ItemStack mainHandItem, Item itemEntity) {
        ItemStack forStuff = itemEntity.getItemStack();
        ItemStack offering = getDeconstructionOffering(forStuff);

        if (offering != null) {
            itemEntity.setItemStack(offering);
            for (Player players : player.getWorld().getPlayers()) {
                players.playSound(itemEntity.getLocation(), Sound.BLOCK_BASALT_BREAK, 1F, 0.2f);
                players.playSound(itemEntity.getLocation(), Sound.BLOCK_BEEHIVE_SHEAR, 1F, 0.7f);
            }
            getSkill().xp(player, getValue(offering));

            // Damage the shears
            Damageable damageable = (Damageable) mainHandItem.getItemMeta();
            int newDamage = damageable.getDamage() + 8 * forStuff.getAmount();
            if (newDamage >= mainHandItem.getType().getMaxDurability()) {
                player.getInventory().setItemInMainHand(null); // Break the shears
            } else {
                damageable.setDamage(newDamage);
                mainHandItem.setItemMeta(damageable);
            }
        } else {
            for (Player players : player.getWorld().getPlayers()) {
                players.playSound(itemEntity.getLocation(), Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 1F, 1f); // Burnt torch sound
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
    protected static class Config {
        boolean permanent = false;
        boolean enabled = true;
        int baseCost = 9;
        int initialCost = 8;
        double costFactor = 1.355;
    }
}
