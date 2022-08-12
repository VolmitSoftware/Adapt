package com.volmit.adapt.content.skill;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.content.adaptation.taming.TamingDamage;
import com.volmit.adapt.content.adaptation.taming.TamingHealthBoost;
import com.volmit.adapt.content.adaptation.taming.TamingHealthRegeneration;
import com.volmit.adapt.util.C;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class SkillTaming extends SimpleSkill<SkillTaming.Config> {
    public SkillTaming() {
        super("taming", Adapt.dLocalize("Skill", "Taming", "Name"), Adapt.dLocalize("Skill", "Taming", "Icon"));
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Skill", "Taming", "Description"));
        setColor(C.GOLD);
        setInterval(3700);
        setIcon(Material.LEAD);
        registerAdaptation(new TamingHealthBoost());
        registerAdaptation(new TamingDamage());
        registerAdaptation(new TamingHealthRegeneration());
    }

    @EventHandler
    public void on(EntityBreedEvent e) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage("AHH: " + getConfig().tameXpBase);
            if (player.getLocation().distance(e.getEntity().getLocation()) <= 15) {
                xp(player, getConfig().tameXpBase);
            }
        }
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Tameable &&
                ((Tameable) e.getDamager()).isTamed() &&
                ((Tameable) e.getDamager()).getOwner() instanceof Player owner) {
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
        double tameXpBase = 55;
        double tameHealthXPMultiplier = 63;
        double tameDamageXPMultiplier = 9.85;
    }
}
