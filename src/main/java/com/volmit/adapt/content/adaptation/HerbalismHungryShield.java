package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

public class HerbalismHungryShield extends SimpleAdaptation {
    private final KList<Integer> holds = new KList<>();
    public HerbalismHungryShield() {
        super("hungry-shield");
        setDescription("Take damage to your hunger before your health");
        setIcon(Material.APPLE);
        setBaseCost(7);
        setMaxLevel(5);
        setInterval(875);
        setInitialCost(14);
        setCostFactor(0.925);
    }

    @Override
    public void onTick() {

    }

    private double getEffectiveness(double factor)
    {
        return Math.min(0.9, factor * factor + (0.09));
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getEffectiveness(getLevelPercent(level)), 0) + C.GRAY + " Resisted by Hunger");
    }


    @EventHandler
    public void on(EntityDamageEvent e) {
        if(e.getEntity() instanceof Player && getLevel((Player) e.getEntity()) > 0)
        {
            double f = getEffectiveness(getLevelPercent((Player) e.getEntity()));
            double h = e.getDamage() * f;
            double d = e.getDamage() - h;
            Player p = (Player) e.getEntity();

            if(p.getFoodLevel() >= h)
            {
                p.setFoodLevel((int) (p.getFoodLevel() - h));
            }

            else
            {
                h -= p.getFoodLevel();
                p.setFoodLevel(0);
                d += h;
            }

            e.setDamage(d);
        }
    }
}
