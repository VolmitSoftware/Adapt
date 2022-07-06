package com.volmit.adapt.content.adaptation.rift;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
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

//    private boolean isLookingUp(Player player) {
//        double pitch = player.getLocation().getPitch();
//        return pitch >= 45.0 || pitch <= -45.0;
//    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + (getBlinkDistance(level)) + C.GRAY + " Blocks on blink (2x Vertical)");
        v.addLore(C.ITALIC + "While Sprinting: Double tap Jump to " + C.DARK_PURPLE + "Blink");
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        lastJump.remove(e.getPlayer());
    }

    @EventHandler
    public void on(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (hasAdaptation(e.getPlayer()) && p.getGameMode().equals(GameMode.SURVIVAL)) {
            e.setCancelled(true);
            p.setAllowFlight(false);

            if (lastJump.get(p) != null && M.ms() - lastJump.get(p) <= getCooldownDuration(getLevel(p))) {
                return;
            }
            if (p.isSprinting()) {
                Vector v = p.getVelocity().clone();
                Location loc = p.getLocation().clone();
                Location locOG = p.getLocation().clone();
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
                    lastJump.put(p, M.ms());
                    return;
                }

                if(getPlayer(p).getData().getSkillLine(getSkill().getName()).getAdaptationLevel(new RiftResist().getName()) > 0){ // This is the Rift Resist adaptation
                    riftResistCheckAndTrigger(p, 30, 2);
                }

                p.teleport(loc.add(0, 1, 0));
                vfxParticleLine(locOG, loc, Particle.REVERSE_PORTAL, 50, 8, 0.1D, 1D, 0.1D, 0D, null, false, l -> l.getBlock().isPassable());
                J.a(() -> {
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                    p.setVelocity(v.multiply(3));
                });

                lastJump.put(p, M.ms());

                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.25f, 1.0f);
                vfxLevelUp(p);

            }
        }
    }

    public boolean isSafe(Location l) {
        return l.getBlock().getType().isSolid() && !l.getBlock().getRelative(BlockFace.UP).getType().isSolid() && !l.getBlock().getRelative(BlockFace.UP).getRelative(BlockFace.UP).getType().isSolid();
    }

    @EventHandler
    public void on(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (hasAdaptation(e.getPlayer()) && p.getGameMode().equals(GameMode.SURVIVAL)) {
//            if (isLookingUp(p)) {
//                p.setAllowFlight(false);
//                return;
//            }

            if (lastJump.get(p) != null && M.ms() - lastJump.get(p) <= getCooldownDuration(getLevel(p))) {
                p.setAllowFlight(false);
                return;
            }

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
                return;
            } else if (isSafe(loc)) {
                p.setAllowFlight(e.getPlayer().getFallDistance() < 4.5 && e.getPlayer().isSprinting());
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
        int baseCost = 15;
        double costFactor = 1;
        int maxLevel = 5;
        int initialCost = 5;
        double baseDistance = 6;
        double distanceFactor = 5;
    }
}