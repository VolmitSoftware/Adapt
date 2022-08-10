package com.volmit.adapt.content.adaptation.axe;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Impulse;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.ArrayList;
import java.util.List;

public class AxeGroundSmash extends SimpleAdaptation<AxeGroundSmash.Config> {
    private final List<Integer> holds = new ArrayList<>();

    public AxeGroundSmash() {
        super("axe-ground-smash");
        registerConfiguration(Config.class);
        setDescription("Jump, then crouch and smash all nearby enemies.");
        setDisplayName("Axe Ground Smash");
        setIcon(Material.NETHERITE_AXE);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(5000);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageByEntityEvent e) {
        if(e.getDamager() instanceof Player && getLevel((Player) e.getDamager()) > 0 && ((Player) e.getDamager()).isSneaking()) {
            if(!isAxe(((Player) e.getDamager()).getInventory().getItemInMainHand())) {
                return;
            }

            double f = getLevelPercent((Player) e.getDamager());

            if(((Player) e.getDamager()).hasCooldown(((Player) e.getDamager()).getInventory().getItemInMainHand().getType())) {
                return;
            }

            ((Player) e.getDamager()).setCooldown(((Player) e.getDamager()).getInventory().getItemInMainHand().getType(), getCooldownTime(f));
            new Impulse(getRadius(f))
                .damage(getDamage(f), getFalloffDamage(f))
                .force(getForce(f))
                .punch(e.getEntity().getLocation());
            e.getEntity().getWorld().playSound(e.getEntity().getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.HOSTILE, 0.6f, 0.4f);
            e.getEntity().getWorld().playSound(e.getEntity().getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.HOSTILE, 0.5f, 0.1f);
            e.getEntity().getWorld().playSound(e.getEntity().getLocation(), Sound.ENTITY_TURTLE_EGG_CRACK, SoundCategory.HOSTILE, 1f, 0.4f);

        }
    }

    @Override
    public void addStats(int level, Element v) {
        double f = getLevelPercent(level);
        v.addLore(C.RED + "+ " + Form.f(getFalloffDamage(f), 1) + " - " + Form.f(getDamage(f), 1) + C.GRAY + " Damage");
        v.addLore(C.RED + "+ " + Form.f(getRadius(f), 1) + C.GRAY + " Block Radius");
        v.addLore(C.RED + "+ " + Form.pc(getForce(f), 0) + C.GRAY + " Force");
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownTime(getLevelPercent(level)) * 50D, 1) + C.GRAY + " Smash Cooldown");
    }

    public int getCooldownTime(double factor) {
        return (int) (((1D - factor) * getConfig().cooldownTicksInverseLevelMultiplier) + getConfig().cooldownTicksBase);
    }

    public double getRadius(double factor) {
        return getConfig().radiusLevelFactorMultiplier * factor;
    }

    public double getDamage(double factor) {
        return getConfig().damageLevelFactorMultiplier * factor;
    }

    public double getForce(double factor) {
        return (getConfig().forceFactorMultiplier * factor) + getConfig().forceBase;
    }

    public double getFalloffDamage(double factor) {
        return getConfig().falloffFactor * factor;
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
        int baseCost = 6;
        double costFactor = 0.75;
        int maxLevel = 5;
        int initialCost = 8;
        double falloffFactor = 3;
        double radiusLevelFactorMultiplier = 8;
        double damageLevelFactorMultiplier = 8;
        double forceFactorMultiplier = 1.15;
        double forceBase = 0.27;
        double cooldownTicksBase = 80;
        double cooldownTicksInverseLevelMultiplier = 225;
    }
}
