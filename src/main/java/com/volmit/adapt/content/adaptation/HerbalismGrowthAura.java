package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.world.AdaptRecipe;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.KList;
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

public class HerbalismGrowthAura extends SimpleAdaptation {
    private final KList<Integer> holds = new KList<>();

    public HerbalismGrowthAura() {
        super("growth-aura");
        setDescription("Grow nature around you in an aura");
        setIcon(Material.BONE_MEAL);
        setBaseCost(8);
        setMaxLevel(3);
        setInterval(875);
        setInitialCost(12);
        setCostFactor(0.325);
        registerRecipe(AdaptRecipe.shapeless()
            .
            .build());
    }

    private double getRadius(double factor) {
        return factor * 8;
    }

    private double getStrength(double factor) {
        return Math.pow(factor, 1.77);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getRadius(getLevelPercent(level)), 0) + C.GRAY + " Block Radius");
        v.addLore(C.GREEN + "+ " + Form.pc(getStrength(getLevelPercent(level)), 0) + C.GRAY + " Growth Strength");
    }

    @Override
    public void onTick() {
        for(Player p : Bukkit.getOnlinePlayers()) {
            try {
                if(getLevel(p) > 0 && Math.random() < getStrength(getLevelPercent(p))) {
                    double rad = getRadius(getLevelPercent(p));
                    double strength = getStrength(getLevelPercent(p));
                    double angle = Math.toRadians(Math.random() * 360);
                    Location m = p.getLocation().clone().add(new Vector(Math.sin(angle), 0, Math.cos(angle)).multiply(Math.random() * rad));
                    Block a = m.getWorld().getHighestBlockAt(m).getRelative(BlockFace.UP);
                    if(a.getBlockData() instanceof Ageable) {
                        Ageable ab = (Ageable) a.getBlockData();
                        if(ab.getMaximumAge() > ab.getAge()) {
                            J.s(() -> {
                                ab.setAge(ab.getAge() + 1);
                                a.setBlockData(ab, true);
                                a.getWorld().playSound(a.getLocation(), Sound.ITEM_CROP_PLANT, 1f, 1.25f);
                                p.spawnParticle(Particle.VILLAGER_HAPPY, a.getLocation().clone().add(0.5, 0.5, 0.5), 9, 0.3, 0.3, 0.3, 0.9);
                            });
                        }
                    }
                }
            } catch(Throwable e) {
                e.printStackTrace();
            }
        }
    }
}
