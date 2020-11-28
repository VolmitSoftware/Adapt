package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class UnarmedSuckerPunch extends SimpleAdaptation {
    public UnarmedSuckerPunch() {
        super("sucker-punch");
        setDescription("Sprint punches, but more deadly.");
        setIcon(Material.OBSIDIAN);
        setBaseCost(2);
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e)
    {
        if(e.getDamager() instanceof Player) {
            Player p = (Player) e.getDamager();
            double factor = getLevelPercent(p);

            if (!p.isSprinting())
            {
                return;
            }

            if(factor <= 0)
            {
                return;
            }

            e.setDamage(e.getDamage() * factor * 2.25 * (getPlayer(p).getSpeed() * 11.7 * factor));
            e.getEntity().getWorld().playSound(e.getEntity().getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 1.8f);
            e.getEntity().getWorld().playSound(e.getEntity().getLocation(), Sound.BLOCK_BASALT_BREAK, 1f, 0.6f);
            if(e.getDamage() > 5)
            {
                e.getEntity().getWorld().spawnParticle(Particle.FLASH, e.getEntity().getLocation(), 1);
            }
        }
    }

    @Override
    public void onTick() {

    }
}
