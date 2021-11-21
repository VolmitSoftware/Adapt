package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Objects;
import java.util.Random;


public class OtherworldStorage extends SimpleAdaptation {
    public OtherworldStorage() {
        super("other-storage");
        setDescription("Glimpse into the void");
        setIcon(Material.ENDER_CHEST);
        setBaseCost(25);
        setCostFactor(0.5);
        setMaxLevel(5);
        setInitialCost(0);
        setInterval(2);
    }

    private double getConsumePercent(int level) {
        return 0.15 + (0.15 * level);
    }


    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getConsumePercent(level)) + C.GRAY + " Chance to Resist Consumption");
        v.addLore(C.ITALIC + "*left click an Enderperal to use*");
    }


    @EventHandler
    public void onPlayerClicks(PlayerInteractEvent e) {
        if (getLevel(e.getPlayer()) > 0
                && e.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.ENDER_PEARL)
                && (e.getAction().equals(Action.LEFT_CLICK_AIR) || e.getAction().equals(Action.LEFT_CLICK_BLOCK))) {
            Player p = e.getPlayer();
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 45, 1000));
            p.openInventory(e.getPlayer().getEnderChest());
            Objects.requireNonNull(p.getLocation().getWorld()).playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.35f, 0.100f); // Not sure why i need to do this NONNULL here only
            p.getLocation().getWorld().playSound(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 5.35f, 0.10f);
            p.getLocation().getWorld().playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 5.35f, 0.10f);


            Random r = new Random();
            double rand = r.nextDouble();
            if (rand >= getConsumePercent(getLevel(p))) {

                if (p.getInventory().getItemInMainHand().getAmount() == 1) {
                    p.getInventory().setItemInMainHand(null);
                } else {
                    p.getInventory().getItemInMainHand().setAmount(p.getInventory().getItemInMainHand().getAmount() - 1);
                }
            }

        }
    }


    @Override
    public void onTick() {

    }
}