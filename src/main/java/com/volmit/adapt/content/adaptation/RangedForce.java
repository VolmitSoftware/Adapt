package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.KList;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ProjectileLaunchEvent;

public class RangedForce extends SimpleAdaptation {
    private final KList<Integer> holds = new KList<>();
    public RangedForce() {
        super("force");
        setDescription("Shoot projectiles further, faster!");
        setIcon(Material.ARROW);
        setBaseCost(2);
        setMaxLevel(7);
        setInterval(5000);
        setInitialCost(5);
        setCostFactor(0.225);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getSpeed(getLevelPercent(level)), 0) + C.GRAY + " Projectile Speed");
    }

    private double getSpeed(double factor)
    {
        return (factor*1.135);
    }

    @EventHandler
    public void on(ProjectileLaunchEvent e)
    {
        if(e.getEntity().getShooter() instanceof Player)
        {
            Player p = ((Player)e.getEntity().getShooter());

            if(getLevel(p) > 0)
            {
                double factor = getLevelPercent(p);
                e.getEntity().setVelocity(e.getEntity().getVelocity().clone().multiply(1 + getSpeed(factor)));
                e.getEntity().getWorld().playSound(e.getEntity().getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.5f + ((float)factor * 0.25f), 0.7f + (float)(factor/2f));
            }
        }
    }

    @Override
    public void onTick() {

    }
}
