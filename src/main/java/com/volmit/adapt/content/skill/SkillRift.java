package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.content.adaptation.rift.*;
import com.volmit.adapt.content.adaptation.rift.experimental.RiftAstralKey;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.M;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.EnderSignal;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Endermite;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;

public class SkillRift extends SimpleSkill<SkillRift.Config> {
    private final Map<Player, Long> lasttp = new HashMap<>();

    public SkillRift() {
        super("rift", "\u274D");
        registerConfiguration(Config.class);
        setDescription("You have harnessed the harness");
        setColor(C.DARK_PURPLE);
        setInterval(1154);
        setIcon(Material.ENDER_EYE);
        registerAdaptation(new RiftResist());
        registerAdaptation(new RiftAccess());
        registerAdaptation(new RiftEnderchest());
        registerAdaptation(new RiftGate());
        registerAdaptation(new RiftBlink());
    }

    @EventHandler
    public void on(PlayerTeleportEvent e) {
        if(!lasttp.containsKey(e.getPlayer())) { // any teleport. this was problematic when teleporting from a rift to a rift or using a rift to teleport to a rift inside of a dimension
            xpSilent(e.getPlayer(), getConfig().teleportXP);
            lasttp.put(e.getPlayer(), M.ms());
        }
    }

    @EventHandler
    public void on(ProjectileLaunchEvent e) {
        if(e.getEntity() instanceof EnderPearl && e.getEntity().getShooter() instanceof Player p) {
            xp(p, getConfig().throwEnderpearlXP);
        } else if(e.getEntity() instanceof EnderSignal && e.getEntity().getShooter() instanceof Player p) {
            xp(p, getConfig().throwEnderEyeXP);
        }
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if(e.getEntity() instanceof Enderman && e.getDamager() instanceof Player p) {
            xp(p, getConfig().damageEndermanXPMultiplier * Math.min(e.getDamage(), ((Enderman) e.getEntity()).getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue()));
        }
        if(e.getEntity() instanceof Endermite && e.getDamager() instanceof Player p) {
            xp(p, getConfig().damageEndermiteXPMultiplier * Math.min(e.getDamage(), ((Endermite) e.getEntity()).getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue()));
        }
        if(e.getEntity() instanceof EnderDragon && e.getDamager() instanceof Player p) {
            xp(p, getConfig().damageEnderdragonXPMultiplier * Math.min(e.getDamage(), ((EnderDragon) e.getEntity()).getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue()));
        }
        if(e.getEntity() instanceof EnderCrystal && e.getDamager() instanceof Player p) {
            xp(p, getConfig().damageEndCrystalXP);
        }

        if(e.getEntity() instanceof Enderman && e.getDamager() instanceof Projectile j && j.getShooter() instanceof Player p) {
            xp(p, getConfig().damageEndermanXPMultiplier * Math.min(e.getDamage(), ((Enderman) e.getEntity()).getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue()));
        }
        if(e.getEntity() instanceof Endermite && e.getDamager() instanceof Projectile j && j.getShooter() instanceof Player p) {
            xp(p, getConfig().damageEndermiteXPMultiplier * Math.min(e.getDamage(), ((Endermite) e.getEntity()).getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue()));
        }
        if(e.getEntity() instanceof EnderDragon && e.getDamager() instanceof Projectile j && j.getShooter() instanceof Player p) {
            xp(p, getConfig().damageEnderdragonXPMultiplier * Math.min(e.getDamage(), ((EnderDragon) e.getEntity()).getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue()));
        }
        if(e.getEntity() instanceof EnderCrystal && e.getDamager() instanceof Projectile j && j.getShooter() instanceof Player p) {
            xp(p, getConfig().damageEndCrystalXP);
        }
    }

    @EventHandler
    public void on(EntityDeathEvent e) {
        if(e.getEntity() instanceof EnderCrystal && e.getEntity().getKiller() != null) {
            xp(e.getEntity().getKiller(), getConfig().destroyEndCrystalXP);
        }
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        lasttp.remove(e.getPlayer());
    }

    @Override
    public void onTick() {
        for(Player i : lasttp.k()) {
            if(M.ms() - lasttp.get(i) > getConfig().teleportXPCooldown) {
                lasttp.remove(i);
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        double destroyEndCrystalXP = 550;
        double damageEndCrystalXP = 110;
        double damageEndermanXPMultiplier = 4;
        double damageEndermiteXPMultiplier = 2;
        double damageEnderdragonXPMultiplier = 8;
        double throwEnderpearlXP = 105;
        double throwEnderEyeXP = 45;
        double teleportXP = 15;
        double teleportXPCooldown = 60000;
    }
}
