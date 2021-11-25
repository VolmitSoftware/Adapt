package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.content.adaptation.UnarmedPower;
import com.volmit.adapt.content.adaptation.UnarmedSuckerPunch;
import com.volmit.adapt.util.C;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class SkillUnarmed extends SimpleSkill<SkillUnarmed.Config> {
    public SkillUnarmed() {
        super("unarmed", "\u269C");
        registerConfiguration(Config.class);
        setColor(C.YELLOW);
        setDescription("Without a weapon is not without strength");
        setInterval(2570);
        registerAdaptation(new UnarmedSuckerPunch());
        registerAdaptation(new UnarmedPower());
        setIcon(Material.FIRE_CHARGE);
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if(e.getDamager() instanceof Player) {
            AdaptPlayer a = getPlayer((Player) e.getDamager());
            ItemStack hand = a.getPlayer().getInventory().getItemInMainHand();

            if(!isMelee(hand)) {
                xp(a.getPlayer(), e.getEntity().getLocation(), getConfig().damageXPMultiplier * e.getDamage());
            }
        }
    }

    @Override
    public void onTick() {

    }

    @NoArgsConstructor
    protected static class Config {
        double damageXPMultiplier = 11.44;
    }
}
