package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.KMap;
import com.volmit.adapt.util.M;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;


public class AgilitySuperJump extends SimpleAdaptation<AgilitySuperJump.Config> {
    private final KMap<Player, Long> lastJump = new KMap<>();

    public AgilitySuperJump() {
        super("super-jump");
        setDescription("Exceptional Height Advantage");
        setIcon(Material.LEATHER_BOOTS);
        setBaseCost(2);
        setCostFactor(0.55);
        setMaxLevel(5);
        setInitialCost(5);
        setInterval(9344);
        registerConfiguration(Config.class);
    }

    private double getJumpHeight(int level) {
        return 0.43 + (0.07 * level);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + (1 + (0.5 * level)) + C.GRAY + " Max Additional Blocks");
        v.addLore(C.YELLOW + "* 1 Complete Jump From Floor" + C.GRAY + " *kinda buggy* ");
    }

    @EventHandler
    public void on(PlayerToggleSneakEvent e) {
        if(!hasAdaptation(e.getPlayer())) {
            return;
        }

        if(e.isSneaking() && e.getPlayer().isOnGround()) {
            e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.3f, 0.35f);
        }
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        lastJump.remove(e.getPlayer());
    }

    @EventHandler
    public void on(PlayerMoveEvent e) {
        Player p = e.getPlayer();

        if(p.isSwimming() || p.isFlying() || p.isGliding() || p.isSprinting()) {
            return;
        }

        if(p.isSneaking() && getLevel(p) > 0) {
            Vector velocity = p.getVelocity();

            if(velocity.getY() > 0) {
                double jumpVelocity = 0.4;
                PotionEffect jumpPotion = p.getPotionEffect(PotionEffectType.JUMP);

                if(jumpPotion != null) {
                    jumpVelocity += (double) ((float) jumpPotion.getAmplifier() + 1) * 0.1F;
                }

                if(lastJump.get(p) != null && M.ms() - lastJump.get(p) < 1000) {
                    return;
                } else if(lastJump.get(p) != null && M.ms() - lastJump.get(p) > 1500) {
                    lastJump.remove(p);
                }

                if(p.getLocation().getBlock().getType() != Material.LADDER && velocity.getY() > jumpVelocity && p.isOnline()) {
                    p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.25f, 0.7f);
                    p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.25f, 1.7f);
                    p.getWorld().spawnParticle(Particle.BLOCK_CRACK, p.getLocation().clone().add(0, 0.3, 0), 15, 0.1, 0.8, 0.1, 0.1, p.getLocation().getBlock().getRelative(BlockFace.DOWN).getBlockData());
                    p.setVelocity(p.getVelocity().setY(getJumpHeight(getLevel(p))));
                    lastJump.put(p, M.ms());
                }
            }
        }
    }

    @Override
    public void onTick() {

    }

    protected static class Config {
    }
}