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

package com.volmit.adapt.content.adaptation.blocking;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.content.item.ItemListings;
import com.volmit.adapt.content.item.multiItems.MultiArmor;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


public class BlockingMultiArmor extends SimpleAdaptation<BlockingMultiArmor.Config> {
    private static final MultiArmor multiarmor = new MultiArmor();
    private final Map<Player, Long> cooldowns;


    public BlockingMultiArmor() {
        super("blocking-multiarmor");
        registerConfiguration(BlockingMultiArmor.Config.class);
        setDisplayName(Localizer.dLocalize("blocking", "multiarmor", "name"));
        setDescription(Localizer.dLocalize("blocking", "multiarmor", "description"));
        setIcon(Material.ELYTRA);
        setInterval(20202);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        cooldowns = new HashMap<>();
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + Localizer.dLocalize("blocking", "multiarmor", "lore1"));
        v.addLore(C.GRAY + "" + C.GRAY + Localizer.dLocalize("blocking", "multiarmor", "lore2"));
        v.addLore(C.GREEN + Localizer.dLocalize("blocking", "multiarmor", "lore3"));
        v.addLore(C.RED + Localizer.dLocalize("blocking", "multiarmor", "lore4"));
        v.addLore(C.GRAY + Localizer.dLocalize("blocking", "multiarmor", "lore5"));
        v.addLore(C.UNDERLINE + Localizer.dLocalize("blocking", "multiarmor", "lore6"));
    }

    @Override
    public boolean isEnabled() {
        return !getConfig().enabled;
    }

    @Override
    public void onTick() {
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void on(PlayerMoveEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        ItemStack chest = p.getInventory().getChestplate();
        if (chest != null && hasAdaptation(p) && validateArmor(chest)) {
            Long cooldown = cooldowns.get(p);
            if (cooldown != null) {
                if (cooldown + 3000 > System.currentTimeMillis())
                    return;
                else cooldowns.remove(p);
            }

            SoundPlayer spw = SoundPlayer.of(p.getWorld());
            if (p.isOnGround() && !p.isFlying()) {
                if (isChestplate(chest)) {
                    return;
                }
                J.s(() -> p.getInventory().setChestplate(multiarmor.nextChestplate(chest)));
                cooldowns.put(p, System.currentTimeMillis());
                spw.play(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
                spw.play(p.getLocation(), Sound.BLOCK_BEEHIVE_SHEAR, 0.5f, 0.77f);

            } else if (p.getFallDistance() > 4) {
                if (isElytra(chest)) {
                    return;
                }
                J.s(() -> p.getInventory().setChestplate(multiarmor.nextElytra(chest)));
                cooldowns.put(p, System.currentTimeMillis());
                spw.play(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
                spw.play(p.getLocation(), Sound.ENTITY_IRON_GOLEM_STEP, 0.5f, 0.77f);
            }
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        SoundPlayer sp = SoundPlayer.of(p);
        if (!hasAdaptation(p)) {
            return;
        }
        if (p.isSneaking()) {
            if (validateArmor(e.getItemDrop().getItemStack())) {
                List<ItemStack> drops = multiarmor.explode(e.getItemDrop().getItemStack());
                for (ItemStack i : drops) {
                    Damageable iDmgable = (Damageable) i.getItemMeta();
                    if (i.hasItemMeta()) {
                        ItemMeta im = Objects.requireNonNull(i.getItemMeta()).clone();
                        if (im.hasDisplayName()) {
                            im.setDisplayName(im.getDisplayName());
                        }
                        if (im.hasEnchants()) {
                            Map<Enchantment, Integer> enchants = im.getEnchants();
                            for (Enchantment enchant : enchants.keySet()) {
                                im.addEnchant(enchant, enchants.get(enchant), true);
                            }
                        }
                        if (iDmgable != null && iDmgable.hasDamage()) {
                            ((Damageable) im).setDamage(iDmgable.getDamage());
                        }
                        im.setLore(null);
                        i.setItemMeta(im);
                    }
                    drops.set(drops.indexOf(i), i);
                }

                J.s(() -> {
                    sp.play(p.getLocation(), Sound.ENTITY_IRON_GOLEM_DEATH, 0.25f, 0.77f);
                    for (ItemStack i : drops) {
                        p.getWorld().dropItem(p.getLocation(), i);
                    }
                });
                e.getItemDrop().setItemStack(new ItemStack(Material.AIR));
            }
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(InventoryClickEvent e) {
        if (!hasAdaptation((Player) e.getWhoClicked())) {
            return;
        }
        if (e.getClickedInventory() != null
                && e.getClick().equals(ClickType.SHIFT_LEFT)
                && e.getClickedInventory().getItem(e.getSlot()) != null
                && e.getAction().equals(InventoryAction.MOVE_TO_OTHER_INVENTORY)) {
            ItemStack cursor = e.getWhoClicked().getItemOnCursor().clone();
            ItemStack clicked = Objects.requireNonNull(e.getClickedInventory().getItem(e.getSlot())).clone();

            if (cursor.getType().equals(Material.ELYTRA) || clicked.getType().equals(Material.ELYTRA)) { // One must be an ELYTRA

                if (multiarmor.explode(cursor).size() > 1 || multiarmor.explode(clicked).size() > 1) {

                    if (multiarmor.explode(cursor).size() >= getSlots(getLevel((Player) e.getWhoClicked())) || multiarmor.explode(clicked).size() >= getSlots(getLevel((Player) e.getWhoClicked()))) {
                        e.setCancelled(true);
                        SoundPlayer sp = SoundPlayer.of((Player) e.getWhoClicked());
                        sp.play(e.getWhoClicked().getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.77f);
                        return;
                    }
                }
                if (ItemListings.getMultiArmorable().contains(cursor.getType()) && ItemListings.getMultiArmorable().contains(clicked.getType())) { // Chest/Elytra Only

                    if (!cursor.getType().isAir() && !clicked.getType().isAir() && multiarmor.supportsItem(cursor) && multiarmor.supportsItem(clicked)) {
                        e.setCancelled(true);
                        e.getWhoClicked().setItemOnCursor(new ItemStack(Material.AIR));
                        e.getClickedInventory().setItem(e.getSlot(), multiarmor.build(cursor, clicked));
                        SoundPlayer spw = SoundPlayer.of(e.getWhoClicked().getWorld());
                        spw.play(e.getWhoClicked().getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
                    }
                }
            }
        }
    }


    private boolean validateArmor(ItemStack item) {
        if (item.getItemMeta() != null && item.getItemMeta().getLore() != null) {
            for (String lore : item.getItemMeta().getLore()) {
                if (lore != null && lore.contains("MultiArmor")) {
                    return true;
                }
            }
        }
        return false;
    }


    private double getSlots(double level) {
        return getConfig().startingSlots + level;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    protected static class Config {
        final boolean permanent = false;
        final boolean enabled = true;
        final int baseCost = 1;
        final int initialCost = 3;
        final double costFactor = 1;
        final int maxLevel = 1;
        final int startingSlots = 1;
    }
}
