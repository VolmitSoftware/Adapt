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

public class HunterRegen extends SimpleAdaptation<HunterRegen.Config> {
    public HunterRegen() {
        super("hunter-regen");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("HunterRegen.Description"));
        setDisplayName(Adapt.dLocalize("HunterRegen.Name"));
        setIcon(Material.AXOLOTL_BUCKET);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + Adapt.dLocalize("HunterRegen.Lore1"));
        v.addLore(C.GREEN + "+ " + level + C.GRAY + Adapt.dLocalize("HunterRegen.Lore2"));
        v.addLore(C.RED + "- " + 5+level + C.GRAY + Adapt.dLocalize("HunterRegen.Lore3"));
        v.addLore(C.GRAY + "* " + level + C.GRAY + Adapt.dLocalize("HunterRegen.Lore4"));
        v.addLore(C.GRAY + "* " + level + C.GRAY + Adapt.dLocalize("HunterRegen.Lore5"));
    }


    @EventHandler
    public void on(EntityDamageEvent e) {
        if(e.getEntity() instanceof org.bukkit.entity.Player p && !e.getCause().equals(EntityDamageEvent.DamageCause.STARVATION) && hasAdaptation(p)) {
            addPotionStacks(p, PotionEffectType.HUNGER, 5 + getLevel(p), 100, true);
            addPotionStacks(p, PotionEffectType.REGENERATION, getLevel(p), 50, false);
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
