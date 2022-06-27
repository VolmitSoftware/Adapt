package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.content.adaptation.nether.NetherWitherResist;
import com.volmit.adapt.content.adaptation.nether.NetherSkullYeet;
import com.volmit.adapt.util.C;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class SkillNether extends SimpleSkill<SkillNether.Config> {

    public static final String ID = "nether";

    private int witherRoseCooldown;

    public static String id(String name) {
        return ID + "-" + name;
    }

    public SkillNether() {
        super(ID, "\u20AA");
        registerConfiguration(Config.class);
        setDescription("From the depths of the Nether itself.");
        setInterval(3425);
        setColor(C.DARK_GRAY);
        setIcon(Material.NETHER_STAR);
        registerAdaptation(new NetherWitherResist());
        registerAdaptation(new NetherSkullYeet());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityDamage(EntityDamageEvent event) {
        if(event.getCause() == EntityDamageEvent.DamageCause.WITHER && event.getEntity() instanceof Player p && !(event instanceof EntityDamageByBlockEvent))
            xp(p, getConfig().getWitherDamageXp());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if(e.getBlock().getType() == Material.WITHER_ROSE && witherRoseCooldown == 0) {
            witherRoseCooldown = getConfig().getWitherRoseBreakCooldown();
            xp(e.getPlayer(), e.getBlock().getLocation().add(.5D, .5D, .5D), getConfig().getWitherRoseBreakXp());
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if(e.getEntity().getKiller() != null) {
            Player p = e.getEntity().getKiller();
            if(e.getEntityType() == EntityType.WITHER_SKELETON)
                xp(p, getConfig().getWitherSkeletonKillXp());
            else if(e.getEntityType() == EntityType.WITHER)
                xp(p, getConfig().getWitherKillXp());
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if(e.getDamager() instanceof Player p && e.getCause() == EntityDamageEvent.DamageCause.WITHER)
            xp(p, getConfig().getWitherAttackXp());
    }

    @Override
    public void onTick() {
        if(witherRoseCooldown > 0)
            witherRoseCooldown--;
    }

    @Override
    public boolean isEnabled() { return getConfig().isEnabled(); }

    @Data
    @NoArgsConstructor
    public static class Config {
        private boolean enabled = true;
        private double witherDamageXp = 1.0D;
        private double witherAttackXp = 1.0D;
        private double witherSkeletonKillXp = 1.0D;
        private double witherKillXp = 1.0D;
        private double witherRoseBreakXp = 1.0D;
        private int witherRoseBreakCooldown = 60 * 20;
    }
}
