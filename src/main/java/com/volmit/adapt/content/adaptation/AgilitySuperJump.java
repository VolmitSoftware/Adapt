package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class AgilitySuperJump extends SimpleAdaptation {
    private final KMap<Player, Integer> ticksCrouching = new KMap<>();


    public AgilitySuperJump() {
        super("super-jump");
        setDescription("Exceptional Height Advantage");
        setIcon(Material.LEATHER_BOOTS);
        setBaseCost(2);
        setCostFactor(0.55);
        setMaxLevel(5);
        setInitialCost(5);
        setInterval(50);
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        ticksCrouching.remove(e.getPlayer());
    }

    private double getChargeTime(double factor) {
        return M.lerp(60, 20, factor);
    }

    private double getJumpHeight(int level) {
        return 0.625 + (getLevelPercent(level) * 0.225);
    }


    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc((getJumpHeight(level)), 0) + C.GRAY + " Max Additional Height");
        v.addLore(C.YELLOW + "* " + Form.duration(getChargeTime(getLevelPercent(level)) * 50D, 1) + C.GRAY + " Charge Time");

    }


    public void on(PlayerToggleSneakEvent e) {

    }


    @Override
    public void onTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isSneaking() && getLevel(p) > 0) {
                int level = getLevel(p); // Get level
                Vector velocity = p.getVelocity();

                if (velocity.getY() > 0) {
                    double jumpVelocity = (double) 0.42F; // Default jump velocity
                    PotionEffect jumpPotion = p.getPotionEffect(PotionEffectType.JUMP);

                    if (jumpPotion != null) {
                        // If player has jump potion add it to jump velocity
                        jumpVelocity += (double) ((float) jumpPotion.getAmplifier() + 1) * 0.1F;
                    }
                    // Check if player is not on ladder and if jump velocity calculated is equals to player Y velocity
                    if (p.getLocation().getBlock().getType() != Material.LADDER && Double.compare(velocity.getY(), jumpVelocity) == 0 && p.isSneaking()) {

                        p.sendMessage("You jumped... i think");
                        p.setVelocity(p.getVelocity().setY(getJumpHeight(level)));
                        p.getLocation().getWorld().playSound(p.getLocation(), Sound.BLOCK_LODESTONE_PLACE, 1f, 0.7f);
                        p.resetMaxHealth();
                    }
                }

//                    //TODO Ensure they press space while crouching
//                    p.setVelocity(p.getVelocity().setY(getJumpHeight(level)));
//                    p.getLocation().getWorld().playSound(p.getLocation(), Sound.BLOCK_LODESTONE_PLACE, 1f, 0.7f);


            }


            if (p.isSwimming() || p.isFlying() || p.isGliding() || p.isSprinting()) {
                ticksCrouching.remove(p);
                return;
            } else {
                ticksCrouching.remove(p);
            }
        }
    }
}

