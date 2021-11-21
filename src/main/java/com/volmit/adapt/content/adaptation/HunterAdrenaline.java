package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class HunterAdrenaline extends SimpleAdaptation {
    public HunterAdrenaline() {
        super("adrenaline");
        setDescription("Deal more damage the lower health you are (Melee)");
        setIcon(Material.LEATHER_HELMET);
        setBaseCost(4);
        setMaxLevel(5);
        setInitialCost(8);
        setCostFactor(0.4);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getDamage(level), 0) + C.GRAY + " Max Damage");
    }

    private double getDamage(int level) {
        return ((getLevelPercent(level) * 0.26) + 0.07);
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if(e.getDamager() instanceof Player && (getLevel((Player) e.getDamager()) > 0)) {
            double damageMax = getDamage(getLevel((Player) e.getDamager()));
            double hpp = ((Player) e.getDamager()).getHealth() / ((Player) e.getDamager()).getMaxHealth();

            if(hpp >= 1) {
                return;
            }

            damageMax *= (1D - hpp);
            e.setDamage(e.getDamage() * (damageMax + 1D));
        }
    }

    @Override
    public void onTick() {

    }
}
