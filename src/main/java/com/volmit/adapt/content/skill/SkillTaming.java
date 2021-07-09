package com.volmit.adapt.content.skill;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.xp.XP;
import com.volmit.adapt.content.adaptation.TamingDamage;
import com.volmit.adapt.content.adaptation.TamingHealthBoost;
import com.volmit.adapt.content.adaptation.TamingHealthRegeneration;
import com.volmit.adapt.util.C;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;

public class SkillTaming extends SimpleSkill {
    public SkillTaming() {
        super("taming");
        setColor(C.GOLD);
        setBarColor(BarColor.YELLOW);
        setInterval(3700);
        setIcon(Material.LEAD);
        registerAdaptation(new TamingHealthBoost());
        registerAdaptation(new TamingDamage());
        registerAdaptation(new TamingHealthRegeneration());
    }

    @EventHandler
    public void on(EntityTameEvent e)
    {
        if(e.getOwner() instanceof Player)
        {
            xp((Player) e.getOwner(), e.getEntity().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * 63);
        }
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e)
    {
        if(e.getDamager() instanceof Tameable &&
                ((Tameable) e.getDamager()).isTamed() &&
                ((Tameable) e.getDamager()).getOwner() instanceof Player)
        {
            Player owner = (Player) ((Tameable) e.getDamager()).getOwner();
            xp(owner, e.getDamage() * 6.85);
        }
    }

    @EventHandler
    public void on(EntityDeathEvent e)
    {
        EntityDamageEvent ed = e.getEntity().getLastDamageCause();

        if(ed != null && ed instanceof EntityDamageByEntityEvent)
        {
            EntityDamageByEntityEvent ede = (EntityDamageByEntityEvent) ed;

            if(ede.getDamager() instanceof Tameable &&
                    ((Tameable) ede.getDamager()).isTamed() &&
                    ((Tameable) ede.getDamager()).getOwner() instanceof Player)
            {
                Player owner = (Player) ((Tameable) ede.getDamager()).getOwner();
                xp(owner, e.getEntity().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * 8.85);
                XP.xp(owner, Adapt.instance.getAdaptServer().getSkillRegistry().getSkill("Hunter"), e.getEntity().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * 4.85);
            }
        }

    }

    @Override
    public void onTick() {

    }
}
