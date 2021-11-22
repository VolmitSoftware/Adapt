package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
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
        v.addLore(C.ITALIC + "1: Shift Left-Click a container with an Enderpearl to link");
        v.addLore(C.ITALIC + "2: Right-Click the Resonating pearl to access the inventory");
        v.addLore(C.ITALIC + "3: Shift Right-Click the air to unbind the pearl");
//        v.addLore(C.RED + "ONE TIME USE");
    }


    @EventHandler
    public void onPlayerClicks(PlayerInteractEvent e) {  // THIS IS THE INITAL CREATOR
        if (getLevel(e.getPlayer()) > 0) {
            Player p = e.getPlayer();


            // ------------------------------------------------------------------------
            // ------------------------------BIND THE PEARL----------------------------
            // ------------------------------------------------------------------------

            if (e.getAction() == Action.LEFT_CLICK_BLOCK
                    && e.getPlayer().isSneaking()
                    && e.getClickedBlock().getType() == Material.CHEST
                    && e.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.ENDER_PEARL)) {

                ItemStack ep = new ItemStack(Material.ENDER_PEARL, 1);
                ep.addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1);

                List<String> locList = Arrays.asList( // Ill fix later / Bad checking
                        "Resonating to...",
                        "World: " + Objects.requireNonNull(e.getClickedBlock().getLocation().getWorld()).getName(),
                        "X: " + e.getClickedBlock().getLocation().getBlockX(),
                        "Y: " + e.getClickedBlock().getLocation().getBlockY(),
                        "Z: " + e.getClickedBlock().getLocation().getBlockZ());
                ItemMeta im = ep.getItemMeta(); // Make meta
                if (im != null) {
                    im.setLore(locList);

                }

                ep.setItemMeta(im); // Save Meta
                p.getLocation().getWorld().playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.35f, 0.100f); // Not sure why i need to do this NONNULL here only
                p.getLocation().getWorld().playSound(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 3.35f, 0.500f);
                if (p.getInventory().getItemInMainHand().getAmount() == 1) {
                    p.getInventory().setItemInMainHand(null);
                } else {
                    p.getInventory().getItemInMainHand().setAmount(p.getInventory().getItemInMainHand().getAmount() - 1);
                }
                p.updateInventory();
                p.getPlayer().getInventory().addItem(ep);

            }
            // ------------------------------------------------------------------------
            // ------------------------------USING THE PEARL --------------------------
            // ------------------------------------------------------------------------

            if (!e.getPlayer().isSneaking()
                    && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)
                    && e.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.ENDER_PEARL)
                    && e.getPlayer().getInventory().getItemInMainHand().getItemMeta() != null
                    && e.getPlayer().getInventory().getItemInMainHand().getItemMeta().getLore() != null
                    && e.getPlayer().getInventory().getItemInMainHand().getItemMeta().getLore().get(0).equals("Resonating to...")) {
                e.setCancelled(true);


                List<String> itemMeta = e.getPlayer().getInventory().getItemInMainHand().getItemMeta().getLore();
                p.getLocation().getWorld().playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.35f, 0.100f); // Not sure why i need to do this NONNULL here only
                p.getLocation().getWorld().playSound(p.getLocation(), Sound.BLOCK_LODESTONE_PLACE, 1.35f, 0.100f);


                World w = Bukkit.getWorld(itemMeta.get(1).split(": ", 2)[1]);
                double x = Double.parseDouble(itemMeta.get(2).split(": ", 2)[1]);
                double y = Double.parseDouble(itemMeta.get(3).split(": ", 2)[1]);
                double z = Double.parseDouble(itemMeta.get(4).split(": ", 2)[1]);

                Location loc = new Location(w, x, y, z);
                Block chest = loc.getWorld().getBlockAt(loc);

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
                    && (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK)
                    && e.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.ENDER_PEARL)
                    && (e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.CHEST)
                    && e.getPlayer().getInventory().getItemInMainHand().getItemMeta() != null
                    && e.getPlayer().getInventory().getItemInMainHand().getItemMeta().getLore() != null
                    && e.getPlayer().getInventory().getItemInMainHand().getItemMeta().getLore().get(0).equals("Resonating to...")) {

                Adapt.info("MADE IT HERE");

                if (p.getInventory().getItemInMainHand().getAmount() == 1) {
                    p.getInventory().setItemInMainHand(new ItemStack(Material.ENDER_PEARL, 1));
                } else {
                    p.getInventory().getItemInMainHand().setAmount(p.getInventory().getItemInMainHand().getAmount() - 1);
                    p.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 1));
                }

                e.setCancelled(true);

            }
            p.updateInventory();
        }
    }


    public static String removeLastChars(String str, int chars) {
        return str.substring(0, str.length() - chars);
    }


    @Override
    public void onTick() {

    }

}