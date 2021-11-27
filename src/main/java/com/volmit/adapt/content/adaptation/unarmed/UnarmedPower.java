package com.volmit.adapt.content.adaptation.unarmed;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class UnarmedPower extends SimpleAdaptation<UnarmedPower.Config> {
    public UnarmedPower() {
        super("power-of-the-fist");
        registerConfiguration(Config.class);
        setDescription("Improved Unarmed Damage");
        setIcon(Material.LEATHER_HELMET);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 3;
        int maxLevel = 7;
        int initialCost = 6;
        double costFactor = 0.425;
        double damageFactor = 2.57;
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getUnarmedDamage(level), 0) + C.GRAY + " Damage");
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if(e.getDamager() instanceof Player) {
            Player p = (Player) e.getDamager();
            double factor = getLevelPercent(p);

            if(factor <= 0) {
                return;
            }

            e.setDamage(e.getDamage() * (1 + getUnarmedDamage(getLevel(p))));
            getSkill().xp(p, 0.321 * factor * e.getDamage());

        }
    }

    private double getUnarmedDamage(int level) {
        return getLevelPercent(level) * getConfig().damageFactor;
    }

    @Override
    public void onTick() {

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }
}
