package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.KList;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ProjectileLaunchEvent;

public class RangedPiercing extends SimpleAdaptation {
    private final KList<Integer> holds = new KList<>();
    public RangedPiercing() {
        super("piercing");
        setDescription("Adds Piercing to projectiles! Shoot through things!");
        setIcon(Material.SHEARS);
        setBaseCost(3);
        setMaxLevel(3);
        setInterval(5000);
        setInitialCost(8);
        setCostFactor(0.5);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + level + C.GRAY + " Piercing");
    }

    @EventHandler
    public void on(ProjectileLaunchEvent e)
    {
        if(e.getEntity().getShooter() instanceof Player)
        {
            if(e.getEntity() instanceof AbstractArrow)
            {
                Player p = ((Player)e.getEntity().getShooter());

                if(getLevel(p) > 0)
                {
                    ((AbstractArrow)e.getEntity()).setPierceLevel(((AbstractArrow)e.getEntity()).getPierceLevel() + getLevel(p));
                }
            }
        }
    }

    @Override
    public void onTick() {

    }
}
