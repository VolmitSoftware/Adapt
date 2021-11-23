package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.content.adaptation.TamingDamage;
import com.volmit.adapt.content.adaptation.TamingHealthBoost;
import com.volmit.adapt.content.adaptation.TamingHealthRegeneration;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.KList;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTameEvent;

public class SkillTaming extends SimpleSkill {
    public SkillTaming() {
        super("taming", "\u2665");
        setDescription("The parrots and the bees... and you?");
        setColor(C.GOLD);
        setInterval(3700);
        setIcon(Material.LEAD);
        registerAdaptation(new TamingHealthBoost());
        registerAdaptation(new TamingDamage());
        registerAdaptation(new TamingHealthRegeneration());
    }

    @EventHandler
    public void on(EntityTameEvent e) {
        if(e.getOwner() instanceof Player) {
            xp((Player) e.getOwner(), e.getEntity().getLocation(), e.getEntity().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * 63);
        }
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if(e.getDamager() instanceof Tameable &&
            ((Tameable) e.getDamager()).isTamed() &&
            ((Tameable) e.getDamager()).getOwner() instanceof Player) {
            Player owner = (Player) ((Tameable) e.getDamager()).getOwner();
            xp(owner, e.getEntity().getLocation(), e.getDamage() * 12.85);
        }
    }

    @Override
    public void onTick() {

    }
}
