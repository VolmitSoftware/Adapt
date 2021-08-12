package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.Discovery;
import com.volmit.adapt.content.adaptation.DiscoveryUnity;
import com.volmit.adapt.util.*;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.Map;

public class SkillDiscovery extends SimpleSkill {
    public SkillDiscovery() {
        super("discovery");
        setColor(C.DARK_BLUE);
        setBarColor(BarColor.BLUE);
        setInterval(500);
        setIcon(Material.FILLED_MAP);
        registerAdaptation(new DiscoveryUnity());
    }

    @EventHandler
    public void on(PlayerChangedWorldEvent e)
    {
        seeWorld(e.getPlayer(), e.getPlayer().getWorld());
    }

    @EventHandler
    public void on(PlayerInteractAtEntityEvent e)
    {
        seeEntity(e.getPlayer(), e.getRightClicked());
    }

    @EventHandler
    public void on(EntityPickupItemEvent e)
    {
        if(e.getEntity() instanceof Player)
        {
            seeItem((Player) e.getEntity(), e.getItem().getItemStack());
        }
    }

    @EventHandler
    public void on(PlayerItemConsumeEvent e)
    {
        seeItem(e.getPlayer(), e.getItem());
        seeFood(e.getPlayer(), e.getItem().getType());
    }

    @EventHandler
    public void on(PlayerInteractEvent e)
    {
        if(e.getClickedBlock() != null)
        {
           seeBlock(e.getPlayer(), e.getClickedBlock().getBlockData(), e.getClickedBlock().getLocation());
        }
    }

    public void seeBlock(Player p, BlockData bd, Location l) {
        Discovery<String> d = getPlayer(p).getData().getSeenBlocks();
        if (d.isNewDiscovery(bd.getAsString()))
        {
            xp(p, 25 + (d.getPower() * 33));
            p.spawnParticle(Particle.TOTEM, l.clone().add(0.5, 0.5, 0.5), 9, 0, 0, 0, 0.3);
        }

        seeItem(p, bd.getMaterial());
    }

    public void seeItem(Player p, Material bd)
    {
        Discovery<Material> d = getPlayer(p).getData().getSeenItems();
        if (d.isNewDiscovery(bd))
        {
            xp(p, 10 + (d.getPower() * 45));
        }
    }

    public void seeItem(Player p, ItemStack bd)
    {
        seeItem(p, bd.getType());
        Map<Enchantment, Integer> m = bd.getEnchantments();

        for(Enchantment i : m.keySet())
        {
            seeEnchant(p, i, m.get(i));
        }
    }

    public void seeFood(Player p, Material bd)
    {
        Discovery<Material> d = getPlayer(p).getData().getSeenFoods();
        if (d.isNewDiscovery(bd))
        {
            xp(p, 10 + (d.getPower() * 45));
        }
    }

    public void seeEntity(Player p, Entity bd)
    {
        Discovery<EntityType> d = getPlayer(p).getData().getSeenMobs();
        if (d.isNewDiscovery(bd.getType()))
        {
            xp(p, 25 + (d.getPower() * 250));
        }

        if(bd instanceof Player)
        {
            seePlayer(p, (Player) bd);
        }

        if(bd instanceof LivingEntity)
        {
            for(PotionEffect i : ((LivingEntity) bd).getActivePotionEffects())
            {
                seePotionEffect(p, i);
            }
        }
    }

    public void seePlayer(Player p, Player bd)
    {
        Discovery<String> d = getPlayer(p).getData().getSeenPeople();
        if (d.isNewDiscovery(bd.getUniqueId().toString()))
        {
            xp(p, 125 + (d.getPower() * 25));
        }
    }

    public void seeEnchant(Player p, Enchantment bd, int level)
    {
        Discovery<String> d = getPlayer(p).getData().getSeenEnchants();
        if (d.isNewDiscovery(bd.getName() + " " + Form.toRoman(level)))
        {
            xp(p, (5 + (d.getPower() * 40)) + Math.min(250, level * 52));
        }
    }

    public void seeWorld(Player p, World world)
    {
        Discovery<String> d = getPlayer(p).getData().getSeenWorlds();
        if (d.isNewDiscovery(world.getName() + "-" + world.getSeed()))
        {
            xp(p, (100 + (d.getPower() * 100)));
        }

        seeEnvironment(p, world.getEnvironment());
    }

    public void seeEnvironment(Player p, World.Environment world)
    {
        Discovery<World.Environment> d = getPlayer(p).getData().getSeenEnvironments();
        if (d.isNewDiscovery(world))
        {
            xp(p, (250 + (d.getPower() * 150)));
        }
    }

    public void seePotionEffect(Player p, PotionEffect e)
    {
        Discovery<String> d = getPlayer(p).getData().getSeenPotionEffects();
        if (d.isNewDiscovery(e.getType().getName() + " " + Form.toRoman(e.getAmplifier()).trim()))
        {
            xp(p, (15 + (d.getPower() * 36)));
        }
    }

    public void seeBiome(Player p, Biome e)
    {
        Discovery<Biome> d = getPlayer(p).getData().getSeenBiomes();
        if (d.isNewDiscovery(e))
        {
            xp(p, (15 + (d.getPower() * 36)));
        }
    }

    @Override
    public void onTick() {
        for(Player i : Bukkit.getOnlinePlayers())
        {
            try
            {
                Block b = i.getTargetBlockExact(5, FluidCollisionMode.NEVER);
                seeBlock(i, b.getBlockData(), b.getLocation());
                seeBiome(i, b.getBiome());
            }

            catch(Throwable ignored)
            {

            }
        }
    }
}
