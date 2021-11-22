package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.content.item.BoundEnderPearl;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;


public class EnderAccess extends SimpleAdaptation {
    public EnderAccess() {
        super("ender-access");
        setDescription("Pull from the void");
        setIcon(Material.NETHER_STAR);
        setBaseCost(0);
        setCostFactor(0);
        setMaxLevel(1);
        setInitialCost(10);
        setInterval(0);
    }

    private double getConsumePercent(int level) {
        return 0.15 + (0.15 * level);
    }


    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.ITALIC + "1: Sneak Left-Click a container with an Enderpearl to link");
        v.addLore(C.ITALIC + "2: Click the Resonating pearl to access remote inventory");
        v.addLore(C.ITALIC + "3: Sneak Click the air to unbind the pearl");
        v.addLore(C.RED + "[ SNEAK-CLICKING A BLOCK WILL THROW IT ]");
    }


    @EventHandler
    public void on(PlayerInteractEvent e) {  // THIS IS THE INITAL CREATOR
        if (getLevel(e.getPlayer()) > 0) {
            Player p = e.getPlayer();

            // ------------------------------------------------------------------------
            // ------------------------------BIND THE PEARL----------------------------
            // ------------------------------------------------------------------------
            if (e.getAction() == Action.LEFT_CLICK_BLOCK
                    && e.getPlayer().isSneaking()
                    && (e.getClickedBlock() == null  || e.getClickedBlock().getBlockData().getMaterial().equals(Material.CHEST) )
                    && e.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.ENDER_PEARL)) {
                ItemStack item = BoundEnderPearl.withData(e.getClickedBlock());
                item.addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1);


                p.getLocation().getWorld().playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.35f, 0.100f); // Not sure why i need to do this NONNULL here only
                p.getLocation().getWorld().playSound(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 3.35f, 0.500f);
                if (p.getInventory().getItemInMainHand().getAmount() == 1) {
                    p.getInventory().setItemInMainHand(null);
                    p.getInventory().addItem(item);
                    p.updateInventory();

                } else {
                    p.getInventory().getItemInMainHand().setAmount(p.getInventory().getItemInMainHand().getAmount() - 1);
                    p.getInventory().addItem(item);

                }
                p.updateInventory();
                Adapt.info("Bound Enderpearl to : " + e.getClickedBlock().getLocation());

            }
            // ------------------------------------------------------------------------
            // ------------------------------USING THE PEARL --------------------------
            // ------------------------------------------------------------------------

            if (!e.getPlayer().isSneaking()
                    && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK)
                    && e.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.ENDER_PEARL)
                    && BoundEnderPearl.getChest(e.getPlayer().getInventory().getItemInMainHand()) != null) {
                e.setCancelled(true);
                Adapt.info("Using EnderPeral");

                p.getLocation().getWorld().playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.35f, 0.100f); // Not sure why i need to do this NONNULL here only
                p.getLocation().getWorld().playSound(p.getLocation(), Sound.BLOCK_LODESTONE_PLACE, 1.35f, 0.100f);

                Block bc = BoundEnderPearl.getChest(e.getPlayer().getInventory().getItemInMainHand());
                Block chest = bc.getWorld().getBlockAt(bc.getLocation());

                if (chest.getState() instanceof InventoryHolder holder) {
                    Inventory inventory = holder.getInventory();
                    e.getPlayer().openInventory(inventory);
                }
                p.updateInventory();
            }

            // ------------------------------------------------------------------------
            // ------------------------------RESET THE PEARL --------------------------
            // ------------------------------------------------------------------------

            //TODO FIX WHEN RIGHTCLICKING A BLOCK IT TOSSES
            if (e.getPlayer().isSneaking()
                    && (e.getClickedBlock() == null  || !e.getClickedBlock().getBlockData().getMaterial().equals(Material.CHEST) )
                    && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_AIR)
                    && e.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.ENDER_PEARL)
                    && e.getPlayer().getInventory().getItemInMainHand().getItemMeta() != null) {

                if (p.getInventory().getItemInMainHand().getAmount() == 1) {
                    p.getInventory().setItemInMainHand(null);
                    p.getInventory().setItemInMainHand(new ItemStack(Material.ENDER_PEARL, 1));
                    p.updateInventory();
                } else {
                    p.getInventory().getItemInMainHand().setAmount(p.getInventory().getItemInMainHand().getAmount() - 1);
                    p.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 1));
                }
            }
        }
    }


    @Override
    public void onTick() {

    }

}