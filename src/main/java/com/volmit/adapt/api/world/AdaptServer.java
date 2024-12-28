/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package com.volmit.adapt.api.world;

import com.google.gson.Gson;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.notification.AdvancementNotification;
import com.volmit.adapt.api.notification.SoundNotification;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.skill.SkillRegistry;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.api.xp.SpatialXP;
import com.volmit.adapt.api.xp.XP;
import com.volmit.adapt.api.xp.XPMultiplier;
import com.volmit.adapt.content.gui.SkillsGui;
import com.volmit.adapt.content.item.ExperienceOrb;
import com.volmit.adapt.content.item.KnowledgeOrb;
import com.volmit.adapt.util.*;
import lombok.Getter;
import lombok.SneakyThrows;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class AdaptServer extends TickedObject {
    private final ReentrantLock clearLock = new ReentrantLock();
    private final Map<Player, AdaptPlayer> players = new ConcurrentHashMap<>();
    @Getter
    private final List<SpatialXP> spatialTickets = new ArrayList<>();
    @Getter
    private final SkillRegistry skillRegistry = new SkillRegistry();
    @Getter
    private AdaptServerData data = new AdaptServerData();

    public AdaptServer() {
        super("core", UUID.randomUUID().toString(), 1000);
        load();

        Bukkit.getOnlinePlayers().forEach(this::join);
    }

    public void offer(SpatialXP xp) {
        if (xp == null || xp.getSkill() == null || xp.getRadius() > 0 || xp.getMs() > 0 || xp.getLocation() == null) {
            return;
        }
        spatialTickets.add(xp);
    }

    public void takeSpatial(AdaptPlayer p) {
        J.attempt(() -> {
            Optional<SpatialXP> optX = spatialTickets.stream().findAny();
            if (optX.isEmpty()) {
                return;
            }
            SpatialXP x = optX.get();
            if (M.ms() > x.getMs()) {
                spatialTickets.remove(x);
                return;
            }
            if (!p.getPlayer().getClass().getSimpleName().equals("CraftPlayer")) {
                spatialTickets.remove(x);
                return;
            }
            if (p.getPlayer().getWorld().equals(x.getLocation().getWorld())) {
                double c = p.getPlayer().getLocation().distanceSquared(x.getLocation());
                if (c < x.getRadius() * x.getRadius()) {
                    double distl = M.lerpInverse(0, x.getRadius() * x.getRadius(), c);
                    double xp = x.getXp() / (1.5D * ((distl * 9) + 1));
                    x.setXp(x.getXp() - xp);

                    if (x.getXp() < 10) {
                        xp += x.getXp();
                        spatialTickets.remove(x);
                    }

                    XP.xp(p, x.getSkill(), xp);
                }
            }
        });
    }

    public void join(Player p) {
        AdaptPlayer a = new AdaptPlayer(p);
        players.put(p, a);
    }

    public void quit(Player p) {
        Optional.ofNullable(players.remove(p)).ifPresent((ap) -> {
            ap.unregister();
            if (AdaptConfig.get().isUseSql()) {
                Adapt.instance.getSqlManager().updateTime(p.getUniqueId(), 0L);
            }
        });
    }

    @Override
    public void unregister() {
        new HashSet<>(players.keySet()).forEach(this::quit);
        skillRegistry.unregister();
        save();
        super.unregister();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void on(ProjectileLaunchEvent e) {
        if (e.getEntity() instanceof Snowball s && e.getEntity().getShooter() instanceof Player p) {
            KnowledgeOrb.Data data = KnowledgeOrb.get(s.getItem());
            if (data != null) {
                Skill<?> skill = getSkillRegistry().getSkill(data.getSkill());
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
                    .model(CustomModel.get(Material.BOOK, "snippets", "gui", "knowledge"))
                    .title(C.GRAY + "+ " + C.WHITE + data.getKnowledge() + " " + skill.getDisplayName() + " Knowledge")
                    .build());
            } else {
                ExperienceOrb.Data datax = ExperienceOrb.get(s.getItem());
                if (datax != null) {
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void on(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        join(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        quit(p);
    }

    @EventHandler
    public void on(CraftItemEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            SoundPlayer sp = SoundPlayer.of(p);
            for (Skill<?> i : getSkillRegistry().getSkills()) {
                for (Adaptation<?> j : i.getAdaptations()) {
                    if (j.isAdaptationRecipe(e.getRecipe()) && !j.hasAdaptation(p)) {
                        Adapt.actionbar(p, C.RED + "Requires " + j.getDisplayName() + C.RED + " from " + i.getDisplayName());
                        sp.play(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.8f);
                        e.setCancelled(true);
                    }
                }
            }
        }
    }

    @Override
    public void onTick() {
        synchronized (spatialTickets) {
            spatialTickets.removeIf(ticket -> M.ms() > ticket.getMs());
        }

        J.a(() -> {
            if (!clearLock.tryLock())
                return;

            try {
                players.keySet().removeIf(player -> !player.isOnline());
            } finally {
                clearLock.unlock();
            }
        });
    }

    public PlayerData peekData(UUID player) {
        if (Bukkit.getPlayer(player) != null) {
            return getPlayer(Bukkit.getPlayer(player)).getData();
        }

        if (AdaptConfig.get().isUseSql()) {
            String sqlData = Adapt.instance.getSqlManager().fetchData(player);
            if (sqlData != null) {
                return Adapt.gson.fromJson(sqlData, PlayerData.class);
            }
        }

        File f = new File(Adapt.instance.getDataFolder("data", "players"), player + ".json");
        if (f.exists()) {
            try {
                return Adapt.gson.fromJson(IO.readAll(f), PlayerData.class);
            } catch (Throwable ignored) {
                Adapt.verbose("Failed to load player data for " + player);
            }
        }

        return new PlayerData();
    }

    public AdaptPlayer getPlayer(Player p) {
        if (players.containsKey(p)) {
            return players.get(p);
        }
        Adapt.warn("Failed to find AdaptPlayer for " + p.getName() + " (" + p.getUniqueId() + ")");
        Adapt.warn("Loading new AdaptPlayer...");
        throw new RuntimeException("Failed to find AdaptPlayer for " + p.getName() + " (" + p.getUniqueId() + ")");
    }

    public List<Player> getAdaptPlayers() {
        List<Player> result = new ArrayList<>(players.size());
        for (Map.Entry<Player, AdaptPlayer> entry : players.entrySet()) {
            if (entry.getValue().isActive()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public void openSkillGUI(Skill<?> skill, Player p) {
        skill.openGui(p);
    }

    public void openAdaptGui(Player p) {
        SkillsGui.open(p);
    }

    public void openAdaptationGUI(Adaptation<?> adaptation, Player p) {
        adaptation.openGui(p);
    }

    public void boostXP(double boost, int ms) {
        data.getMultipliers().add(new XPMultiplier(boost, ms));
    }

    public void load() {
        File f = new File(Adapt.instance.getDataFolder("data"), "server-data.json");
        if (f.exists()) {
            try {
                String text = IO.readAll(f);
                data = Adapt.gson.fromJson(text, AdaptServerData.class);
            } catch (Throwable ignored) {
                Adapt.verbose("Failed to load global boosts data");
            }
        }
    }

    @SneakyThrows
    public void save() {
        IO.writeAll(new File(Adapt.instance.getDataFolder("data"), "server-data.json"), new JSONObject(data).toString(4));
    }
}
