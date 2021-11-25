package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.content.adaptation.TamingDamage;
import com.volmit.adapt.content.adaptation.TamingHealthBoost;
import com.volmit.adapt.content.adaptation.TamingHealthRegeneration;
import com.volmit.adapt.util.C;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTameEvent;

public class SkillTaming extends SimpleSkill<SkillTaming.Config> {
    public SkillTaming() {
        super("taming", "\u2665");
        registerConfiguration(Config.class);
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
            xp((Player) e.getOwner(), e.getEntity().getLocation(), e.getEntity().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * getConfig().tameHealthXPMultiplier);
        }
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if(e.getDamager() instanceof Tameable &&
            ((Tameable) e.getDamager()).isTamed() &&
            ((Tameable) e.getDamager()).getOwner() instanceof Player) {
            Player owner = (Player) ((Tameable) e.getDamager()).getOwner();
            xp(owner, e.getEntity().getLocation(), e.getDamage() * getConfig().tameDamageXPMultiplier);
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
        double tameHealthXPMultiplier = 63;
        double tameDamageXPMultiplier = 9.85;
    }
}
