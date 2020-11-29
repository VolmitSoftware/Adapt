package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
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

    @Override
    public void addStats(int level, Element v) {
        double f = getLevelPercent(level);
        double d = f * 1.25 * (0.125 * 11.7 * f);

        v.addLore(C.GREEN + "+ " + Form.pc(d, 0) + C.GRAY + " Damage");

        v.addLore(C.BLUE + "Damage increases by with your speed while punching");
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

            e.setDamage(e.getDamage() * factor * 1.25 * (getPlayer(p).getSpeed() * 11.7 * factor));
            e.getEntity().getWorld().playSound(e.getEntity().getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 1.8f);
            e.getEntity().getWorld().playSound(e.getEntity().getLocation(), Sound.BLOCK_BASALT_BREAK, 1f, 0.6f);
            getSkill().xp(p, 6.221 * e.getDamage());
            if(e.getDamage() > 5)
            {
                getSkill().xp(p, 0.42 * e.getDamage());
                e.getEntity().getWorld().spawnParticle(Particle.FLASH, e.getEntity().getLocation(), 1);
            }
        }
    }

    @Override
    public void onTick() {

    }
}
