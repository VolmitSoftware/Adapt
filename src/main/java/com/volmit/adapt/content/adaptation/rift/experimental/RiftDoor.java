package com.volmit.adapt.content.adaptation.rift.experimental;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.content.item.BoundDoor;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class RiftDoor extends SimpleAdaptation<RiftDoor.Config> {


    //TODO: ADD RECIPE OR CONSUMPTION COST
    public RiftDoor() {
        super("rift-door");
        setDescription("Almost Dimensional-Doors");
        setIcon(Material.IRON_DOOR);
        setBaseCost(0);
        setCostFactor(0);
        setMaxLevel(1);
        setInitialCost(25);
        setInterval(100);
        registerConfiguration(Config.class);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "BROKEN DONT USE THIS");
        v.addLore(C.GREEN + "Sneak Left-CLick to create point 1");
        v.addLore(C.GREEN + "Sneak Left-CLick again, to set second point");
        v.addLore(C.ITALIC + "CONSUMES ON USE, links 2 points in 1 space");
    }


    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        Location location = null;

        if(e.getClickedBlock() == null) {
            location = e.getPlayer().getLocation();

        } else {
            location = new Location(e.getClickedBlock().getLocation().getWorld(),
                e.getClickedBlock().getLocation().getX() + 0.5,
                e.getClickedBlock().getLocation().getY() + 1,
                e.getClickedBlock().getLocation().getZ() + 0.5);
        }

        if(!hasAdaptation(p) || (!hand.getType().equals(Material.ENDER_EYE))) {
            return;
        }
        e.setCancelled(true);

        switch(e.getAction()) {
            case LEFT_CLICK_BLOCK, LEFT_CLICK_AIR -> {
                if(p.isSneaking() && isBound(hand)) { // pop portal open
                    openEye(p, hand, location);
                } else if(p.isSneaking() && !isBound(hand)) {// Start portal process
                    linkEye(p, location);

                }
            }

        }

    }


    private void openEye(Player p, ItemStack hand, Location location) {
        Location l = BoundDoor.getLocation(p.getInventory().getItemInMainHand());
        Location l2 = location;

        getSkill().xp(p, 75); // xp time
        if(hand.getAmount() > 1) { // consume the hand
            hand.setAmount(hand.getAmount() - 1);
        } else {
            p.getInventory().setItemInMainHand(null);
        }


        p.playSound(l, Sound.BLOCK_ENDER_CHEST_OPEN, 10f, 0.1f);
        p.playSound(l, Sound.BLOCK_END_PORTAL_FRAME_FILL, 10f, 0.1f);

        p.spawnParticle(Particle.ASH, l, 1, 0, 1, 0, 0);
        p.getWorld().strikeLightningEffect(l);
        l.getBlock().setType(Material.SOUL_FIRE);

        p.spawnParticle(Particle.ASH, l2, 1, 0, 2, 0, 0);
        p.getWorld().strikeLightningEffect(l);
        l2.getBlock().setType(Material.SOUL_FIRE);
    }

    private boolean isBound(ItemStack stack) {
        return BoundDoor.getLocation(stack) != null;

    }

    private void linkEye(Player p, Location location) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        ItemStack eye = BoundDoor.withData(location);


        if(hand.getAmount() == 1) {
            BoundDoor.setData(hand, location);
        } else {
            hand.setAmount(hand.getAmount() - 1);
            p.getInventory().addItem(eye).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
        }
    }


    @Override
    public void onTick() {
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
    }
}