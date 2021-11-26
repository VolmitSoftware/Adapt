package com.volmit.adapt.content.adaptation.ranged;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.KList;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.util.Vector;

public class RangedLungeShot extends SimpleAdaptation<RangedLungeShot.Config> {
    private final KList<Integer> holds = new KList<>();

    public RangedLungeShot() {
        super("lunge-shot");
        registerConfiguration(Config.class);
        setDescription("360 NO SCOPE");
        setIcon(Material.FEATHER);
        setBaseCost(3);
        setMaxLevel(3);
        setInterval(5000);
        setInitialCost(8);
        setCostFactor(0.5);
    }

    private double getSpeed(double factor) {
        return (factor * 0.935);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getSpeed(getLevelPercent(level)), 0) + C.GRAY + " Speed");
    }

    @EventHandler
    public void on(ProjectileLaunchEvent e) {
        if(e.getEntity().getShooter() instanceof Player) {
            if(e.getEntity() instanceof AbstractArrow a) {
                Player p = ((Player) e.getEntity().getShooter());

                if(hasAdaptation(p)) {
                    if(!p.isOnGround()) {
                        Vector velocity = p.getPlayer().getLocation().getDirection().normalize().multiply(getSpeed(getLevelPercent(p)));
                        p.setVelocity(p.getVelocity().subtract(velocity));
                        p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_TURTLE, 1f, 0.75f);
                        p.getWorld().playSound(p.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 1.95f);

                        for(int i = 0; i < 9; i++) {
                            Vector v = velocity.clone().add(Vector.getRandom().subtract(Vector.getRandom()).multiply(0.3)).normalize();
                            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().clone().add(0, 1, 0), 0, v.getX(), v.getY(), v.getZ(), 0.2);
                        }
                    }
                }
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
