package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.xp.XP;
import com.volmit.adapt.util.Inventories;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.V;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

public class StealthSnatch extends SimpleAdaptation {
    private KList<Integer> holds = new KList<Integer>();
    public StealthSnatch() {
        super("snatch");
        setDescription("Snatch items instantly while sneaking!");
        setIcon(Material.CHEST_MINECART);
        setBaseCost(3);
        setInterval(50);
    }

    @EventHandler
    public void on(PlayerToggleSneakEvent e)
    {
        if(e.isSneaking())
        {
            snatch(e.getPlayer());
        }
    }

    private void snatch(Player player) {
        double factor = getLevelPercent(player);

        if(factor == 0)
        {
            return;
        }

        if(!player.isDead())
        {
            double range = factor * 3.5;

            for(Entity j : player.getWorld().getNearbyEntities(player.getLocation(), range, range /1.5, range))
            {
                if(j instanceof Item && !holds.contains(j.getEntityId()))
                {
                    double dist = j.getLocation().distanceSquared(player.getLocation());

                    if(dist < range * range && player.isSneaking() && j.getTicksLived() > 1)
                    {
                        ItemStack is = ((Item) j).getItemStack().clone();

                        if(Inventories.hasSpace(player.getInventory(), is))
                        {
                            holds.add(j.getEntityId());

                            if(player.isSneaking())
                            {
                                player.getWorld().playSound(player.getLocation(), Sound.ITEM_SWEET_BERRIES_PICK_FROM_BUSH, 1f, (float) (1.0 + (Math.random() / 3)));
                            }

                            player.getInventory().addItem(is);
                            sendCollected(player, (Item) j);
                            j.remove();

                            int id = j.getEntityId();

                            J.s(() -> holds.remove(new Integer(id)));
                        }
                    }
                }
            }
        }
    }

    public void sendCollected(Player plr, Item item)
    {
        try
        {
            Entity collected = item;
            Entity collector = plr;
            String nmstag = Bukkit.getServer().getClass().getCanonicalName().split("\\Q.\\E")[3];
            Class<?> c = Class.forName("net.minecraft.server." + nmstag + ".PacketPlayOutCollect");
            Class<?> p = Class.forName("net.minecraft.server." + nmstag + ".EntityPlayer");
            Class<?> pk = Class.forName("net.minecraft.server." + nmstag + ".Packet");
            Object v = c.getConstructor().newInstance();
            new V(v).set("a", collected.getEntityId());
            new V(v).set("b", collector.getEntityId());
            new V(v).set("c", ((Item)collected).getItemStack().getAmount());

            for(Entity i : plr.getWorld().getNearbyEntities(plr.getLocation(), 8, 8, 8))
            {
                if(i instanceof Player)
                {
                    Object pconnect = new V(new V(i).invoke("getHandle")).get("playerConnection");
                    pconnect.getClass().getMethod("sendPacket", pk).invoke(pconnect, v);
                }
            }
        }

        catch(Throwable e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void onTick() {
        for(Player i : Bukkit.getOnlinePlayers())
        {
            if(i.isSneaking())
            {
                J.s(() -> snatch(i));
            }
        }
    }
}
