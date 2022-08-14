package com.volmit.adapt.content.adaptation.discovery;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.M;
import lombok.NoArgsConstructor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.HashMap;
import java.util.Map;

public class DiscoveryXpResist extends SimpleAdaptation<DiscoveryXpResist.Config> {
    private final Map<Player, Long> cooldowns;

    public DiscoveryXpResist() {
        super("discovery-xp-resist");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Discovery", "DiscoveryXpResist", "Description"));
        setDisplayName(" " + Adapt.dLocalize("Discovery", "DiscoveryXpResist", "Name"));
        setIcon(Material.EMERALD);
        setInterval(5211);
        setBaseCost(getConfig().baseCost);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        cooldowns = new HashMap<>();
    }


    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + C.GRAY + Adapt.dLocalize("Discovery", "DiscoveryXpResist", "Lore0"));
        v.addLore(C.GREEN + "+ " + Form.pc(getEffectiveness(getLevelPercent(level)), 0) + C.GRAY + Adapt.dLocalize("Discovery", "DiscoveryXpResist", "Lore1"));
        v.addLore(C.GREEN + "+ " + getXpTaken(level) + C.GRAY + Adapt.dLocalize("Discovery", "DiscoveryXpResist", "Lore2"));
    }

    private double getEffectiveness(double factor) {
        return Math.min(getConfig().maxEffectiveness, factor * factor + getConfig().effectivenessBase);
    }

    private int getXpTaken(double level) {
        double d = (getConfig().levelCostAdd * getConfig().amplifier) - (level * getConfig().levelDrain);
        return (int) d;
    }

    @EventHandler
    public void on(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && hasAdaptation(p)) {
            if (getTotalExp(p) < getXpTaken(getLevel(p))) {
                vfxFastRing(p.getLocation().add(0, 0.05, 0), 1, Color.RED);
                p.playSound(p.getLocation(), Sound.BLOCK_FUNGUS_BREAK, 15, 0.01f);
                return;
            }
            if (!cooldowns.containsKey(p) || (cooldowns.containsKey(p) && M.ms() - cooldowns.get(p) > 15000)) {
                e.setDamage(e.getDamage() - (e.getDamage() * (getEffectiveness(getLevelPercent(getLevel(p))))));
                xp(p, 5);
                cooldowns.put(p, M.ms());
                takeExp(p, getXpTaken(getLevel(p)));
                vfxFastRing(p.getLocation().add(0, 0.05, 0), 1, Color.LIME);
                p.playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_REPAIR, 3, 0.01f);
                p.playSound(p.getLocation(), Sound.BLOCK_SHROOMLIGHT_HIT, 15, 0.01f);
            } else {
                vfxFastRing(p.getLocation().add(0, 0.05, 0), 1, Color.RED);
                p.playSound(p.getLocation(), Sound.BLOCK_FUNGUS_BREAK, 15, 0.01f);
                return;
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
        int baseCost = 5;
        int initialCost = 3;
        double costFactor = 0.8;
        int maxLevel = 5;
        double effectivenessBase = 0.15;
        double maxEffectiveness = 0.95;
        int levelDrain = 2;
        int levelCostAdd = 12;
        double amplifier = 1.0;
    }
}
