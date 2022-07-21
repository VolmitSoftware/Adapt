package com.volmit.adapt.content.adaptation.excavation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.content.adaptation.excavation.util.ToolListing;
import com.volmit.adapt.content.item.multiItems.OmniTool;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class ExcavationOmniTool extends SimpleAdaptation<ExcavationOmniTool.Config> {
    private static final OmniTool omniTool = new OmniTool();

    public ExcavationOmniTool() {
        super("excavation-omnitool");
        registerConfiguration(ExcavationOmniTool.Config.class);
        setDisplayName("Trusty T.O.O.L.");
        setDescription("A Craftable Leatherman Tool, swapping based on needs.");
        setIcon(Material.DISC_FRAGMENT_5);
        setInterval(20202);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + "This skill, Probably the most powerful of  many allows you to dynamically");
        v.addLore(C.GRAY + "merge and change tools on the fly, based on your needs.");
        v.addLore(C.ITALIC + "to merge, just shift click an item over another in your inventory.");
        v.addLore(C.RED + "THIS CANT BE UNDONE (yet) AND PERMANENTLY MERGES THE TOOLs");
        v.addLore(C.RED + "Should you lose this skill, or items, you will lose your tools capabilities.");
        v.addLore(C.GREEN + "" + (level + 3) + C.GRAY + "total merge-able items");

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
        if (e.getDamager() instanceof Player p && hasAdaptation(p)) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            //TODO: Check pref for Axe or Sword
            J.s(() -> p.getInventory().setItemInMainHand(omniTool.nextSword(hand)));
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
        }
    }

    @EventHandler
    public void on(PlayerInteractEvent e){
        if(e.getAction().equals(Action.RIGHT_CLICK_BLOCK) && hasAdaptation(e.getPlayer())){
            ItemStack hand = e.getPlayer().getInventory().getItemInMainHand();
            Block block = e.getClickedBlock();
            if (block == null){
                return;
            }
            if(ToolListing.farmable.contains(block.getType())){
                J.s(() -> e.getPlayer().getInventory().setItemInMainHand(omniTool.nextHoe(hand)));
                e.getPlayer().getWorld().playSound(e.getPlayer().getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
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

        if (ToolListing.getAxe().contains(b.getType())) {
            if (hand.getType().toString().contains("_AXE")) {
                return;
            }
            J.s(() -> e.getPlayer().getInventory().setItemInMainHand(omniTool.nextAxe(hand)));
            e.getPlayer().getWorld().playSound(e.getPlayer().getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
        } else if (ToolListing.getShovel().contains(b.getType())) {
            if (hand.getType().toString().contains("_SHOVEL")) {
                return;
            }
            J.s(() -> e.getPlayer().getInventory().setItemInMainHand(omniTool.nextShovel(hand)));
            e.getPlayer().getWorld().playSound(e.getPlayer().getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
        } else { // Default to pickaxe
            if (hand.getType().toString().contains("_PICKAXE")) {
                return;
            }
            J.s(() -> e.getPlayer().getInventory().setItemInMainHand(omniTool.nextPickaxe(hand)));
            e.getPlayer().getWorld().playSound(e.getPlayer().getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
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

            if (!ToolListing.tools.contains(cursor.getType()) && !ToolListing.tools.contains(clicked.getType())) { // TOOLS ONLY
                return;
            }

            if (!cursor.getType().isAir() && !clicked.getType().isAir() && omniTool.supportsItem(cursor) && omniTool.supportsItem(clicked)) {
                e.getWhoClicked().sendMessage("Omnitool: Combined " + cursor.getType() + " with " + clicked.getType());
                e.setCancelled(true);
                e.getWhoClicked().setItemOnCursor(new ItemStack(Material.AIR));
                e.getClickedInventory().setItem(e.getSlot(), omniTool.build(cursor, clicked));
                e.getWhoClicked().getWorld().playSound(e.getWhoClicked().getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
            }
        }

    }

    public void canuse(){

    }


    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 15;
        int initialCost = 5;
        double costFactor = 1;
        int maxLevel = 1;
    }
}
