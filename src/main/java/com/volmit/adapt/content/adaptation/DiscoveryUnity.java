package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.xp.XP;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;

public class DiscoveryUnity extends SimpleAdaptation {
    public DiscoveryUnity() {
        super("unity");
        setDescription("Collecting XP Orbs adds xp to a random skill.");
        setIcon(Material.REDSTONE);
        setBaseCost(4);
        setMaxLevel(9);
    }

    @EventHandler
    public void on(PlayerExpChangeEvent e)
    {
        if(e.getAmount() > 0 && getLevel(e.getPlayer()) > 0) {
            e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.9f);
            XP.xp(e.getPlayer(), getServer().getSkillRegistry().getSkill(getPlayer(e.getPlayer()).getData().getSkillLines().v().getRandom().getLine()), e.getAmount() * 125 * getLevelPercent(e.getPlayer()));
        }
    }

    @Override
    public void onTick() {

    }
}
