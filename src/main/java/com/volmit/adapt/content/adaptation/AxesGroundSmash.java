package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class AxesGroundSmash extends SimpleAdaptation {
    private final KList<Integer> holds = new KList<>();

    public AxesGroundSmash() {
        super("ground-smash");
        setDescription("Jump, then crouch and smash all nearby enemies.");
        setIcon(Material.NETHERITE_AXE);
        setBaseCost(6);
        setCostFactor(0.75);
        setMaxLevel(5);
        setInitialCost(8);
        setInterval(5000);
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e)
    {
        if(e.getDamager() instanceof Player && getLevel((Player) e.getDamager()) > 0 && ((Player) e.getDamager()).isSneaking())
        {
            if(!isAxe(((Player) e.getDamager()).getInventory().getItemInMainHand()))
            {
                return;
            }

            double f = getLevelPercent((Player) e.getDamager());

            if(((Player) e.getDamager()).hasCooldown(((Player) e.getDamager()).getInventory().getItemInMainHand().getType()))
            {
                return;
            }

            ((Player) e.getDamager()).setCooldown(((Player) e.getDamager()).getInventory().getItemInMainHand().getType(), getCooldownTime(f));
            new Impulse(getRadius(f))
                    .damage(getDamage(f), getFalloffDamage(f))
                    .force(getForce(f))
                    .punch(e.getEntity().getLocation());
            e.getEntity().getWorld().playSound(e.getEntity().getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.HOSTILE,0.6f, 0.4f);
            e.getEntity().getWorld().playSound(e.getEntity().getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.HOSTILE,0.5f, 0.1f);
            e.getEntity().getWorld().playSound(e.getEntity().getLocation(), Sound.ENTITY_TURTLE_EGG_CRACK, SoundCategory.HOSTILE,1f, 0.4f);

        }
    }

    @Override
    public void addStats(int level, Element v) {
        double f = getLevelPercent(level);
        v.addLore(C.RED + "+ " + Form.f(getFalloffDamage(f), 1) + " - " + Form.f(getDamage(f), 1) + C.GRAY + " Damage");
        v.addLore(C.RED + "+ " + Form.f(getRadius(f), 1) + C.GRAY + " Block Radius");
        v.addLore(C.RED + "+ " + Form.pc(getForce(f), 0) + C.GRAY + " Force");
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownTime(getLevelPercent(level)) * 50D, 1) + C.GRAY + " Smash Cooldown");
    }

    public int getCooldownTime(double factor)
    {
        return (int) (((1D - factor) * 225) + 80);
    }

    public double getRadius(double factor)
    {
        return 6 * factor;
    }

    public double getDamage(double factor)
    {
        return 8 * factor;
    }

    public double getForce(double factor)
    {
        return (1.15 * factor) + 0.27;
    }

    public double getFalloffDamage(double factor)
    {
        return 3 * factor;
    }

    @Override
    public void onTick() {

    }
}
