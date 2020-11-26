package com.volmit.adapt.api.world;

import com.volmit.adapt.api.skill.SkillRegistry;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.util.KMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.IOException;
import java.util.UUID;

public class AdaptServer extends TickedObject {
    private final KMap<Player, AdaptPlayer> players;
    private SkillRegistry skillRegistry;

    public AdaptServer()
    {
        super("core", UUID.randomUUID().toString(), 1000);
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

    }

    public AdaptPlayer getPlayer(Player p) {
        return players.get(p);
    }
}
