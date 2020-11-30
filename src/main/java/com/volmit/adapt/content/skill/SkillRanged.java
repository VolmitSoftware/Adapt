package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.content.adaptation.AgilityWindUp;
import com.volmit.adapt.content.adaptation.RangedForce;
import com.volmit.adapt.content.adaptation.RangedPiercing;
import com.volmit.adapt.util.C;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

public class SkillRanged extends SimpleSkill {
    public SkillRanged() {
        super("ranged");
        setColor(C.DARK_GREEN);
        setBarColor(BarColor.GREEN);
        setInterval(3000);
        registerAdaptation(new RangedForce());
        registerAdaptation(new RangedPiercing());
        setIcon(Material.CROSSBOW);
    }

    @EventHandler
    public void on(ProjectileLaunchEvent e)
    {
        if(e.getEntity().getShooter() instanceof Player)
        {
            xp(((Player)e.getEntity().getShooter()), 7);
        }
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e)
    {
        if (e.getDamager() instanceof Projectile && ((Projectile)e.getDamager()).getShooter() instanceof Player) {
            Player p = ((Player)((Projectile)e.getDamager()).getShooter());

            xp(p, (3.125 * e.getDamage()) + (e.getEntity().getLocation().distance(p.getLocation()) * 1.7));
        }
    }

    @Override
    public void onTick() {

    }
}
