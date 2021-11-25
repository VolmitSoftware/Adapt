package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.content.adaptation.RangedArrowRecovery;
import com.volmit.adapt.content.adaptation.RangedForce;
import com.volmit.adapt.content.adaptation.RangedLungeShot;
import com.volmit.adapt.content.adaptation.RangedPiercing;
import com.volmit.adapt.util.C;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

import java.util.Locale;

public class SkillRanged extends SimpleSkill<SkillRanged.Config> {
    public SkillRanged() {
        super("ranged", "\uD83C\uDFF9");
        registerConfiguration(Config.class);
        setDescription("When distance is your only alternative");
        setColor(C.DARK_GREEN);
        setInterval(3000);
        registerAdaptation(new RangedForce());
        registerAdaptation(new RangedPiercing());
        registerAdaptation(new RangedArrowRecovery());
        registerAdaptation(new RangedLungeShot());
        setIcon(Material.CROSSBOW);
    }

    @EventHandler
    public void on(ProjectileLaunchEvent e) {
        if(e.getEntity().getShooter() instanceof Player) {
            xp(((Player) e.getEntity().getShooter()), getConfig().shootXP);
            getPlayer(((Player) e.getEntity().getShooter())).getData().addStat("ranged.shotsfired", 1);
            getPlayer(((Player) e.getEntity().getShooter())).getData().addStat("ranged.shotsfired." + e.getEntity().getType().name().toLowerCase(Locale.ROOT), 1);
        }
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if(e.getDamager() instanceof Projectile && ((Projectile) e.getDamager()).getShooter() instanceof Player) {
            Player p = ((Player) ((Projectile) e.getDamager()).getShooter());
            getPlayer(p).getData().addStat("ranged.damage", e.getDamage());
            getPlayer(p).getData().addStat("ranged.distance", e.getEntity().getLocation().distance(p.getLocation()));
            getPlayer(p).getData().addStat("ranged.damage." + e.getDamager().getType().name().toLowerCase(Locale.ROOT), e.getDamage());
            getPlayer(p).getData().addStat("ranged.distance." + e.getDamager().getType().name().toLowerCase(Locale.ROOT), e.getEntity().getLocation().distance(p.getLocation()));
            xp(p, e.getEntity().getLocation(), (getConfig().hitDamageXPMultiplier * e.getDamage()) + (e.getEntity().getLocation().distance(p.getLocation()) * getConfig().hitDistanceXPMultiplier));
        }
    }

    @Override
    public void onTick() {

    }

    @NoArgsConstructor
    protected static class Config {
        double shootXP = 7;
        double hitDamageXPMultiplier = 3.125;
        double hitDistanceXPMultiplier = 1.7;
    }
}
