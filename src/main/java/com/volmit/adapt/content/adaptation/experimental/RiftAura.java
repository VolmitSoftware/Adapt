package com.volmit.adapt.content.adaptation.experimental;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;


public class RiftAura extends SimpleAdaptation<RiftAura.Config> {
    public RiftAura() {
        super("rift-aura");
        setDescription("Passive & instantaneous non-telegraphed teleportation");
        setIcon(Material.ENDER_EYE);
        setBaseCost(0);
        setCostFactor(0);
        setMaxLevel(1);
        setInitialCost(25);
        setInterval(9540);
        registerConfiguration(Config.class);
    }

    private double getPhasePercent(int level) {
        return 0.10 + (0.05 * level);
    }

    private int maxPhaseBlocks(int level) {
        return (int) Math.floor(5 + (10 * level));
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getPhasePercent(level)) + C.GRAY + " Max Phase Percent");
        v.addLore(C.GREEN + "+ " + maxPhaseBlocks(level) + C.GRAY + " Max Phase Blocks");
        v.addLore(C.YELLOW + "~ This can be controlled if you practice...");
        v.addLore(C.RED + "- YOU WILL NOT ALWAYS BE IN THE BEST PLACE...");
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent e) {
        if(e.getEntity() instanceof Player p && getLevel((Player) e.getEntity()) > 0) {
            if(getLevel(p) > 0) {
                Random r = new Random();
                double dd = r.nextDouble();
                if(dd <= getPhasePercent(getLevel(p))) {
                    getSkill().xp(p, 10);

                    int x = (int) p.getLocation().getX() + (r.nextBoolean() ? 2 + r.nextInt(maxPhaseBlocks(getLevel(p))) : -(2 + r.nextInt(maxPhaseBlocks(p.getLevel()))));
                    int z = (int) p.getLocation().getZ() + (r.nextBoolean() ? 2 + r.nextInt(maxPhaseBlocks(getLevel(p))) : -(2 + r.nextInt(maxPhaseBlocks(p.getLevel()))));

                    int y = p.getWorld().getHighestBlockYAt(x, z) + 1;
                    Location rLoc = new Location(p.getWorld(), x, y, z, p.getLocation().getYaw(), p.getLocation().getPitch());
                    p.teleport(rLoc); // Vwoop
                    p.getLocation().getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.25f, 0.100f);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 20, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 100));
                }
            }
        }
    }


    @Override
    public void onTick() {

    }

    protected static class Config {

    }
}