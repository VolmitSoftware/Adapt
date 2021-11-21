package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.KMap;
import com.volmit.adapt.util.M;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;

public class AgilityWindUp extends SimpleAdaptation {
    private final KMap<Player, Integer> ticksRunning = new KMap<>();

    public AgilityWindUp() {
        super("wind-up");
        setDescription("Get faster the longer you sprint!");
        setIcon(Material.POWERED_RAIL);
        setBaseCost(2);
        setCostFactor(0.65);
        setInitialCost(8);
        setInterval(50);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getWindupSpeed(getLevelPercent(level)), 0) + C.GRAY + " Max Speed");
        v.addLore(C.YELLOW + "* " + Form.duration(getWindupTicks(getLevelPercent(level)) * 50D, 1) + C.GRAY + " Windup Time");
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        ticksRunning.remove(e.getPlayer());
    }

    private double getWindupTicks(double factor) {
        return M.lerp(180, 60, factor);
    }

    private double getWindupSpeed(double factor) {
        return 0.22 + (factor * 0.225);
    }

    @Override
    public void onTick() {
        for(Player i : Bukkit.getOnlinePlayers()) {
            for(AttributeModifier j : i.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getModifiers()) {
                if(j.getName().equals("adapt-wind-up")) {
                    i.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).removeModifier(j);
                }
            }

            if(i.isSwimming() || i.isFlying() || i.isGliding() || i.isSneaking()) {
                ticksRunning.remove(i);
                return;
            }

            if(i.isSprinting() && getLevel(i) > 0) {
                ticksRunning.compute(i, (k, v) -> {
                    if(v == null) {
                        return 1;
                    }

                    return v + 1;
                });

                Integer tr = ticksRunning.get(i);

                if(tr == null || tr <= 0) {
                    continue;
                }

                double factor = getLevelPercent(i);
                double ticksToMax = getWindupTicks(factor);
                double progress = Math.min(M.lerpInverse(0, ticksToMax, tr), 1);
                double speedIncrease = M.lerp(0, getWindupSpeed(factor), progress);

                if(M.r(0.2 * progress)) {
                    i.getWorld().spawnParticle(Particle.LAVA, i.getLocation(), 1);
                }

                if(M.r(0.25 * progress)) {
                    i.getWorld().spawnParticle(Particle.FLAME, i.getLocation(), 1, 0, 0, 0, 0);
                }

                i.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).addModifier(new AttributeModifier("adapt-wind-up", speedIncrease, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            } else {
                ticksRunning.remove(i);
            }
        }
    }
}
