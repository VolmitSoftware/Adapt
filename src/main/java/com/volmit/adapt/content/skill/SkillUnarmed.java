package com.volmit.adapt.content.skill;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.content.adaptation.unarmed.UnarmedGlassCannon;
import com.volmit.adapt.content.adaptation.unarmed.UnarmedPower;
import com.volmit.adapt.content.adaptation.unarmed.UnarmedSuckerPunch;
import com.volmit.adapt.util.C;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class SkillUnarmed extends SimpleSkill<SkillUnarmed.Config> {
    public SkillUnarmed() {
        super("unarmed", Adapt.dLocalize("Skill", "Unarmed", "Icon"));
        registerConfiguration(Config.class);
        setColor(C.YELLOW);
        setDescription(Adapt.dLocalize("Skill", "Unarmed", "Description"));
        setInterval(2570);
        registerAdaptation(new UnarmedSuckerPunch());
        registerAdaptation(new UnarmedPower());
        registerAdaptation(new UnarmedGlassCannon());
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

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        double damageXPMultiplier = 11.44;
    }
}
