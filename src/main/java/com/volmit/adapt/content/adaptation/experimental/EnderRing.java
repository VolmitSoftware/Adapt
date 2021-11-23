package com.volmit.adapt.content.adaptation.experimental;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Objects;


public class EnderRing extends SimpleAdaptation {
    public EnderRing() {
        super("ender-ring");
        setDescription("THIS DOES NOTHING; AND IS JUST A PROOF OF CONCEPT FOR LATER USE");
        setIcon(Material.OBSIDIAN);
        setBaseCost(10);
        setCostFactor(0.5);
        setMaxLevel(5);
        setInitialCost(5);
        setInterval(9726);
    }

    private double getPhasePercent(int level) {
        return 0.10 + (0.05 * level);
    }

    private double getPhaseCd(int level) {
        return 5500 - (500 * level);
    }

    private int getPoints(int level) {
        return 5 + (10 * level);
    }

    private int getRange(int level) {
        return 2 + level;
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ ");
        v.addLore(C.YELLOW + "~ ");
        v.addLore(C.RED + "- ");
    }


    @EventHandler
    public void on(PlayerInteractEvent e) {
        if (getLevel(e.getPlayer()) > 0) {
            Player p = e.getPlayer();
            int points = getPoints(getLevel(p)); //amount of points to be generated

            double d = getRange(getLevel(p));
            J.a(() -> {
                for (int i = 0; i < 360; i += 360 / points) {
                    double angle = (i * Math.PI / 180);
                    double x = d * Math.cos(angle);
                    double z = d * Math.sin(angle);
                    Location loc = p.getLocation().add(x, 1, z);
                    Objects.requireNonNull(p.getLocation().getWorld()).spawnParticle(Particle.ASH, loc, 1);
                }
            });
        }
    }


    @Override
    public void onTick() {


    }
}