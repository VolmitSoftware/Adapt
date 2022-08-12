package com.volmit.adapt.content.adaptation.discovery;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerExpChangeEvent;

public class DiscoveryUnity extends SimpleAdaptation<DiscoveryUnity.Config> {
    public DiscoveryUnity() {
        super("discovery-unity");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Discovery", "DiscoveryUnity", "Description"));
        setDisplayName(Adapt.dLocalize("Discovery", "DiscoveryUnity", "Name"));
        setIcon(Material.REDSTONE);
        setBaseCost(getConfig().baseCost);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getXPGained(getLevelPercent(level), 1), 0) + Adapt.dLocalize("Discovery", "DiscoveryUnity", "Lore1") + C.GRAY + Adapt.dLocalize("Discovery", "DiscoveryUnity", "Lore2"));
    }

    @EventHandler(priority = EventPriority.LOW)
    public void on(PlayerExpChangeEvent e) {
        if (e.getAmount() > 0 && getLevel(e.getPlayer()) > 0) {
            e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.9f);
            getPlayer(e.getPlayer()).boostXPToRandom(getPlayer(e.getPlayer()), e.getAmount() * getConfig().xpBoostMultiplier, getConfig().xpBoostDuration);
            getPlayer(e.getPlayer()).giveXPToRandom(getPlayer(e.getPlayer()), getXPGained(getLevelPercent(e.getPlayer()), e.getAmount()));
        }
    }

    private double getXPGained(double factor, int amount) {
        return amount * getConfig().xpGainedMultiplier * factor;
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
        int initialCost = 3;
        double costFactor = 0.3;
        int maxLevel = 7;
        double xpGainedMultiplier = 8;
        double xpBoostMultiplier = 0.01;
        int xpBoostDuration = 15000;
    }
}
