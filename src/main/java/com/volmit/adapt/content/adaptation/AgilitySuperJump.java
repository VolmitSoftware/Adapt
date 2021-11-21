package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.M;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;


public class AgilitySuperJump extends SimpleAdaptation {

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

    private double getJumpHeight(int level) {
        return 0.43 + (0.07 * level);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc((getJumpHeight(level)), 0) + C.GRAY + " Max Additional Blocks");
        v.addLore(C.YELLOW + "* 1 Second" + C.GRAY + " Windup Time");
    }

    @Override
    public void onTick() {

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isSneaking() && getLevel(p) > 0) {

                int level = getLevel(p);
                Vector velocity = p.getVelocity();
                if (velocity.getY() > 0) {
                    double jumpVelocity = 0.41999998688697815; // Default jump velocity 0.333f is the alt, not sure why the height is 0.333f as a result of this randomly...
                    PotionEffect jumpPotion = p.getPotionEffect(PotionEffectType.JUMP);

                    if (jumpPotion != null) { // potion Garbage
                        jumpVelocity += (double) ((float) jumpPotion.getAmplifier() + 1) * 0.1F;
                    }
                    if (p.getLocation().getBlock().getType() != Material.LADDER && Double.compare(velocity.getY(), jumpVelocity) == 0) {
                        p.getLocation().getWorld().playSound(p.getLocation(), Sound.BLOCK_LAVA_POP, 1.25f, 0.100f);
                        p.setVelocity(p.getVelocity().setY(getJumpHeight(level)));
                    }

                }
            }
            // the rest
            if (p.isSwimming() || p.isFlying() || p.isGliding() || p.isSprinting()) {
                return;
            }
        }
    }
}

