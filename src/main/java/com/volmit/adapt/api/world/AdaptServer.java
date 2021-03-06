package com.volmit.adapt.api.world;

import com.volmit.adapt.api.skill.SkillRegistry;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.api.xp.SpatialXP;
import com.volmit.adapt.api.xp.XP;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.KMap;
import com.volmit.adapt.util.M;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.IOException;
import java.util.UUID;

public class AdaptServer extends TickedObject {
    private final KMap<Player, AdaptPlayer> players;
    private final KList<SpatialXP> spatialTickets;
    @Getter
    private SkillRegistry skillRegistry;

    public AdaptServer()
    {
        super("core", UUID.randomUUID().toString(), 1000);
        spatialTickets = new KList<>();
        players = new KMap<>();
        try {
            skillRegistry = new SkillRegistry();
        } catch (IOException e) {
            e.printStackTrace();
        }

        for(Player i : Bukkit.getServer().getOnlinePlayers())
        {
            join(i);
        }
    }

    public void offer(SpatialXP xp)
    {
        spatialTickets.add(xp);
    }

    public void takeSpatial(AdaptPlayer p)
    {
        SpatialXP x = spatialTickets.getRandom();

        if(x == null)
        {
            return;
        }

        if(M.ms() > x.getMs())
        {
            spatialTickets.remove(x);
            return;
        }

        if(p.getPlayer().getWorld().equals(x.getLocation().getWorld()))
        {
            double c = p.getPlayer().getLocation().distanceSquared(x.getLocation());
            if(c < x.getRadius() * x.getRadius())
            {
                double distl = M.lerpInverse(0, x.getRadius() * x.getRadius(), c);
                double xp = x.getXp() / (1.5D * ((distl*9) + 1));
                x.setXp(x.getXp() - xp);

                if(x.getXp() < 10)
                {
                    xp += x.getXp();
                    spatialTickets.remove(x);
                }

                XP.xp(p, x.getSkill(), xp);
            }
        }
    }

    public void join(Player p)
    {
        if(!players.containsKey(p))
        {
            players.put(p, new AdaptPlayer(p));
        }
    }

    public void quit(Player p)
    {
        if(players.containsKey(p))
        {
            players.remove(p).unregister();
        }
    }

    @Override
    public void unregister()
    {
        for(Player i : players.k())
        {
            quit(i);
        }
        skillRegistry.unregister();
        super.unregister();
    }

    @EventHandler
    public void on(PlayerJoinEvent e)
    {
        join(e.getPlayer());
    }

    @EventHandler
    public void on(PlayerQuitEvent e)
    {
        quit(e.getPlayer());
    }

    @Override
    public void onTick() {
        synchronized (spatialTickets)
        {
            for(int i = 0; i < spatialTickets.size(); i++)
            {
                if(M.ms() > spatialTickets.get(i).getMs())
                {
                    spatialTickets.remove(i);
                }
            }
        }
    }

    public AdaptPlayer getPlayer(Player p) {
        return players.get(p);
    }
}
