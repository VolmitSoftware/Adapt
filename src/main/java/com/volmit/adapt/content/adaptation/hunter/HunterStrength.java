package com.volmit.adapt.content.adaptation.hunter;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;

public class HunterStrength extends SimpleAdaptation<HunterStrength.Config> {
    public HunterStrength() {
        super("hunter-strength");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Hunter", "HunterStrength", "Description"));
        setDisplayName(Adapt.dLocalize("Hunter", "HunterStrength", "Name"));
        setIcon(Material.COD_BUCKET);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + Adapt.dLocalize("Hunter", "HunterStrength", "Lore1"));
        v.addLore(C.GREEN + "+ " + level + C.GRAY + Adapt.dLocalize("Hunter", "HunterStrength", "Lore2"));
        v.addLore(C.RED + "- " + 5+level + C.GRAY + Adapt.dLocalize("Hunter", "HunterStrength", "Lore3"));
        v.addLore(C.GRAY + "* " + level + C.GRAY + " " +Adapt.dLocalize("Hunter", "HunterStrength", "Lore4"));
        v.addLore(C.GRAY + "* " + level + C.GRAY + " " +Adapt.dLocalize("Hunter", "HunterStrength", "Lore5"));
    }


    @EventHandler
    public void on(EntityDamageEvent e) {
        if(e.getEntity() instanceof org.bukkit.entity.Player p && !e.getCause().equals(EntityDamageEvent.DamageCause.STARVATION) && hasAdaptation(p)) {
            addPotionStacks(p, PotionEffectType.HUNGER, 5 + getLevel(p), 100, true);
            addPotionStacks(p, PotionEffectType.INCREASE_DAMAGE, getLevel(p), 50, false);
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
        int baseCost = 4;
        int maxLevel = 5;
        int initialCost = 8;
        double costFactor = 0.4;
    }
}
