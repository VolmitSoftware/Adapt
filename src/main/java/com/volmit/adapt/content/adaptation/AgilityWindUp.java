package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

public class AgilityWindUp extends SimpleAdaptation {
    private KMap<Player, Integer> ticksRunning = new KMap<>();

    public AgilityWindUp() {
        super("wind-up");
        setDescription("Get faster the longer you run straight!");
        setIcon(Material.POWERED_RAIL);
        setBaseCost(2);
        setInterval(50);
    }

    @EventHandler
    public void on(PlayerQuitEvent e)
    {
        ticksRunning.remove(e.getPlayer());
    }

    @Override
    public void onTick() {
        for(Player i : Bukkit.getOnlinePlayers())
        {
            for(AttributeModifier j : i.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getModifiers())
            {
                if(j.getName().equals("adapt-wind-up"))
                {
                    i.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).removeModifier(j);
                }
            }

            if(i.isSprinting() && getLevel(i) > 0)
            {
                ticksRunning.compute(i, (k,v) -> {
                    if(v == null)
                    {
                        return 1;
                    }

                    return v+1;
                });

                Integer tr = ticksRunning.get(i);

                if(tr == null || tr <= 0)
                {
                    continue;
                }

                double factor = getLevelPercent(i);
                double ticksToMax = M.lerp(180, 60, factor);

                if(tr == ticksToMax)
                {
                    i.getWorld().playSound(i.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 0.01f);
                    i.getWorld().spawnParticle(Particle.FLASH, i.getLocation(), 1);
                }

                double progress = Math.min(M.lerpInverse(0, ticksToMax, tr), factor > 0.8 ? 1.25 : 1);
                double speedIncrease = M.lerp(0, 0.22 + (factor * 0.225), progress);


                if(progress < 0.9)
                {
                    i.getWorld().playSound(i.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, (float) (0.1f * (1d - progress)), (float) 1f + ((float) (progress * 1f)));
                }

                if(M.r(0.2 * progress))
                {
                    i.getWorld().spawnParticle(Particle.LAVA, i.getLocation(), 1);
                }

                if(M.r(0.35 * progress))
                {
                    i.getWorld().spawnParticle(Particle.FLAME, i.getLocation(), 1, 0, 0, 0, 0);
                }

                i.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).addModifier(new AttributeModifier("adapt-wind-up", speedIncrease, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            }

            else {
                ticksRunning.remove(i);
            }
        }
    }
}
