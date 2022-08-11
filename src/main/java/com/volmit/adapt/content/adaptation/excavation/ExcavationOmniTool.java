package com.volmit.adapt.content.adaptation.excavation;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.content.item.ItemListings;
import com.volmit.adapt.content.item.multiItems.OmniTool;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
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
        setDisplayName(Adapt.dLocalize("OmniTool.Name"));
        setDescription(Adapt.dLocalize("OmniTool.Description"));
        setIcon(Material.DISC_FRAGMENT_5);
        setInterval(20202);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + Adapt.dLocalize("OmniTool.Lore1"));
        v.addLore(C.GRAY + "" + (level) + C.GRAY + Adapt.dLocalize("OmniTool.Lore2"));
        v.addLore(C.GREEN + Adapt.dLocalize("OmniTool.Lore3"));
        v.addLore(C.RED + Adapt.dLocalize("OmniTool.Lore4"));
        v.addLore(C.GRAY + Adapt.dLocalize("OmniTool.Lore5"));
        v.addLore(C.GREEN + "" + (level + getConfig().startingSlots) + C.GRAY + Adapt.dLocalize("OmniTool.Lore6"));
        v.addLore(C.UNDERLINE + Adapt.dLocalize("OmniTool.Lore7"));


    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public void onTick() {
    }

    @EventHandler(priority = EventPriority.MONITOR)
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockBreakEvent e) {
        if (!hasAdaptation(e.getPlayer())) {
            return;
        }
        if (!validateTool(e.getPlayer().getInventory().getItemInMainHand())) {
            return;
        }
        xp(e.getPlayer(), 3);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
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
            if (ItemListings.farmable.contains(block.getType())) {
                J.s(() -> e.getPlayer().getInventory().setItemInMainHand(omniTool.nextHoe(hand)));
                e.getPlayer().getWorld().playSound(e.getPlayer().getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
                if (imHand != null && imHand.hasDamage()) {
                    if ((hand.getType().getMaxDurability() - imHand.getDamage() - 2) <= 2) {
                        e.setCancelled(true);
                        e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ENTITY_IRON_GOLEM_STEP, 0.25f, 0.77f);
                    }
                }
            } else if (ItemListings.farmable.contains(block.getType())) {
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

    @EventHandler(priority = EventPriority.HIGHEST)
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

    @EventHandler(priority = EventPriority.MONITOR)
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
        if (ItemListings.getAxe().contains(b.getType())) {
            if (hand.getType().toString().contains("_AXE")) {
                return;
            }
            J.s(() -> e.getPlayer().getInventory().setItemInMainHand(omniTool.nextAxe(hand)));
            itemDelegate(e, hand, imHand);
        } else if (ItemListings.getShovel().contains(b.getType())) {
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


    @EventHandler(priority = EventPriority.HIGHEST)
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
            if (ItemListings.tool.contains(cursor.getType()) && ItemListings.tool.contains(clicked.getType())) { // TOOLS ONLY
                if (!cursor.getType().isAir() && !clicked.getType().isAir() && omniTool.supportsItem(cursor) && omniTool.supportsItem(clicked)) {
                    e.setCancelled(true);
                    e.getWhoClicked().setItemOnCursor(new ItemStack(Material.AIR));
                    e.getClickedInventory().setItem(e.getSlot(), omniTool.build(cursor, clicked));
                    e.getWhoClicked().getWorld().playSound(e.getWhoClicked().getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
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
