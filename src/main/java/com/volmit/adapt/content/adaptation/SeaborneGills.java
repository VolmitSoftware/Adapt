package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class SeaborneGills extends SimpleAdaptation {
    private KList<Integer> holds = new KList<Integer>();
    public SeaborneGills() {
        super("gills");
        setDescription("Exchange your lungs for gills!");
        setIcon(Material.TURTLE_EGG);
        setBaseCost(1);
        setMaxLevel(1);
        setInterval(50);
        setInitialCost(20);
        setCostFactor(0.225);
    }

    @Override
    public void addStats(int level, Element v) {
    }

    @EventHandler
    public void on(PlayerMoveEvent e)
    {
        if(e.getFrom().getWorld().equals(e.getTo().getWorld()) && getLevel(e.getPlayer()) > 0)
        {
            if(!(e.getPlayer().getEyeLocation().getBlock().getType().equals(Material.WATER) || (e.getPlayer().getEyeLocation().getBlock().getBlockData() instanceof Waterlogged && ((Waterlogged) e.getPlayer().getEyeLocation().getBlock().getBlockData()).isWaterlogged())))
            {
                double effort = e.getFrom().distance(e.getTo()) * 5;
                e.getPlayer().setRemainingAir((int) (e.getPlayer().getRemainingAir() - effort));
            }
        }
    }

    @Override
    public void onTick() {
        for(Player i : Bukkit.getOnlinePlayers())
        {
            boolean cw = false;
            if(getLevel(i) > 0)
            {
                int newAir = i.getRemainingAir();

                if(i.getEyeLocation().getBlock().getType().equals(Material.WATER) || (i.getEyeLocation().getBlock().getBlockData() instanceof Waterlogged && ((Waterlogged) i.getEyeLocation().getBlock().getBlockData()).isWaterlogged()))
                {
                    newAir += 4;

                    if(newAir < 0)
                    {
                        newAir = 1;
                    }
                }

                else
                {
                    newAir -= 5;

                    if(i.getLocation().getBlock().getType().equals(Material.WATER) || (i.getLocation().getBlock().getBlockData() instanceof Waterlogged && ((Waterlogged) i.getLocation().getBlock().getBlockData()).isWaterlogged()))
                    {
                        newAir += 3;
                        cw = true;
                    }
                }

                if(newAir < -125 && cw)
                {
                    newAir = -125;
                }

                if(newAir > i.getMaximumAir())
                {
                    newAir = i.getMaximumAir();
                }

                if(newAir < -300)
                {
                    newAir = -300;
                }

                if(newAir < -200)
                {
                    if(M.r(0.1))
                    {
                        J.s(() -> {
                            i.damage(1);
                        });
                    }
                }

                else if(newAir < -25 && !cw)
                {
                   J.s(() -> {
                       i.setExhaustion(i.getExhaustion() + 0.07f);
                       i.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0));
                       i.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 20, 0));
                       i.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20, 0));
                   });
                }

                if(newAir != i.getRemainingAir())
                {
                    int na = newAir;
                    J.s(() -> {
                        i.setRemainingAir(na);
                    });
                }
            }
        }
    }
}
