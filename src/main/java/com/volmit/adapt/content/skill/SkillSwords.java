package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.content.adaptation.SwordsMachete;
import com.volmit.adapt.util.C;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class SkillSwords extends SimpleSkill {
    public SkillSwords() {
        super("swords", "\u2694");
        setColor(C.YELLOW);
        setInterval(2150);
        setIcon(Material.DIAMOND_SWORD);
        registerAdaptation(new SwordsMachete());
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if(e.getDamager() instanceof Player) {
            AdaptPlayer a = getPlayer((Player) e.getDamager());
            ItemStack hand = a.getPlayer().getInventory().getItemInMainHand();

            if(isSword(hand)) {
                xp(a.getPlayer(), 13.26 * e.getDamage());
            }
        }
    }

    @Override
    public void onTick() {

    }
}
