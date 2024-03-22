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

package com.volmit.adapt.content.adaptation.excavation;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.content.item.ItemListings;
import com.volmit.adapt.content.item.multiItems.OmniTool;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;


public class ExcavationOmniTool extends SimpleAdaptation<ExcavationOmniTool.Config> {
    private static final OmniTool omniTool = new OmniTool();

    public ExcavationOmniTool() {
        super("excavation-omnitool");
        registerConfiguration(ExcavationOmniTool.Config.class);
        setDisplayName(Localizer.dLocalize("excavation", "omnitool", "name"));
        setDescription(Localizer.dLocalize("excavation", "omnitool", "description"));
        setIcon(Material.DISC_FRAGMENT_5);
        setInterval(20202);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + Localizer.dLocalize("excavation", "omnitool", "lore1"));
        v.addLore(C.GRAY + Localizer.dLocalize("excavation", "omnitool", "lore2"));
        v.addLore(C.GREEN + Localizer.dLocalize("excavation", "omnitool", "lore3"));
        v.addLore(C.RED + Localizer.dLocalize("excavation", "omnitool", "lore4"));
        v.addLore(C.GRAY + Localizer.dLocalize("excavation", "omnitool", "lore5"));
        v.addLore(C.GREEN + "" + (level + getConfig().startingSlots) + C.GRAY + " " + Localizer.dLocalize("excavation", "omnitool", "lore6"));
        v.addLore(C.UNDERLINE + Localizer.dLocalize("excavation", "omnitool", "lore7"));


    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public void onTick() {
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void on(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && validateTool(p.getInventory().getItemInMainHand())) {
            //deny if the tool durability is about to break
            if (p.getInventory().getItemInMainHand().getType().getMaxDurability() - p.getInventory().getItemInMainHand().getDurability() <= 2) {
                e.setCancelled(true);
                return;
            }

            if (e.isCancelled()) {
                return;
            }
            if (!hasAdaptation(p) && validateTool(p.getInventory().getItemInMainHand())) {
                e.setCancelled(true);
                return;
            }
            if (!hasAdaptation(p)) {
                if (validateTool(p.getInventory().getItemInMainHand())) {
                    e.setCancelled(true);
                }
                return;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            Damageable inHand = (Damageable) hand.getItemMeta();

            if (!validateTool(hand)) {
                return;
            }
            J.s(() -> p.getInventory().setItemInMainHand(omniTool.nextSword(hand)));
            for (Player players : p.getWorld().getPlayers()) {
                players.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
            }
            if (inHand != null && inHand.hasDamage()) {
                if ((hand.getType().getMaxDurability() - inHand.getDamage() - 2) <= 2) {
                    e.setCancelled(true);
                    p.playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_STEP, 0.25f, 0.77f);
                }
            }

        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void on(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (validateTool(p.getInventory().getItemInMainHand())) {
            //deny if the tool durability is about to break
            if (p.getInventory().getItemInMainHand().getType().getMaxDurability() - p.getInventory().getItemInMainHand().getDurability() <= 2) {
                e.setCancelled(true);
                return;
            }


            //deny if they dont have the adaptation
            if (!hasAdaptation(p)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (validateTool(p.getInventory().getItemInMainHand())) {
            //deny if the tool durability is about to break
            if (p.getInventory().getItemInMainHand().getType().getMaxDurability() - p.getInventory().getItemInMainHand().getDurability() <= 2) {
                e.setCancelled(true);
                return;
            }

            if (!hasAdaptation(p)) {
                return;
            }
            if (e.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
                ItemStack hand = p.getInventory().getItemInMainHand();
                Damageable imHand = (Damageable) hand.getItemMeta();
                Block block = e.getClickedBlock();
                if (block != null) {
                    if (ItemListings.farmable.contains(block.getType())) {
                        if (isShovel(hand)) {
                            J.s(() -> p.getInventory().setItemInMainHand(omniTool.nextHoe(hand)));
                            for (Player players : p.getWorld().getPlayers()) {
                                players.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
                            }
                        } else {
                            J.s(() -> p.getInventory().setItemInMainHand(omniTool.nextShovel(hand)));
                            for (Player players : p.getWorld().getPlayers()) {
                                players.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
                            }
                        }
                        if (imHand != null && imHand.hasDamage()) {
                            if ((hand.getType().getMaxDurability() - imHand.getDamage() - 2) <= 2) {
                                e.setCancelled(true);
                                p.playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_STEP, 0.25f, 0.77f);
                            }
                        }
                    } else if (ItemListings.burnable.contains(block.getType())) {
                        J.s(() -> p.getInventory().setItemInMainHand(omniTool.nextFnS(hand)));
                        for (Player players : p.getWorld().getPlayers()) {
                            players.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
                        }
                        if (imHand != null && imHand.hasDamage()) {
                            if ((hand.getType().getMaxDurability() - imHand.getDamage() - 2) <= 2) {
                                e.setCancelled(true);
                                p.playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_STEP, 0.25f, 0.77f);
                            }
                        }
                    }
                }
            }
        }

    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (!hasAdaptation(p)) {
            return;
        }
        if (p.isSneaking()) {
            if (validateTool(e.getItemDrop().getItemStack())) {
                List<ItemStack> drops = omniTool.explode(e.getItemDrop().getItemStack());
                for (ItemStack i : drops) {
                    Damageable iDmgable = (Damageable) i.getItemMeta();
                    if (i.hasItemMeta()) {
                        ItemMeta im = i.getItemMeta().clone();
                        ItemMeta im2 = im;
                        if (im.hasDisplayName()) {
                            im2.setDisplayName(im.getDisplayName());
                        }
                        if (im.hasEnchants()) {
                            Map<Enchantment, Integer> enchants = im.getEnchants();
                            for (Enchantment enchant : enchants.keySet()) {
                                im2.addEnchant(enchant, enchants.get(enchant), true);
                            }
                        }
                        if (iDmgable != null && iDmgable.hasDamage()) {
                            ((Damageable) im2).setDamage(iDmgable.getDamage());
                        }
                        im2.setLore(null);
                        i.setItemMeta(im2);
                    }
                    drops.set(drops.indexOf(i), i);
                }

                J.s(() -> {
                    p.playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_DEATH, 0.25f, 0.77f);
                    for (ItemStack i : drops) {
                        p.getWorld().dropItem(p.getLocation(), i);
                    }
                });
                e.getItemDrop().setItemStack(new ItemStack(Material.AIR));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockDamageEvent e) {
        Player p = e.getPlayer();
        org.bukkit.block.Block b = e.getBlock(); // nms block for pref tool
        ItemStack hand = p.getInventory().getItemInMainHand();

        if (validateTool(hand)) {
            if (e.isCancelled()) {
                return;
            }
            if (!hasAdaptation(p)) {
                return;
            }

            Damageable imHand = (Damageable) hand.getItemMeta();
            if (ItemListings.getAxePreference().contains(b.getType())) {
                if (!isAxe(hand)) {
                    Adapt.verbose("Omnitool for " + p.getName() + " changed to axe");
                    J.s(() -> p.getInventory().setItemInMainHand(omniTool.nextAxe(hand)));
                    itemDelegate(e, hand, imHand);
                } else {
                    Adapt.verbose("Omnitool for " + p.getName() + " is already axe");
                }
            } else if (ItemListings.getShovelPreference().contains(b.getType())) {
                if (!isShovel(hand)) {
                    Adapt.verbose("Omnitool for " + p.getName() + " changed to shovel");
                    J.s(() -> p.getInventory().setItemInMainHand(omniTool.nextShovel(hand)));
                    itemDelegate(e, hand, imHand);
                } else {
                    Adapt.verbose("Omnitool for " + p.getName() + " is already shovel");
                }
            } else if (ItemListings.getSwordPreference().contains(b.getType())) {
                if (!isSword(hand)) {
                    Adapt.verbose("Omnitool for " + p.getName() + " changed to sword");
                    J.s(() -> p.getInventory().setItemInMainHand(omniTool.nextSword(hand)));
                    itemDelegate(e, hand, imHand);
                } else {
                    Adapt.verbose("Omnitool for " + p.getName() + " is already sword");
                }
            } else { // Default to pickaxe
                if (!isPickaxe(hand)) {
                    Adapt.verbose("Omnitool for " + p.getName() + " changed to pickaxe");
                    J.s(() -> p.getInventory().setItemInMainHand(omniTool.nextPickaxe(hand)));
                    itemDelegate(e, hand, imHand);
                }
            }
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(InventoryClickEvent e) {
        if (!hasAdaptation((Player) e.getWhoClicked())) {
            return;
        }
        if (e.getClickedInventory() != null && e.getClick().equals(ClickType.SHIFT_LEFT) && e.getAction().equals(InventoryAction.MOVE_TO_OTHER_INVENTORY)) {
            ItemStack cursor = e.getWhoClicked().getItemOnCursor().clone();
            ItemStack clicked = e.getClickedInventory().getItem(e.getSlot()).clone();

            if (omniTool.explode(cursor).size() > 1 || omniTool.explode(clicked).size() > 1) {
                if (omniTool.explode(cursor).size() >= getSlots(getLevel((Player) e.getWhoClicked())) || omniTool.explode(clicked).size() >= getSlots(getLevel((Player) e.getWhoClicked()))) {
                    e.setCancelled(true);
                    ((Player) e.getWhoClicked()).playSound(e.getWhoClicked().getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.77f);
                    return;
                }
            }
            if (ItemListings.tool.contains(cursor.getType()) && ItemListings.tool.contains(clicked.getType())) { // TOOLS ONLY
                if (!cursor.getType().isAir() && !clicked.getType().isAir() && omniTool.supportsItem(cursor) && omniTool.supportsItem(clicked)) {
                    e.setCancelled(true);
                    e.getWhoClicked().setItemOnCursor(new ItemStack(Material.AIR));
                    e.getClickedInventory().setItem(e.getSlot(), omniTool.build(cursor, clicked));
                    for (Player players : e.getWhoClicked().getWorld().getPlayers()) {
                       players.playSound(e.getWhoClicked().getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
                    }
                }
            }
        }

    }

    private void itemDelegate(BlockDamageEvent e, ItemStack hand, Damageable imHand) {
        Player p = e.getPlayer();
        for (Player players : p.getWorld().getPlayers()) {
            players.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
        }
        if (imHand != null && imHand.hasDamage()) {
            if ((hand.getType().getMaxDurability() - imHand.getDamage() - 2) <= 2) {
                e.setCancelled(true);
                p.playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_STEP, 0.25f, 0.77f);
            }
        }
    }

    private boolean validateTool(ItemStack item) {
        return (item.getItemMeta() != null
                && item.getItemMeta().getLore() != null
                && item.getItemMeta().getLore().toString().contains("Leatherman"));
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
        boolean permanent = false;
        boolean enabled = true;
        int baseCost = 10;
        int initialCost = 3;
        double costFactor = 0.20;
        int maxLevel = 5;
        int startingSlots = 1;
    }
}
