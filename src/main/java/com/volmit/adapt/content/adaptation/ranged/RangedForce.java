package com.volmit.adapt.content.adaptation.ranged;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.advancements.advancement.AdvancementDisplay;
import com.volmit.adapt.util.advancements.advancement.AdvancementVisibility;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

public class RangedForce extends SimpleAdaptation<RangedForce.Config> {

    public RangedForce() {
        super("ranged-force");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Ranged", "ForceShot", "Description"));
        setDisplayName(Adapt.dLocalize("Ranged", "ForceShot", "Name"));
        setIcon(Material.ARROW);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(5000);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SPECTRAL_ARROW)
                .key("challenge_force_30")
                .title(Adapt.dLocalize("Ranged", "ForceShot", "AdvancementName"))
                .description(Adapt.dLocalize("Ranged", "ForceShot", "AdvancementLore"))
                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getSpeed(getLevelPercent(level)), 0) + C.GRAY + Adapt.dLocalize("Ranged", "ForceShot", "Lore1"));
    }

    private double getSpeed(double factor) {
        return (factor * getConfig().speedFactor);
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Projectile r && r.getShooter() instanceof Player p && hasAdaptation(p) && !getPlayer(p).getData().isGranted("challenge_force_30")) {
            Location a = e.getEntity().getLocation().clone();
            Location b = p.getLocation().clone();
            a.setY(0);
            b.setY(0);

            if (a.distanceSquared(b) > 10) {
                getPlayer(p).getAdvancementHandler().grant("challenge_force_30");
                getSkill().xp(p, getConfig().challengeRewardLongShotReward);
            }
        }
    }

    @EventHandler
    public void on(ProjectileLaunchEvent e) {
        if (e.getEntity().getShooter() instanceof Player) {
            Player p = ((Player) e.getEntity().getShooter());

            if (getLevel(p) > 0) {
                double factor = getLevelPercent(p);
                e.getEntity().setVelocity(e.getEntity().getVelocity().clone().multiply(1 + getSpeed(factor)));
                e.getEntity().getWorld().playSound(e.getEntity().getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.5f + ((float) factor * 0.25f), 0.7f + (float) (factor / 2f));
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
        int baseCost = 2;
        int maxLevel = 7;
        int initialCost = 5;
        double costFactor = 0.225;
        double challengeRewardLongShotReward = 2000;
        double speedFactor = 1.135;
    }
}
