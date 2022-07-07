package com.volmit.adapt.content.adaptation.stealth;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class StealthSight extends SimpleAdaptation<StealthSight.Config> {
    public StealthSight() {
        super("stealth-vision");
        registerConfiguration(Config.class);
        setDescription("Gain night vision while sneaking");
        setDisplayName("Stealth Vision");
        setIcon(Material.POTION);
        setBaseCost(getConfig().baseCost);
        setInterval(2000);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);

    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + "Gain a burst of " + C.GREEN + "night vision" + C.GRAY + " while sneaking");
    }

    @EventHandler
    public void on(PlayerToggleSneakEvent e) {
        if (!hasAdaptation(e.getPlayer())) {
            return;
        }
        if (!e.getPlayer().isSneaking()) {
            Player p = e.getPlayer();
            p.playSound(p.getLocation(), Sound.BLOCK_FUNGUS_BREAK, 1, 0.99f);
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20000, 0, false, false));
        } else {
            e.getPlayer().removePotionEffect(PotionEffectType.NIGHT_VISION);
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
        int baseCost = 2;
        int initialCost = 5;
        double costFactor = 0.6;
        double factor = 1.25;
        int maxLevel = 1;
    }
}
