package com.volmit.adapt.content.skill;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.content.adaptation.*;
import com.volmit.adapt.content.adaptation.experimental.RiftAura;
import com.volmit.adapt.content.adaptation.experimental.RiftRing;
import com.volmit.adapt.content.adaptation.experimental.RiftSphere;
import com.volmit.adapt.content.adaptation.experimental.RiftStorage;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.KMap;
import com.volmit.adapt.util.M;
import eu.endercentral.crazy_advancements.AdvancementDisplay;
import eu.endercentral.crazy_advancements.AdvancementVisibility;
import eu.endercentral.crazy_advancements.events.AdvancementGrantEvent;

import net.minecraft.world.entity.boss.enderdragon.EntityEnderDragon;
import net.minecraft.world.entity.monster.EntityEnderman;
import net.minecraft.world.entity.monster.EntityEndermite;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderPearl;
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

public class SkillRift extends SimpleSkill {
    private KMap<Player, Long> lasttp = new KMap<>();

    public SkillRift() {
        super("rift", "\u274D");
        setDescription("Dimensional magic");
        setColor(C.DARK_PURPLE);
        setInterval(1154);
        setIcon(Material.ENDER_EYE);
        registerAdaptation(new RiftAura());
        registerAdaptation(new RiftAccess());
        registerAdaptation(new RiftStorage());
        registerAdaptation(new RiftSphere());
        registerAdaptation(new RiftRing());
    }

    @EventHandler
    public void on(PlayerTeleportEvent e)
    {
        if(getPlayer(e.getPlayer()).hasSkill(this) && e.getFrom().getWorld() != e.getTo().getWorld())
        {
            xpSilent(e.getPlayer(), 1000);
        }
    }

    @EventHandler
    public void on(ProjectileLaunchEvent e)
    {
        if(e.getEntity() instanceof EnderPearl && e.getEntity().getShooter() instanceof Player p)
        {
            xp(p, 50);
        }
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e)
    {
        if(e.getEntity() instanceof Enderman && e.getDamager() instanceof Player p)
        {
            xp(p, 4 * Math.min(e.getDamage(),((Enderman) e.getEntity()).getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue()));
        }
        if(e.getEntity() instanceof Endermite && e.getDamager() instanceof Player p)
        {
            xp(p, 2 * Math.min(e.getDamage(),((Endermite) e.getEntity()).getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue()));
        }
        if(e.getEntity() instanceof EnderDragon && e.getDamager() instanceof Player p)
        {
            xp(p, 4 * Math.min(e.getDamage(),((EnderDragon) e.getEntity()).getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue()));
        }
        if(e.getEntity() instanceof EnderCrystal && e.getDamager() instanceof Player p)
        {
            xp(p, 250);
        }

        if(e.getEntity() instanceof Enderman && e.getDamager() instanceof Projectile j && j.getShooter() instanceof Player p)
        {
            xp(p, 4 * Math.min(e.getDamage(),((Enderman) e.getEntity()).getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue()));
        }
        if(e.getEntity() instanceof Endermite && e.getDamager() instanceof Projectile j && j.getShooter() instanceof Player p)
        {
            xp(p, 2 * Math.min(e.getDamage(),((Endermite) e.getEntity()).getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue()));
        }
        if(e.getEntity() instanceof EnderDragon && e.getDamager() instanceof Projectile j && j.getShooter() instanceof Player p)
        {
            xp(p, 4 * Math.min(e.getDamage(),((EnderDragon) e.getEntity()).getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue()));
        }
        if(e.getEntity() instanceof EnderCrystal && e.getDamager() instanceof Projectile j && j.getShooter() instanceof Player p)
        {
            xp(p, 250);
        }
    }

    @EventHandler
    public void on(EntityDeathEvent e)
    {
        if(e.getEntity() instanceof EnderCrystal && e.getEntity().getKiller() != null)
        {
            xp(e.getEntity().getKiller(), 350);
        }
    }

    @EventHandler
    public void on(PlayerQuitEvent e)
    {
        lasttp.remove(e.getPlayer());
    }

    @Override
    public void onTick() {
        for(Player i : lasttp.k())
        {
            if(M.ms() - lasttp.get(i) > 60000)
            {
                lasttp.remove(i);
            }
        }
    }

    @Override
    public void onRegisterAdvancements(KList<AdaptAdvancement> advancements) {

    }
}
