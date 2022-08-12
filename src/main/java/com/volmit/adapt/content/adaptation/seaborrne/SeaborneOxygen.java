package com.volmit.adapt.content.adaptation.seaborrne;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class SeaborneOxygen extends SimpleAdaptation<SeaborneOxygen.Config> {
    private final List<Integer> holds = new ArrayList<>();

    public SeaborneOxygen() {
        super("seaborne-oxygen");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Seaborn","SeabornOxygen", "Description"));
        setDisplayName(Adapt.dLocalize("Seaborn","SeabornOxygen", "Name"));
        setIcon(Material.GLASS_PANE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(3750);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getAirBoost(level), 0) + C.GRAY + Adapt.dLocalize("Seaborn","SeabornOxygen", "Lore1"));
    }

    public int getRealMaxAir(int level) {
        return (int) ((getAirBoost(level) * 300) + 300);
    }

    public double getAirBoost(int level) {
        return getLevelPercent(level) * getConfig().airFactor;
    }

    @Override
    public void onTick() {
        for(Player i : Bukkit.getOnlinePlayers()) {
            if(getLevel(i) > 0) {
                i.setMaximumAir(getRealMaxAir(getLevel(i)));
            }
        }
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
        int initialCost = 5;
        double costFactor = 0.525;
        double airFactor = 4.55;
    }
}
