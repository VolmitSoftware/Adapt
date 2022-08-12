package com.volmit.adapt.content.skill;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.content.adaptation.hunter.*;
import com.volmit.adapt.util.C;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class SkillHunter extends SimpleSkill<SkillHunter.Config> {
    public SkillHunter() {
        super(Adapt.dLocalize("SkillHunter.Name"), Adapt.dLocalize("SkillHunter.Icon"));
        registerConfiguration(Config.class);
        setColor(C.RED);
        setDescription(Adapt.dLocalize("SkillHunter.Description"));
        setInterval(4150);
        setIcon(Material.BONE);
        registerAdaptation(new HunterAdrenaline());
        registerAdaptation(new HunterRegen());
        registerAdaptation(new HunterInvis());
        registerAdaptation(new HunterJumpBoost());
        registerAdaptation(new HunterLuck());
        registerAdaptation(new HunterSpeed());
        registerAdaptation(new HunterStrength());
        registerAdaptation(new HunterResistance());
    }

    @EventHandler
    public void on(BlockBreakEvent e) {
        if (e.getBlock().getType().equals(Material.TURTLE_EGG)) {
            xp(e.getBlock().getLocation(), getConfig().turtleEggKillXP, getConfig().turtleEggSpatialRadius, getConfig().turtleEggSpatialDuration);
            getPlayer(e.getPlayer()).getData().addStat("killed.tutleeggs", 1);
        }
    }

    @EventHandler
    public void on(PlayerInteractEvent e) {
        if (e.getAction().equals(Action.PHYSICAL) && e.getClickedBlock().getType().equals(Material.TURTLE_EGG)) {
            xp(e.getClickedBlock().getLocation(), getConfig().turtleEggKillXP, getConfig().turtleEggSpatialRadius, getConfig().turtleEggSpatialDuration);
            getPlayer(e.getPlayer()).getData().addStat("killed.tutleeggs", 1);
        }
    }

    @EventHandler
    public void on(EntityDeathEvent e) {
        if (e.getEntity().getKiller() != null && e.getEntity().getKiller() != null) {
            double cmult = e.getEntity().getType().equals(EntityType.CREEPER) ? getConfig().creeperKillMultiplier : 1;
            xp(e.getEntity().getLocation(), e.getEntity().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * getConfig().killMaxHealthSpatialXPMultiplier * cmult, getConfig().killSpatialRadius, getConfig().killSpatialDuration);
            xp(e.getEntity().getKiller(), e.getEntity().getLocation(), e.getEntity().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * getConfig().killMaxHealthXPMultiplier * cmult);
            getPlayer(e.getEntity().getKiller()).getData().addStat("killed.kills", 1);
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
        double turtleEggKillXP = 125;
        int turtleEggSpatialRadius = 24;
        long turtleEggSpatialDuration = 15000;
        double creeperKillMultiplier = 4;
        double killMaxHealthSpatialXPMultiplier = 3;
        double killMaxHealthXPMultiplier = 6;
        int killSpatialRadius = 24;
        long killSpatialDuration = 15000;
    }
}
