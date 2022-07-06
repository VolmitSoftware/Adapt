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

public class UnarmedGlassCannon extends SimpleAdaptation<UnarmedGlassCannon.Config> {
    public UnarmedGlassCannon() {
        super("unarmed-glass-cannon");
        registerConfiguration(Config.class);
        setDescription("Bonus Unarmed Damage the lower your armor value is");
        setDisplayName("Glass Cannon");
        setIcon(Material.DISC_FRAGMENT_5);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }


    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + (getConfig().maxDamageFactor + (level * getConfig().maxDamagePerLevelMultiplier)) + "x Damage at 0 armor");
        v.addLore(C.GREEN + "+ " + Form.f(level * getConfig().perLevelBonusMultiplier) + " PerLevel Bonus Damage");
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            if (!hasAdaptation(p)) {
                return;
            }
            if (p.getInventory().getItemInMainHand().getType() != Material.AIR || p.getInventory().getItemInOffHand().getType() != Material.AIR) {
                return;
            }
            double armor = getArmorValue(p);
            double damage = e.getDamage();
            if (armor == 0) {
                e.setDamage(damage * getConfig().maxDamageFactor);
            } else {
                e.setDamage(damage - (damage * armor));
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
        int maxLevel = 7;
        int initialCost = 6;
        double costFactor = 0.425;
        double perLevelBonusMultiplier = 0.25;
        double maxDamageFactor = 4.0;
        double maxDamagePerLevelMultiplier = 0.15;
    }

}
