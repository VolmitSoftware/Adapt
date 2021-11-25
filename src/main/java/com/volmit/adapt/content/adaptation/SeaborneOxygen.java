package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.KList;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class SeaborneOxygen extends SimpleAdaptation<SeaborneOxygen.Config> {
    private final KList<Integer> holds = new KList<>();

    public SeaborneOxygen() {
        super("oxygen");
        setDescription("Hold more oxygen!");
        setIcon(Material.GLASS_PANE);
        setBaseCost(3);
        setMaxLevel(5);
        setInterval(3750);
        setInitialCost(5);
        setCostFactor(0.525);
        registerConfiguration(Config.class);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getAirBoost(level), 0) + C.GRAY + " Oxygen");
    }

    public int getRealMaxAir(int level) {
        return (int) ((getAirBoost(level) * 300) + 300);
    }

    public double getAirBoost(int level) {
        return getLevelPercent(level) * 4.55;
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
    }
}
