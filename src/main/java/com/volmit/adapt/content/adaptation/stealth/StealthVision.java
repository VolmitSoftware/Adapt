package com.volmit.adapt.content.adaptation.stealth;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;

import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class StealthVision extends SimpleAdaptation<StealthVision.Config> {

    public StealthVision() {
        super("stealth-vision");
        registerConfiguration(Config.class);
        setDescription("Reveal the unseen...");
        setIcon(Material.NETHERITE_BOOTS);
        setBaseCost(getConfig().baseCost);
        setInterval(2666);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore( C.GRAY + " Stealth Vision, reveal nearby entities");
    }

    @EventHandler
    public void on(PlayerToggleSneakEvent e) {

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
        int baseCost = 2;
        int initialCost = 5;
        double costFactor = 0.6;
        double factor = 1.25;
    }
}
