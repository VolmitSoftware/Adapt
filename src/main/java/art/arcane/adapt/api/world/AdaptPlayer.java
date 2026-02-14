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
import art.arcane.volmlib.util.format.Form;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.notification.AdvancementNotification;
import art.arcane.adapt.api.notification.Notifier;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RollingSequence;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.scheduling.J;

@EqualsAndHashCode(callSuper = false)
@Data
public class AdaptPlayer extends TickedObject {
    private final Player player;
    private final PlayerData data;
    private ChronoLatch savelatch;
    private ChronoLatch updatelatch;
    private Notifier not;
    private Notifier actionBarNotifier;
    private AdvancementHandler advancementHandler;
    private RollingSequence speed;
    private long lastloc;
    private Vector velocity;
    private Location lastpos;
    private long lastSeen;
    private volatile boolean pendingDataDeletion;

    public AdaptPlayer(Player p) {
        this(p, null);
    }

    public AdaptPlayer(Player p, PlayerData prefetchedData) {
        super("players", p.getUniqueId().toString(), 50);
        this.player = p;
        data = prefetchedData == null ? loadPlayerData(p.getUniqueId()) : prefetchedData;
        updatelatch = new ChronoLatch(1000);
        savelatch = new ChronoLatch(60000);
        not = new Notifier(this);
        actionBarNotifier = new Notifier(this);
        advancementHandler = new AdvancementHandler(this);
        speed = new RollingSequence(7);
        lastloc = M.ms();
        lastSeen = M.ms();
        velocity = new Vector();
    }

    public boolean canConsumeFood(double cost, int minFood) {
        return (player.getFoodLevel() + player.getSaturation()) - minFood > cost;
    }

    public boolean consumeFood(double cost, int minFood) {
        if (canConsumeFood(cost, minFood)) {
            int food = player.getFoodLevel();
            double sat = player.getSaturation();

            if (sat >= cost) {
                sat = (player.getSaturation() - cost);
                cost = 0;
            } else if (player.getSaturation() > 0) {
                cost -= sat;
                sat = 0;
            }

            if (cost >= 1) {
                food -= (int) Math.floor(cost);
                cost = Math.floor(cost);
            }

            if (cost > 0) {
                if (sat >= cost) {
                    sat -= cost;
                    cost = 0;
                } else {
                    sat++;
                    food--;
                }
            }

            if (sat >= cost && cost > 0) {
                sat -= cost;
                cost = 0;
            }

            player.setFoodLevel(food);
            player.setSaturation((float) sat);

            return true;
        }

        return false;
    }

    public boolean isBusy() {
        return not.isBusy();
    }

    public PlayerSkillLine getSkillLine(String l) {
        return getData().getSkillLine(l);
    }

    private void save() {
        UUID uuid = player.getUniqueId();
        File playerDataFile = getPlayerDataFile(uuid);

        if (pendingDataDeletion) {
            queueDelete(uuid, playerDataFile);
            return;
        }

        String json = this.data.toJson(AdaptConfig.get().isUseSql());
        PlayerDataPersistenceQueue queue = Adapt.instance.getPlayerDataPersistenceQueue();
        if (queue != null) {
            queue.queueSave(uuid, json, playerDataFile);
            return;
        }

        if (AdaptConfig.get().isUseSql()) {
            if (Adapt.instance.getRedisSync() != null) {
                Adapt.instance.getRedisSync().publish(uuid, json);
            }
            if (Adapt.instance.getSqlManager() != null) {
                Adapt.instance.getSqlManager().updateData(uuid, json);
            }
        } else {
            J.attempt(() -> IO.writeAll(playerDataFile, json));
        }
    }

    @Override
    public void unregister() {
        super.unregister();
        save();
    }

    public void delete(UUID uuid) {
        pendingDataDeletion = true;
        File local = getPlayerDataFile(uuid);
        Adapt.warn("Deleting Player Data: " + local.getAbsolutePath());
        queueDelete(uuid, local);

        Player p = player;
        if (!p.isOnline()) {
            return;
        }

        J.s(() -> p.kickPlayer("Your data has been deleted."), 20);
    }

    public boolean shouldUnload() {
        if (player.isOnline()) {
            lastSeen = M.ms();
            return false;
        }

        return lastSeen + 60_000 < System.currentTimeMillis();
    }

    public static PlayerData loadPlayerData(UUID uuid) {
        boolean upload = false;
        if (AdaptConfig.get().isUseSql()) {
            if (Adapt.instance.getRedisSync() != null) {
                var opt = Adapt.instance.getRedisSync().cachedData(uuid);
                if (opt.isPresent()) {
                    Adapt.verbose("Using cached data for player: " + uuid);
                    return opt.get();
                }
            }

            if (Adapt.instance.getSqlManager() != null) {
                String sqlData = Adapt.instance.getSqlManager().fetchData(uuid);
                if (sqlData != null) {
                    return PlayerData.fromJson(sqlData);
                }
                upload = true;
            }
        }

        File f = getPlayerDataFile(uuid);
        if (f.exists()) {
            try {
                String text = IO.readAll(f);
                if (upload) {
                    PlayerDataPersistenceQueue queue = Adapt.instance.getPlayerDataPersistenceQueue();
                    if (queue != null) {
                        queue.queueSave(uuid, text, f);
                    } else if (Adapt.instance.getSqlManager() != null) {
                        Adapt.instance.getSqlManager().updateData(uuid, text);
                    }
                }
                return PlayerData.fromJson(text);
            } catch (Throwable ignored) {
                Adapt.verbose("Failed to load player data for " + uuid);
            }
        }

        return new PlayerData();
    }

