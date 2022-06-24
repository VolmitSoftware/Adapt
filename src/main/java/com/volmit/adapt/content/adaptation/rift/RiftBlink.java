package com.volmit.adapt.content.adaptation.rift;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.util.Vector;


public class RiftBlink extends SimpleAdaptation<RiftBlink.Config> {
    private final KMap<Player, Long> lastJump = new KMap<>();

    public RiftBlink() {
        super("rift-blink");
        registerConfiguration(Config.class);
        setDescription("Just a blink away");
        setIcon(Material.FEATHER);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(9288);
    }

    private double getBlinkDistance(int level) {
        return getConfig().baseDistance + (getLevelPercent(level) * getConfig().distanceFactor);
    }

    private int getCooldownDuration(int level) {
        return 2000;
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + (getBlinkDistance(level)) + C.GRAY + " Blocks on blink");
        v.addLore(C.ITALIC + "While Sprinting: Double tap Jump to " + C.DARK_PURPLE + "Blink");
    }


    @EventHandler
    public void on(PlayerQuitEvent e) {
        lastJump.remove(e.getPlayer());
    }

    @EventHandler
    public void on(PlayerToggleFlightEvent e) { // not trying to do this :(
        Player p = e.getPlayer();
        if (!hasAdaptation(e.getPlayer()) || !p.getGameMode().equals(GameMode.SURVIVAL)) {
            return;
        } else {
            e.setCancelled(true);
        }

        if (lastJump.get(p) != null && M.ms() - lastJump.get(p) <= getCooldownDuration(getLevel(p))) {
            return;
        }


        if (hasAdaptation(e.getPlayer()) && p.isSprinting()) {

            lastJump.put(p, M.ms());
            Location loc = p.getLocation().clone();
            Vector dir = loc.getDirection();
            double dist = getBlinkDistance(getLevel(p));
            dir.multiply(dist);
            loc.add(dir);
            double cd = dist * 2;
            loc.subtract(0, dist, 0);

            while (!isSafe(loc) && cd-- > 0) {
                loc.add(0, 1, 0);
            }

            if (!isSafe(loc)) {
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_CONDUIT_DEACTIVATE, 1f, 1.24f);
                return;
            }
            vfxLevelUp(p);
            Vector v = p.getVelocity().clone().multiply(3);

            p.teleport(loc.add(0, 1, 0));
            J.a(() -> {
                try {Thread.sleep(15);} catch (InterruptedException ex) {throw new RuntimeException(ex);}
                p.setVelocity(v);
            });

            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.25f, 1.0f);
            vfxLevelUp(p);

        }
    }

    public boolean isSafe(Location l) {
        return l.getBlock().getType().isSolid() && !l.getBlock().getRelative(BlockFace.UP).getType().isSolid() && !l.getBlock().getRelative(BlockFace.UP).getRelative(BlockFace.UP).getType().isSolid();
    }

    @EventHandler
    public void on(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if(hasAdaptation(e.getPlayer()) && !p.getGameMode().equals(GameMode.SURVIVAL)){
            return;
        }

        if (e.getPlayer().getFallDistance() < 4.5 && e.getPlayer().isSprinting()) {
            p.setAllowFlight(true);
        } else {
            p.setAllowFlight(false);
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
        int baseCost = 15;
        double costFactor = 1;
        int maxLevel = 5;
        int initialCost = 5;
        double baseDistance = 6;
        double distanceFactor = 5;
    }
}