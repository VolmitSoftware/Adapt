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

package art.arcane.adapt.api.world;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.notification.AdvancementNotification;
import art.arcane.adapt.api.notification.SoundNotification;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.api.xp.SpatialXP;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.api.xp.XPMultiplier;
import art.arcane.adapt.content.gui.SkillsGui;
import art.arcane.adapt.content.item.ExperienceOrb;
import art.arcane.adapt.content.item.KnowledgeOrb;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.math.M;
import lombok.Getter;
import lombok.NonNull;
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
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.io.Json;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.misc.SoundPlayer;

public class AdaptServer extends TickedObject {
    private final ReentrantLock clearLock = new ReentrantLock();
    private final Map<UUID, AdaptPlayer> players = new ConcurrentHashMap<>();
    private final Cache<UUID, PlayerData> prefetchedPlayerData = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.MINUTES)
            .maximumSize(2048)
            .build();
    @Getter
    private volatile List<Player> onlinePlayerSnapshot = List.of();
    @Getter
    private volatile List<AdaptPlayer> onlineAdaptPlayerSnapshot = List.of();
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
        refreshOnlinePlayerSnapshots();
    }

    public void offer(SpatialXP xp) {
        if (xp == null || xp.getSkill() == null || xp.getLocation() == null) {
            return;
        }
        if (xp.getRadius() <= 0 || xp.getXp() <= 0 || xp.getMs() <= M.ms()) {
            return;
        }
        synchronized (spatialTickets) {
            spatialTickets.add(xp);
        }
    }

    public void takeSpatial(AdaptPlayer p) {
        try {
            SpatialXP x;
            synchronized (spatialTickets) {
                int size = spatialTickets.size();
                if (size == 0) {
                    return;
                }
                x = spatialTickets.get(size - 1);
            }

            if (M.ms() > x.getMs()) {
                synchronized (spatialTickets) {
                    spatialTickets.remove(x);
                }
                return;
            }

            if (!p.getPlayer().getClass().getSimpleName().equals("CraftPlayer")) {
                synchronized (spatialTickets) {
                    spatialTickets.remove(x);
                }
                return;
            }

            if (p.getPlayer().getWorld().equals(x.getLocation().getWorld())) {
                double c = p.getPlayer().getLocation().distanceSquared(x.getLocation());
                if (c < x.getRadius() * x.getRadius()) {
                    double distl = M.lerpInverse(0, x.getRadius() * x.getRadius(), c);
                    double xp = x.getXp() / (1.5D * ((distl * 9) + 1));
                    synchronized (spatialTickets) {
                        x.setXp(x.getXp() - xp);

                        if (x.getXp() < 10) {
                            xp += x.getXp();
                            spatialTickets.remove(x);
                        }
                    }

                    XP.xp(p, x.getSkill(), xp);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public void join(Player p) {
        PlayerData prefetched = takePrefetchedData(p.getUniqueId());
        AdaptPlayer a = new AdaptPlayer(p, prefetched);
        players.put(p.getUniqueId(), a);
        refreshOnlinePlayerSnapshots();
        a.loggedIn();
    }

    public void quit(UUID p) {
        AdaptPlayer a = players.get(p);
        if (a == null) return;
        a.unregister();
        players.remove(p);
        prefetchedPlayerData.invalidate(p);
        refreshOnlinePlayerSnapshots();
    }

    @Override
    public void unregister() {
        new HashSet<>(players.keySet()).forEach(this::quit);
        prefetchedPlayerData.invalidateAll();
        onlinePlayerSnapshot = List.of();
        onlineAdaptPlayerSnapshot = List.of();
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
    public void on(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        UUID uuid = e.getUniqueId();
        if (players.containsKey(uuid) || prefetchedPlayerData.getIfPresent(uuid) != null) {
            return;
        }

        try {
            prefetchedPlayerData.put(uuid, AdaptPlayer.loadPlayerData(uuid));
        } catch (Throwable ignored) {
            Adapt.verbose("Failed to prefetch player data for " + uuid);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        quit(p.getUniqueId());
    }

    @EventHandler
    public void on(CraftItemEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            Adaptation<?> required = getSkillRegistry().getRequiredAdaptation(e.getRecipe());
            if (required == null || required.hasAdaptation(p)) {
                return;
            }

            Skill<?> requiredSkill = required.getSkill();
            String skillName = requiredSkill == null ? "Unknown Skill" : requiredSkill.getDisplayName();
            SoundPlayer sp = SoundPlayer.of(p);
            Adapt.actionbar(p, C.RED + "Requires " + required.getDisplayName() + C.RED + " from " + skillName);
            sp.play(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.8f);
            e.setCancelled(true);
        }
    }

    @Override
    public void onTick() {
        data.getMultipliers().removeIf(multiplier -> multiplier == null || multiplier.isExpired());

        synchronized (spatialTickets) {
            spatialTickets.removeIf(ticket -> M.ms() > ticket.getMs());
        }

        if (!clearLock.tryLock())
            return;

        try {
            int sizeBefore = players.size();
            players.values().removeIf(AdaptPlayer::shouldUnload);
            if (players.size() != sizeBefore) {
                refreshOnlinePlayerSnapshots();
            }
        } finally {
            clearLock.unlock();
        }
    }

    public PlayerData peekData(UUID player) {
        if (Bukkit.getPlayer(player) != null) {
            return getPlayer(Bukkit.getPlayer(player)).getData();
        }

        if (AdaptConfig.get().isUseSql()) {
            String sqlData = Adapt.instance.getSqlManager().fetchData(player);
            if (sqlData != null) {
                return Json.fromJson(sqlData, PlayerData.class);
            }
        }

        File f = new File(Adapt.instance.getDataFolder("data", "players"), player + ".json");
        if (f.exists()) {
            try {
                return Json.fromJson(IO.readAll(f), PlayerData.class);
            } catch (Throwable ignored) {
                Adapt.verbose("Failed to load player data for " + player);
            }
        }

        return new PlayerData();
    }

    @NonNull
    public Optional<PlayerData> getPlayerData(@NonNull UUID uuid) {
        return Optional.ofNullable(players.get(uuid))
                .map(AdaptPlayer::getData);
    }

    public AdaptPlayer getPlayer(Player p) {
        AdaptPlayer existing = players.get(p.getUniqueId());
        if (existing != null) {
            return existing;
        }

        AdaptPlayer created = players.computeIfAbsent(p.getUniqueId(), player -> {
            Adapt.warn("Failed to find AdaptPlayer for " + p.getName() + " (" + p.getUniqueId() + ")");
            Adapt.warn("Loading new AdaptPlayer...");
            return new AdaptPlayer(p, takePrefetchedData(player));
        });
        refreshOnlinePlayerSnapshots();
        return created;
    }

    private PlayerData takePrefetchedData(UUID uuid) {
        PlayerData prefetched = prefetchedPlayerData.getIfPresent(uuid);
        if (prefetched != null) {
            prefetchedPlayerData.invalidate(uuid);
        }
        return prefetched;
    }

    private void refreshOnlinePlayerSnapshots() {
        ArrayList<AdaptPlayer> adaptPlayers = new ArrayList<>(players.size());
        ArrayList<Player> playerSnapshot = new ArrayList<>(players.size());

        for (AdaptPlayer adaptPlayer : players.values()) {
            if (adaptPlayer == null) {
                continue;
            }
            Player online = adaptPlayer.getPlayer();
            if (online == null || !online.isOnline()) {
                continue;
            }
            adaptPlayers.add(adaptPlayer);
            playerSnapshot.add(online);
        }

        onlineAdaptPlayerSnapshot = Collections.unmodifiableList(adaptPlayers);
        onlinePlayerSnapshot = Collections.unmodifiableList(playerSnapshot);
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
                data = Json.fromJson(IO.readAll(f), AdaptServerData.class);
            } catch (Throwable ignored) {
                Adapt.verbose("Failed to load global boosts data");
            }
        }
    }

    @SneakyThrows
    public void save() {
        IO.writeAll(new File(Adapt.instance.getDataFolder("data"), "server-data.json"), Json.toJson(data, true));
    }
}
