package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.content.item.BoundEyeOfEnder;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Objects;

public class RiftGate extends SimpleAdaptation<RiftGate.Config> {
    public RiftGate() {
        super("rift-gate");
        registerConfiguration(Config.class);
        setDescription("Move through the void");
        setIcon(Material.END_PORTAL_FRAME);
        setBaseCost(0);
        setCostFactor(0);
        setMaxLevel(1);
        setInitialCost(30);
        setInterval(50);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "Right-Click to recall to a location");
        v.addLore(C.ITALIC + "5s delay, " + C.RED + "you can die in this");
    }


    @EventHandler
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

        if(!hasAdaptation(p) || (!hand.getType().equals(Material.ENDER_EYE) && !isBound(hand))) {
            return;
        }
        e.setCancelled(true);

        switch(e.getAction()) {
            case LEFT_CLICK_BLOCK -> {
                if(p.isSneaking()) {
                    linkEye(p, location);
                }
            }
            case LEFT_CLICK_AIR -> {
                if(p.isSneaking() && isBound(hand)) {
                    unlinkEye(p);
                } else if(p.isSneaking() && !isBound(hand)) {
                    linkEye(p, location);
                }
            }

            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> { // use
                openEye(p);
            }

        }

    }


    private void openEye(Player p) {
        Location l = BoundEyeOfEnder.getLocation(p.getInventory().getItemInMainHand());
        ItemStack hand = p.getInventory().getItemInMainHand();

        getSkill().xp(p, 75);
        if(hand.getAmount() > 1) { // consume the hand
            hand.setAmount(hand.getAmount() - 1);
        } else {
            p.getInventory().setItemInMainHand(null);
        }

        // port animation

        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 1, true, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 80, 0, true, false, false));
        p.playSound(l, Sound.BLOCK_LODESTONE_PLACE, 100f, 0.1f);
        p.playSound(l, Sound.BLOCK_BELL_RESONATE, 100f, 0.1f);
        J.a(() -> {
            double d = 2;
            double pcd = 1000;
            double y = 0.1;
            while(pcd > 0) {
                for(int i = 0; i < 360; i += 360 / 25) {

                    double angle = (i * Math.PI / 180);
                    double x = d * Math.cos(angle);
                    double z = d * Math.sin(angle);
                    Location loc = p.getLocation().add(x, y, z);
                    Objects.requireNonNull(p.getLocation().getWorld()).spawnParticle(Particle.ASH, loc, 1, 0, 0, 0, 0);
                }
                pcd = pcd - 20;
                d = d - 0.04;
                y = y * 1.07;
                J.sleep(80);
            }
            vfxLevelUp(p);
            p.getLocation().getWorld().playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 5.35f, 0.1f);

            J.s(() -> {
                p.teleport(l);
            });
        });

    }

    private boolean isBound(ItemStack stack) {
        return stack.getType().equals(Material.ENDER_EYE) && BoundEyeOfEnder.getLocation(stack) != null;
    }


    private void unlinkEye(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();

        if(hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
            p.getInventory().setItemInMainHand(null);
        }

        ItemStack eye = new ItemStack(Material.ENDER_EYE);
        p.getInventory().addItem(eye).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
    }

    private void linkEye(Player p, Location location) {
        ItemStack hand = p.getInventory().getItemInMainHand();

        if(hand.getAmount() == 1) {
            BoundEyeOfEnder.setData(hand, location);
        } else {
            hand.setAmount(hand.getAmount() - 1);
            ItemStack eye = BoundEyeOfEnder.withData(location);
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