    @Override
    public void onTick() {
        if (updatelatch.flip()) {
            getData().update(this);
        }

        if (savelatch.flip()) {
            save();
        }

        getServer().takeSpatial(this);

        Location at = player.getLocation();

        if (lastpos != null) {
            if (lastpos.getWorld().equals(at.getWorld())) {
                if (lastpos.distanceSquared(at) <= 7 * 7) {
                    speed.put(lastpos.distance(at) / ((double) (M.ms() - lastloc) / 50D));
                    velocity = velocity.clone().add(at.clone().subtract(lastpos).toVector()).multiply(0.5);
                    velocity.setX(Math.abs(velocity.getX()) < 0.01 ? 0 : velocity.getX());
                    velocity.setY(Math.abs(velocity.getY()) < 0.01 ? 0 : velocity.getY());
                    velocity.setZ(Math.abs(velocity.getZ()) < 0.01 ? 0 : velocity.getZ());
                }
            }
        }

        lastpos = at.clone();
        lastloc = M.ms();
    }

    public double getSpeed() {
        return speed.getAverage();
    }

    public boolean hasAdaptation(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        int separator = id.indexOf('-');
        if (separator <= 0) {
            return false;
        }

        String skillLine = id.substring(0, separator);
        if (skillLine.isBlank()) {
            return false;
        }

        PlayerSkillLine line = getData().getSkillLine(skillLine);
        if (line == null) {
            return false;
        }

        PlayerAdaptation adaptation = line.getAdaptation(id);
        return adaptation != null && adaptation.getLevel() > 0;
    }

    public void giveXPToRecents(AdaptPlayer p, double xpGained, int ms) {
        for (PlayerSkillLine i : p.getData().getSkillLines().v()) {
            if (M.ms() - i.getLast() < ms) {
                i.giveXP(not, xpGained);
            }
        }
    }

    public void giveXPToRandom(AdaptPlayer p, double xpGained) {
        p.getData().getSkillLines().v().getRandom().giveXP(p.getNot(), xpGained);
    }

    public void boostXPToRandom(AdaptPlayer p, double boost, int ms) {
        p.getData().getSkillLines().v().getRandom().boost(boost, ms);
    }

    public void boostXPToRecents(double boost, int ms) {
        for (PlayerSkillLine i : this.getData().getSkillLines().v()) {
            if (M.ms() - i.getLast() < ms) {
                i.boost(boost, ms);
            }
        }
    }

    public void loggedIn() {
        lastSeen = M.ms();
        if (AdaptConfig.get().isLoginBonus()) {
            long timeGone = M.ms() - getData().getLastLogin();
            boolean first = getData().getLastLogin() == 0;
            getData().setLastLogin(M.ms());
            long boostTime = (long) Math.min(timeGone / 12D, TimeUnit.HOURS.toMillis(1));
            if (boostTime < TimeUnit.MINUTES.toMillis(5)) {
                return;
            }
            double boostAmount = M.lerp(0.1, 0.25, (double) boostTime / (double) TimeUnit.HOURS.toMillis(1));
            getData().globalXPMultiplier(boostAmount, (int) boostTime);
            if (!AdaptConfig.get().isWelcomeMessage())
                return;
            getNot().queue(AdvancementNotification.builder()
                    .title(first ? Localizer.dLocalize("snippets.gui.welcome") : Localizer.dLocalize("snippets.gui.welcome_back"))
                    .description("+" + C.GREEN + Form.pc(boostAmount, 0) + C.GRAY + " " + Localizer.dLocalize("snippets.gui.xp_bonus_for_time") + " " + C.AQUA + Form.duration(boostTime, 0))
                    .model(CustomModel.get(Material.DIAMOND, "snippets", "gui", first ? "welcome" : "welcomeback"))
                    .build());
        }
    }

    public boolean hasSkill(Skill s) {
        if (s == null) {
            return false;
        }

        PlayerSkillLine line = getData().getSkillLine(s.getName());
        return line != null && line.getXp() > 1;
    }

    private static File getPlayerDataFile(UUID uuid) {
        return new File(Adapt.instance.getDataFolder("data", "players"), uuid.toString() + ".json");
    }

    private void queueDelete(UUID uuid, File localFile) {
        PlayerDataPersistenceQueue queue = Adapt.instance.getPlayerDataPersistenceQueue();
        if (queue != null) {
            queue.queueDelete(uuid, localFile);
            return;
        }

        if (localFile.exists() && !localFile.delete()) {
            Adapt.verbose("Failed to delete local player data file " + localFile.getAbsolutePath());
        }
        if (AdaptConfig.get().isUseSql() && Adapt.instance.getSqlManager() != null) {
            Adapt.instance.getSqlManager().delete(uuid);
        }
    }
}
