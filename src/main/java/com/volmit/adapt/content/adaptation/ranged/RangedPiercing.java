package com.volmit.adapt.content.adaptation.ranged;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ProjectileLaunchEvent;

import java.util.ArrayList;
import java.util.List;

public class RangedPiercing extends SimpleAdaptation<RangedPiercing.Config> {
    private final List<Integer> holds = new ArrayList<>();

    public RangedPiercing() {
        super("ranged-piercing");
        registerConfiguration(Config.class);
        setDescription("Adds Piercing to projectiles! Shoot through things!");
        setDisplayName("Ranged Piercing");
        setIcon(Material.SHEARS);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(5000);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + level + C.GRAY + " Piercing");
    }

    @EventHandler
    public void on(ProjectileLaunchEvent e) {
        if(e.getEntity().getShooter() instanceof Player) {
            if(e.getEntity() instanceof AbstractArrow a) {
                Player p = ((Player) e.getEntity().getShooter());

                if(getLevel(p) > 0) {
                    a.setPierceLevel(((AbstractArrow) e.getEntity()).getPierceLevel() + getLevel(p));
                }
            }
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
        int baseCost = 3;
        int maxLevel = 5;
        int initialCost = 8;
        double costFactor = 0.5;
    }
}
