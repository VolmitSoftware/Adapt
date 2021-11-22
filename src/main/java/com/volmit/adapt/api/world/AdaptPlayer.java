package com.volmit.adapt.api.world;

import com.google.gson.Gson;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.notification.AdvancementNotification;
import com.volmit.adapt.api.notification.Notifier;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.ChronoLatch;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.IO;
import com.volmit.adapt.util.JSONObject;
import com.volmit.adapt.util.M;
import com.volmit.adapt.util.RollingSequence;
import lombok.Data;
import lombok.SneakyThrows;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.concurrent.TimeUnit;

@Data
public class AdaptPlayer extends TickedObject {
    private final Player player;
    private final PlayerData data;
    private ChronoLatch savelatch;
    private ChronoLatch updatelatch;
    private Notifier not;
    private RollingSequence speed;
    private long lastloc;
    private Location lastpos;

    public AdaptPlayer(Player p) {
        super("players", p.getUniqueId().toString(), 50);
        this.player = p;
        data = loadPlayerData();
        updatelatch = new ChronoLatch(1000);
        savelatch = new ChronoLatch(60000);
        not = new Notifier(this);
        speed = new RollingSequence(7);
        lastloc = M.ms();
    }

    public boolean isBusy() {
        return not.isBusy();
    }

    public PlayerSkillLine getSkillLine(String l) {
        return getData().getSkillLine(l);
    }

    @SneakyThrows
    private void save() {
        IO.writeAll(new File(Bukkit.getServer().getPluginManager().getPlugin(Adapt.instance.getName()).getDataFolder() + File.separator + "data" + File.separator + "players" + File.separator + player.getUniqueId() + ".json"), new JSONObject(new Gson().toJson(data)).toString(4));
    }

    @Override
    public void unregister() {
        super.unregister();
        save();
    }

    private PlayerData loadPlayerData() {
        File f = new File(Bukkit.getServer().getPluginManager().getPlugin(Adapt.instance.getName()).getDataFolder() + File.separator + "data" + File.separator + "players" + File.separator + player.getUniqueId() + ".json");

        if(f.exists()) {
            try {
                return new Gson().fromJson(IO.readAll(f), PlayerData.class);
            } catch(Throwable ignored) {

            }
        }

        return new PlayerData();
    }

    @Override
    public void onTick() {
        if(updatelatch.flip()) {
            getData().update(this);
        }

        if(savelatch.flip()) {
            save();
        }

        getServer().takeSpatial(this);

        Location at = player.getLocation();

        if(lastpos != null) {
            if(lastpos.getWorld().equals(at.getWorld())) {
                speed.put(lastpos.distance(at) / ((double) (M.ms() - lastloc) / 50D));
            }
        }

        lastpos = at.clone();
        lastloc = M.ms();
    }

    public double getSpeed() {
        return speed.getAverage();
    }

    public void giveXPToRecents(AdaptPlayer p, double xpGained, int ms) {
        for(PlayerSkillLine i : p.getData().getSkillLines().v()) {
            if(M.ms() - i.getLast() < ms) {
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

    public void boostXPToRecents(AdaptPlayer p, double boost, int ms) {
        for(PlayerSkillLine i : p.getData().getSkillLines().v()) {
            if(M.ms() - i.getLast() < ms) {
                i.boost(boost, ms);
            }
        }
    }

    public void loggedIn() {
        long timeGone = M.ms() - getData().getLastLogin();
        boolean first = getData().getLastLogin() == 0;
        getData().setLastLogin(M.ms());
        long boostTime = (long) Math.min(timeGone / 12D, TimeUnit.HOURS.toMillis(1));

        if(boostTime < TimeUnit.MINUTES.toMillis(5))
        {
            return;
        }

        double boostAmount = M.lerp(0.1, 0.25, (double)boostTime / (double)TimeUnit.HOURS.toMillis(1));
        getData().globalXPMultiplier(boostAmount, (int) boostTime);
        getNot().queue(AdvancementNotification.builder()
                .title(first ? "Welcome!" : "Welcome Back!")
                .description("+" + C.GREEN + Form.pc(boostAmount, 0) + C.GRAY + " XP for " + C.AQUA + Form.duration(boostTime, 0))
            .build());
    }
}
