package com.volmit.adapt.content.adaptation.stealth;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.content.adaptation.stealth.util.EntityListing;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StealthAggro extends SimpleAdaptation<StealthAggro.Config> {
    private final Map<Player, Long> cooldowns;
    private final List<Player> inZone;

    public StealthAggro() {
        super("stealth-aggro");
        registerConfiguration(Config.class);
        setDescription("When you sneak, you release a blast that De-aggros mobs, and de-buffs players");
        setDisplayName("Stealth Aggro");
        setIcon(Material.POTION);
        setBaseCost(getConfig().baseCost);
        setInterval(300);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        cooldowns = new HashMap<>();
        inZone = new ArrayList<>();
    }


    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + "On Sneak, you release a blast that De-aggros mobs, and de-buffs players");
        v.addLore(C.RED + "Debuff Range:  " + level + 3 + "x Radius " + C.GRAY + "Debuff Range");
        v.addLore(C.RED + "Aggro Range:  " + 2 * (level + 3) + "x Radius " + C.GRAY + "Aggro Range");
        v.addLore(C.RED + "Duration: " + level + "x Seconds");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityTargetEvent e) {
        if (EntityListing.getAggroMobs().contains(e.getEntityType())) {
            for (Player p : inZone) {
                if (e.getTarget() instanceof Player pp && pp.equals(p)) {
                    p.sendMessage(C.RED + "You are De-aggroed!");
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerToggleSneakEvent e) {
//        if (e.isSneaking() || cooldowns.containsKey(e.getPlayer())) {
//            return;
//        }
        cooldowns.put(e.getPlayer(), M.ms());
        createZone(e.getPlayer().getLocation(), getLevel(e.getPlayer()), e.getPlayer());

    }


    public void createZone(Location l, int level, Player p) {
        p.sendMessage(C.RED + "You are De-aggroed!");
        J.a(() -> {
            double iterator = 0.5;
            double inner = 0.5;
            double outer = (level * (2 + 1.5));

            vfxSphereV1(p, l, outer, Particle.DRIP_LAVA, 30, 60);
            

        });

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
        int initialCost = 5;
        double costFactor = 0.3;
        int maxLevel = 7;
    }
}
