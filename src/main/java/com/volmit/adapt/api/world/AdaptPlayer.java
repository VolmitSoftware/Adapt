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
import com.volmit.adapt.api.notification.AdvancementNotification;
import com.volmit.adapt.api.notification.Notifier;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.util.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

    public AdaptPlayer(Player p) {
        super("players", p.getUniqueId().toString(), 50);
        this.player = p;
        data = loadPlayerData();
        updatelatch = new ChronoLatch(1000);
        savelatch = new ChronoLatch(60000);
        not = new Notifier(this);
        actionBarNotifier = new Notifier(this);
        advancementHandler = new AdvancementHandler(this);
        speed = new RollingSequence(7);
        lastloc = M.ms();
        getAdvancementHandler().activate();
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

    @SneakyThrows
    private void save() {
        UUID uuid = player.getUniqueId();
        String data = new Gson().toJson(this.data);

        if (AdaptConfig.get().isUseSql()) {
            Adapt.instance.getSqlManager().updateData(uuid, data);
        } else {
            IO.writeAll(getPlayerDataFile(uuid), new JSONObject(data).toString(4));
        }
    }

    @SneakyThrows
    private void unSave() {
        UUID uuid = player.getUniqueId();
        String data = new Gson().toJson(new PlayerData());
        unregister();

        if (AdaptConfig.get().isUseSql()) {
            Adapt.instance.getSqlManager().updateData(uuid, data);
        } else {
            IO.writeAll(getPlayerDataFile(uuid), new JSONObject(data).toString(4));
        }
    }

    @Override
    public void unregister() {
        super.unregister();
        getAdvancementHandler().deactivate();
        save();
    }

    @SneakyThrows
    public void delete(UUID uuid) {
        File local = getPlayerDataFile(player.getUniqueId());
        Adapt.warn("Deleting Player Data: " + local.getAbsolutePath());
        Player p = player;
        J.s(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            p.kickPlayer("Your data has been deleted.");
            if (local.exists()) {
                local.delete();
                unSave();
                local.delete();
                save();
                local.delete();
                unSave();
            }
            if (AdaptConfig.get().isUseSql()) {
                Adapt.instance.getSqlManager().delete(uuid);
            }
        });
    }

    private PlayerData loadPlayerData() {
        boolean upload = false;
        if (AdaptConfig.get().isUseSql()) {
            String sqlData = Adapt.instance.getSqlManager().fetchData(player.getUniqueId());
            if (sqlData != null) {
                return new Gson().fromJson(sqlData, PlayerData.class);
            }
            upload = true;
        }

        File f = getPlayerDataFile(player.getUniqueId());
        if (f.exists()) {
            try {
                String text = IO.readAll(f);
                if (upload) {
                    Adapt.instance.getSqlManager().updateData(player.getUniqueId(), text);
                }
                return new Gson().fromJson(text, PlayerData.class);
            } catch (Throwable ignored) {
                Adapt.verbose("Failed to load player data for " + player.getName() + " (" + player.getUniqueId() + ")");
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
        String skillLine = id.split("-")[0];
        Adapt.verbose("Checking for adaptation " + id + " in skill line " + skillLine);
        if (skillLine == null)
            return false;
        PlayerSkillLine line = getData().getSkillLine(skillLine);
        Adapt.verbose("Found skill line " + line);
        if (line.getAdaptation(id) == null || line.getAdaptation(id).getLevel() == 0) {
            Adapt.verbose("Adaptation " + id + " not found or level 0");
            return false;
        }
        return line.getAdaptation(id).getLevel() > 0;
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
            getNot().queue(AdvancementNotification.builder()
                    .title(first ? Localizer.dLocalize("snippets", "gui", "welcome") : Localizer.dLocalize("snippets", "gui", "welcomeback"))
                    .description("+" + C.GREEN + Form.pc(boostAmount, 0) + C.GRAY + " " + Localizer.dLocalize("snippets", "gui", "xpbonusfortime") + " " + C.AQUA + Form.duration(boostTime, 0))
                    .build());
        }
    }

    public boolean hasSkill(Skill s) {
        return getData().getSkillLines().containsKey(s.getName()) && getData().getSkillLines().get(s.getId()).getXp() > 1;
    }

    private File getPlayerDataFile(UUID uuid) {
        return new File(Adapt.instance.getDataFolder("data", "players"), uuid.toString() + ".json");
    }
}
