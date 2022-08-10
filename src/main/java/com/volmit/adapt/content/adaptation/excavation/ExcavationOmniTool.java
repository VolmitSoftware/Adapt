package com.volmit.adapt.content.adaptation.excavation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.content.adaptation.excavation.util.ToolListing;
import com.volmit.adapt.content.item.multiItems.OmniTool;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import lombok.NoArgsConstructor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
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
        setDisplayName("OMNI - T.O.O.L.");
        setDescription("Tackle's overdesigned opulent Leatherman");
        setIcon(Material.DISC_FRAGMENT_5);
        setInterval(20202);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + "Probably the most powerful of  many allows you to");
        v.addLore(C.GRAY + "dynamically merge and change tools on the fly, based on your needs.");
        v.addLore(C.GREEN + "to merge, shift click an item over another in your inventory.");
        v.addLore(C.RED + "to unbind tools, Sneak-Drop the item, and it will disassemble.");
        v.addLore(C.GRAY + "you can't break tools in this leatherman but you can't use broken tools");
        v.addLore(C.GREEN + "" + (level + getConfig().startingSlots) + C.GRAY + " total merge-able items");
        v.addLore(C.UNDERLINE + "you could use five or six tools, or just one!");


    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public void onTick() {
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {

        if (e.getDamager() instanceof Player p) {
            if (!hasAdaptation(p)) {
                if (validateTool(p.getInventory().getItemInMainHand())) {
                    e.setCancelled(true);
                }
                return;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            Damageable imHand = (Damageable) hand.getItemMeta();

            if (!validateTool(hand)) {
                return;
            }
            J.s(() -> p.getInventory().setItemInMainHand(omniTool.nextSword(hand)));
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
            if (imHand != null && imHand.hasDamage()) {
                if ((hand.getType().getMaxDurability() - imHand.getDamage() - 2) <= 2) {
                    e.setCancelled(true);
                    p.playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_STEP, 0.25f, 0.77f);
                }
            }
        }
    }

    @EventHandler
    public void on(BlockBreakEvent e) {
        if (!hasAdaptation(e.getPlayer())) {
            return;
        }
        if (!validateTool(e.getPlayer().getInventory().getItemInMainHand())) {
            return;
        }
        xp(e.getPlayer(), 3);
    }

    @EventHandler
    public void on(PlayerInteractEvent e) {
        if (!hasAdaptation(e.getPlayer())) {
            return;
        }
        if (e.getAction().equals(Action.RIGHT_CLICK_BLOCK) && hasAdaptation(e.getPlayer())) {
            ItemStack hand = e.getPlayer().getInventory().getItemInMainHand();
            if (!validateTool(hand)) {
                return;
            }
            Damageable imHand = (Damageable) hand.getItemMeta();
            Block block = e.getClickedBlock();
            if (block == null) {
                return;
            }
            if (ToolListing.farmable.contains(block.getType())) {
                J.s(() -> e.getPlayer().getInventory().setItemInMainHand(omniTool.nextHoe(hand)));
                e.getPlayer().getWorld().playSound(e.getPlayer().getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
                if (imHand != null && imHand.hasDamage()) {
                    if ((hand.getType().getMaxDurability() - imHand.getDamage() - 2) <= 2) {
                        e.setCancelled(true);
                        e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ENTITY_IRON_GOLEM_STEP, 0.25f, 0.77f);
                    }
                }
            } else if (ToolListing.farmable.contains(block.getType())) {
                J.s(() -> e.getPlayer().getInventory().setItemInMainHand(omniTool.nextFnS(hand)));
                e.getPlayer().getWorld().playSound(e.getPlayer().getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
                if (imHand != null && imHand.hasDamage()) {
                    if ((hand.getType().getMaxDurability() - imHand.getDamage() - 2) <= 2) {
                        e.setCancelled(true);
                        e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ENTITY_IRON_GOLEM_STEP, 0.25f, 0.77f);
                    }
                }

            }

        }

    }

    @EventHandler
    public void on(PlayerDropItemEvent e) {
        if (!hasAdaptation(e.getPlayer())) {
            return;
        }
        if (e.getPlayer().isSneaking()) {
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
                    e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ENTITY_IRON_GOLEM_DEATH, 0.25f, 0.77f);
                    for (ItemStack i : drops) {
                        e.getPlayer().getWorld().dropItem(e.getPlayer().getLocation(), i);
                    }
                });
                e.getItemDrop().setItemStack(new ItemStack(Material.AIR));
            }
        }
    }

    @EventHandler
    public void on(BlockDamageEvent e) {
        if (!hasAdaptation(e.getPlayer())) {
            return;
        }
        org.bukkit.block.Block b = e.getBlock(); // nms block for pref tool
        ItemStack hand = e.getPlayer().getInventory().getItemInMainHand();

        if (!validateTool(hand)) {
            return;
        }
        Damageable imHand = (Damageable) hand.getItemMeta();
        if (ToolListing.getAxe().contains(b.getType())) {
            if (hand.getType().toString().contains("_AXE")) {
                return;
            }
            J.s(() -> e.getPlayer().getInventory().setItemInMainHand(omniTool.nextAxe(hand)));
            itemDelegate(e, hand, imHand);
        } else if (ToolListing.getShovel().contains(b.getType())) {
            if (hand.getType().toString().contains("_SHOVEL")) {
                return;
            }
            J.s(() -> e.getPlayer().getInventory().setItemInMainHand(omniTool.nextShovel(hand)));
            itemDelegate(e, hand, imHand);
        } else { // Default to pickaxe
            if (hand.getType().toString().contains("_PICKAXE")) {
                return;
            }
            J.s(() -> e.getPlayer().getInventory().setItemInMainHand(omniTool.nextPickaxe(hand)));
            itemDelegate(e, hand, imHand);
        }

    }


    @EventHandler
    public void on(InventoryClickEvent e) {
        if (!hasAdaptation((Player) e.getWhoClicked())) {
            return;
        }
        if (e.getClick().equals(ClickType.SHIFT_LEFT) && e.getAction().equals(InventoryAction.MOVE_TO_OTHER_INVENTORY)) {
            ItemStack cursor = e.getWhoClicked().getItemOnCursor().clone();
            ItemStack clicked = e.getClickedInventory().getItem(e.getSlot()).clone();

            if (omniTool.explode(cursor).size() > 1 || omniTool.explode(clicked).size() > 1) {
                if (omniTool.explode(cursor).size() >= getSlots(getLevel((Player) e.getWhoClicked())) || omniTool.explode(clicked).size() >= getSlots(getLevel((Player) e.getWhoClicked()))) {
                    e.setCancelled(true);
                    ((Player) e.getWhoClicked()).playSound(e.getWhoClicked().getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.77f);
                    return;
                }
            }
            if (ToolListing.tools.contains(cursor.getType()) && ToolListing.tools.contains(clicked.getType())) { // TOOLS ONLY
                if (!cursor.getType().isAir() && !clicked.getType().isAir() && omniTool.supportsItem(cursor) && omniTool.supportsItem(clicked)) {
                    e.getWhoClicked().sendMessage("DWAaa");
                    e.setCancelled(true);
                    e.getWhoClicked().setItemOnCursor(new ItemStack(Material.AIR));
                    e.getClickedInventory().setItem(e.getSlot(), omniTool.build(cursor, clicked));
                    e.getWhoClicked().getWorld().playSound(e.getWhoClicked().getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
                } else {
                    e.getWhoClicked().sendMessage("DWA");
                }
            }
        }

    }

    private void itemDelegate(BlockDamageEvent e, ItemStack hand, Damageable imHand) {
        e.getPlayer().getWorld().playSound(e.getPlayer().getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
        if (imHand != null && imHand.hasDamage()) {
            if ((hand.getType().getMaxDurability() - imHand.getDamage() - 2) <= 2) {
                e.setCancelled(true);
                e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ENTITY_IRON_GOLEM_STEP, 0.25f, 0.77f);
            }
        }
    }

    private boolean validateTool(ItemStack item) {
        if (item.getItemMeta() != null && item.getItemMeta().getLore() != null && item.getItemMeta().getLore().get(0) != null) {
            return (item.getItemMeta().getLore().get(0).contains("Leatherman"));
        } else {
            return false;
        }
    }

    private double getSlots(double level) {
        return getConfig().startingSlots + level;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 10;
        int initialCost = 3;
        double costFactor = 0.20;
        int maxLevel = 9;
        int startingSlots = 1;
    }
}
