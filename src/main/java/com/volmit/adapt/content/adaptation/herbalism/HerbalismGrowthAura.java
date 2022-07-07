package com.volmit.adapt.content.adaptation.herbalism;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.M;
import com.volmit.adapt.util.RNG;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class HerbalismGrowthAura extends SimpleAdaptation<HerbalismGrowthAura.Config> {
    private final List<Integer> holds = new ArrayList<>();

    public HerbalismGrowthAura() {
        super("herbalism-growth-aura");
        registerConfiguration(Config.class);
        setDescription("Grow nature around you in an aura");
        setDisplayName("Growth Aura");
        setIcon(Material.BONE_MEAL);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(850);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    private double getRadius(double factor) {
        return factor * getConfig().radiusFactor;
    }

    private double getStrength(int level) {
        return level * getConfig().strengthFactor;
    }

    private double getFoodCost(double factor)
    {
        return M.lerp(1D - factor, getConfig().maxFoodCost, getConfig().minFoodCost);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getRadius(getLevelPercent(level)), 0) + C.GRAY + " Block Radius");
        v.addLore(C.GREEN + "+ " + Form.pc(getStrength(level), 0) + C.GRAY + " Growth Strength");
        v.addLore(C.YELLOW + "+ " + Form.f(getFoodCost(getLevelPercent(level)), 2) + C.GRAY + " Food Cost");
    }

    @Override
    public void onTick() {
        for(Player p : Bukkit.getOnlinePlayers()) {
            try {
                if(getLevel(p) > 0) {
                    double rad = getRadius(getLevelPercent(p));
                    double strength = getStrength(getLevel(p));
                    double angle = Math.toRadians(Math.random() * 360);
                    double foodCost = getFoodCost(getLevelPercent(p));

                    for(int i = 0; i < Math.min(Math.min(rad * rad, 256), 3); i++)
                    {
                        Location m = p.getLocation().clone().add(new Vector(Math.sin(angle), RNG.r.i(-1, 1), Math.cos(angle)).multiply(Math.random() * rad));
                        Block a = m.getWorld().getHighestBlockAt(m).getRelative(BlockFace.UP);
                        if(a.getBlockData() instanceof Ageable) {
                            Ageable ab = (Ageable) a.getBlockData();
                            int toGrowLeft = ab.getMaximumAge() - ab.getAge();

                            if(toGrowLeft > 0)
                            {
                                int add = (int) Math.max(1, Math.min(strength, toGrowLeft));
                                if(ab.getMaximumAge() > ab.getAge() && getPlayer(p).canConsumeFood(foodCost, 10)) {
                                    while(add-- > 0)
                                    {
                                        J.s(() -> {
                                            if(getPlayer(p).consumeFood(foodCost, 10))
                                            {
                                                Ageable aab = (Ageable) a.getBlockData();

                                                if(aab.getAge() < aab.getMaximumAge())
                                                {
                                                    aab.setAge(aab.getAge() + 1);
                                                    a.setBlockData(aab, true);
                                                    a.getWorld().playSound(a.getLocation(), Sound.BLOCK_CHORUS_FLOWER_DEATH, 0.25f, RNG.r.f(0.3f, 0.7f));
                                                    p.spawnParticle(Particle.VILLAGER_HAPPY, a.getLocation().clone().add(0.5, 0.5, 0.5), 3, 0.3, 0.3, 0.3, 0.9);
                                                }
                                            }
                                        }, RNG.r.i(30, 60));
                                    }
                                }
                            }


                        }
                    }
                }
            } catch(Throwable e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 8;
        int maxLevel = 7;
        int initialCost = 12;
        double costFactor = 0.325;
        double minFoodCost = 0.05;
        double maxFoodCost = 0.4;
        double radiusFactor = 18;
        double strengthFactor = 0.75;
    }
}
