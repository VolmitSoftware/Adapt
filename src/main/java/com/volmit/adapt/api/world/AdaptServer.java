package com.volmit.adapt.api.world;

import com.google.gson.Gson;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.notification.ActionBarNotification;
import com.volmit.adapt.api.notification.AdvancementNotification;
import com.volmit.adapt.api.notification.SoundNotification;
import com.volmit.adapt.api.notification.TitleNotification;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.skill.SkillRegistry;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.api.xp.SpatialXP;
import com.volmit.adapt.api.xp.XP;
import com.volmit.adapt.content.item.ExperienceOrb;
import com.volmit.adapt.content.item.KnowledgeOrb;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.IO;
import com.volmit.adapt.util.Inventories;
import com.volmit.adapt.util.Items;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.KMap;
import com.volmit.adapt.util.M;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class AdaptServer extends TickedObject {
    private final KMap<Player, AdaptPlayer> players;
    private final KList<SpatialXP> spatialTickets;
    @Getter
    private SkillRegistry skillRegistry;

    public AdaptServer() {
        super("core", UUID.randomUUID().toString(), 1000);
        spatialTickets = new KList<>();
        players = new KMap<>();
        try {
            skillRegistry = new SkillRegistry();
        } catch(IOException e) {
            e.printStackTrace();
        }

        for(Player i : Bukkit.getServer().getOnlinePlayers()) {
            join(i);
        }
    }

    public void offer(SpatialXP xp) {
        spatialTickets.add(xp);
    }

    public void takeSpatial(AdaptPlayer p) {
        SpatialXP x = spatialTickets.getRandom();

        if(x == null) {
            return;
        }

        if(M.ms() > x.getMs()) {
            spatialTickets.remove(x);
            return;
        }

        if(p.getPlayer().getWorld().equals(x.getLocation().getWorld())) {
            double c = p.getPlayer().getLocation().distanceSquared(x.getLocation());
            if(c < x.getRadius() * x.getRadius()) {
                double distl = M.lerpInverse(0, x.getRadius() * x.getRadius(), c);
                double xp = x.getXp() / (1.5D * ((distl * 9) + 1));
                x.setXp(x.getXp() - xp);

                if(x.getXp() < 10) {
                    xp += x.getXp();
                    spatialTickets.remove(x);
                }

                XP.xp(p, x.getSkill(), xp);
            }
        }
    }

    public void join(Player p) {
        if(!players.containsKey(p)) {
            players.put(p, new AdaptPlayer(p));
            players.get(p).loggedIn();
        }
    }

    public void quit(Player p) {
        if(players.containsKey(p)) {
            players.remove(p).unregister();
        }
    }

    @Override
    public void unregister() {
        for(Player i : players.k()) {
            quit(i);
        }
        skillRegistry.unregister();
        super.unregister();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(ProjectileLaunchEvent e)
    {
        if(e.getEntity() instanceof Snowball s && e.getEntity().getShooter() instanceof Player p)
        {
            KnowledgeOrb.Data data = KnowledgeOrb.get(s.getItem());

            if(data != null) {
                Skill<?> skill = getSkillRegistry().getSkill(data.getSkill());
                s.remove();
                data.apply(p);
                SoundNotification.builder()
                    .sound(Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM)
                    .volume(0.35f).pitch(1.455f)
                    .build().play(getPlayer(p));
                SoundNotification.builder()
                    .sound(Sound.ENTITY_SHULKER_OPEN)
                    .volume(1f).pitch(1.655f)
                    .build().play(getPlayer(p));
                getPlayer(p).getNot().queue(AdvancementNotification.builder()
                        .icon(Material.BOOK)
                    .title(C.GRAY + "+ " + C.WHITE + data.getKnowledge() + " " + skill.getDisplayName() + " Knowledge")
                    .build());
            }

            else
            {
                ExperienceOrb.Data datax = ExperienceOrb.get(s.getItem());

                if(datax != null) {
                    s.remove();
                    datax.apply(p);
                    SoundNotification.builder()
                        .sound(Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM)
                        .volume(0.35f).pitch(1.455f)
                        .build().play(getPlayer(p));
                    SoundNotification.builder()
                        .sound(Sound.ENTITY_SHULKER_OPEN)
                        .volume(1f).pitch(1.655f)
                        .build().play(getPlayer(p));
                }
            }
        }
    }

    @EventHandler
    public void on(PlayerJoinEvent e) {
        join(e.getPlayer());
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        quit(e.getPlayer());
    }

    @EventHandler
    public void on(CraftItemEvent e) {
        if(e.getWhoClicked() instanceof Player p) {
            for(Skill<?> i : getSkillRegistry().getSkills()) {
                for(Adaptation<?> j : i.getAdaptations()) {
                    if(j.isAdaptationRecipe(e.getRecipe()) && !j.hasAdaptation(p)) {
                        Adapt.actionbar(p, C.RED + "Requires " + j.getDisplayName() + C.RED + " from " + i.getDisplayName());
                        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.8f);
                        e.setCancelled(true);
                    }
                }
            }
        }
    }

    @Override
    public void onTick() {
        synchronized(spatialTickets) {
            for(int i = 0; i < spatialTickets.size(); i++) {
                if(M.ms() > spatialTickets.get(i).getMs()) {
                    spatialTickets.remove(i);
                }
            }
        }
    }

    public PlayerData peekData(UUID player)
    {
        if(Bukkit.getPlayer(player) != null)
        {
            return getPlayer(Bukkit.getPlayer(player)).getData();
        }

        File f = new File(Bukkit.getServer().getPluginManager().getPlugin(Adapt.instance.getName()).getDataFolder() + File.separator + "data" + File.separator + "players" + File.separator + player + ".json");

        if(f.exists()) {
            try {
                return new Gson().fromJson(IO.readAll(f), PlayerData.class);
            } catch(Throwable ignored) {

            }
        }

        return new PlayerData();
    }

    public AdaptPlayer getPlayer(Player p) {
        return players.get(p);
    }
}
