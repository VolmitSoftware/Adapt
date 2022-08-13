package com.volmit.adapt.content.adaptation.seaborrne;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public class SeaborneSpeed extends SimpleAdaptation<SeaborneSpeed.Config> {
    private final List<Integer> holds = new ArrayList<>();

    public SeaborneSpeed() {
        super("seaborne-speed");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Seaborn", "DolphinGrace", "Description"));
        setDisplayName(Adapt.dLocalize("Seaborn", "DolphinGrace", "Name"));
        setIcon(Material.TRIDENT);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(1020);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + Adapt.dLocalize("Seaborn", "DolphinGrace", "Lore1") + C.GREEN + (level) + C.GRAY + Adapt.dLocalize("Seaborn", "DolphinGrace", "Lore2"));
        v.addLore(C.ITALIC + Adapt.dLocalize("Seaborn", "DolphinGrace", "Lore3"));
    }

    @EventHandler
    public void on(EntityAirChangeEvent e) {
        if (e.getEntity() instanceof Player p && p.isSwimming() && hasAdaptation(p)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 50, getLevel(p)));
            p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, getLevel(p), getLevel(p)));
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
        int initialCost = 2;
        double costFactor = 0.525;
    }
}
