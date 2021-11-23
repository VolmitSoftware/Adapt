package com.volmit.adapt.content.adaptation.experimental;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.SneakyThrows;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Objects;


public class RiftRing extends SimpleAdaptation {
    public RiftRing() {
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


    private int getPoints(int level) {
        return 40;
    }


    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ ");
        v.addLore(C.YELLOW + "~ ");
        v.addLore(C.RED + "- ");
    }


    @SneakyThrows
    @EventHandler
    public void on(PlayerInteractEvent e) {
        if (getLevel(e.getPlayer()) > 0) {
            Player p = e.getPlayer();
            int points = getPoints(getLevel(p)); //amount of points to be generated
            Location l = e.getPlayer().getLocation();

            double d = 2;
            double pcd = 1000;
            double y = 0.1;

            p.playSound(l, Sound.BLOCK_LODESTONE_PLACE, 5.35f, 0.1f);
            while (pcd > 0) {
                for (int i = 0; i < 360; i += 360 / points) {
                    double angle = (i * Math.PI / 180);
                    double x = d * Math.cos(angle);
                    double z = d * Math.sin(angle);
                    Location loc = p.getLocation().add(x, y, z);
                    Objects.requireNonNull(p.getLocation().getWorld()).spawnParticle(Particle.ASH, loc, 1, 0, 0, 0, 0);
                }
                pcd = pcd - 20;
                d = d - 0.04;
                y=y*1.07;
                Thread.sleep(5);
            }
            vfxLevelUp(p);
            p.getLocation().getWorld().playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 5.35f, 0.1f);
        }
    }


    @Override
    public void onTick() {

    }
}