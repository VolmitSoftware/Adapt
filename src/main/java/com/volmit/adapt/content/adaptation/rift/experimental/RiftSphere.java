package com.volmit.adapt.content.adaptation.rift.experimental;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Objects;


public class RiftSphere extends SimpleAdaptation<RiftSphere.Config> {
    public RiftSphere() {
        super("rift-sphere");
        setDescription("THIS DOES NOTHING; AND IS JUST A PROOF OF CONCEPT FOR LATER USE");
        setIcon(Material.CRYING_OBSIDIAN);
        setBaseCost(10);
        setCostFactor(0.5);
        setMaxLevel(5);
        setInitialCost(5);
        setInterval(9826);
        registerConfiguration(Config.class);
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
    public void onPlayerMove(PlayerMoveEvent e) {

        if(getLevel(e.getPlayer()) > 0) {
            Player p = e.getPlayer();
            int points = getPoints(getLevel(p)); //amount of points to be generated
            for(int i = 0; i < 360; i += 360 / points) {
                double angle = (i * Math.PI / 180);
                double x = getRange(getLevel(p)) * Math.cos(angle);
                double z = getRange(getLevel(p)) * Math.sin(angle);
                Location loc = p.getLocation().add(x, 1, z);
                Objects.requireNonNull(p.getLocation().getWorld()).spawnParticle(Particle.ASH, loc, 1);

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
    }
}