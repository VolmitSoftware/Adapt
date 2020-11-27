package com.volmit.adapt.api.world;

import com.google.gson.Gson;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.notification.Notifier;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.util.*;
import lombok.Data;
import lombok.SneakyThrows;
import org.bukkit.Sound;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.io.File;

@Data
public class AdaptPlayer extends TickedObject {
    private final Player player;
    private final PlayerData data;
    private ChronoLatch savelatch;
    private ChronoLatch barlatch;
    private ChronoLatch updatelatch;
    private Notifier not;
    private KMap<String, BossBar> bars;

    public AdaptPlayer(Player p) {
        super("players", p.getUniqueId().toString(), 50);
        this.player = p;
        bars = new KMap<>();
        data = loadPlayerData();
        updatelatch = new ChronoLatch(1000);
        barlatch = new ChronoLatch(125);
        savelatch = new ChronoLatch(60000);
        not = new Notifier(p);
    }

    public boolean isBusy()
    {
        return not.isBusy();
    }

    public PlayerSkillLine getSkillLine(String l)
    {
        return getData().getSkillLine(l);
    }

    @SneakyThrows
    private void save()
    {
        IO.writeAll(new File("data/players/" + player.getUniqueId() + ".json"),  new JSONObject(new Gson().toJson(data)).toString(4));
    }

    @Override
    public void unregister()
    {
        super.unregister();
        save();
    }

    private PlayerData loadPlayerData() {
        File f = new File("data/players/" + player.getUniqueId() + ".json");

        if(f.exists())
        {
            try
            {
                return new Gson().fromJson(IO.readAll(f), PlayerData.class);
            }

            catch (Throwable e)
            {

            }
        }

        return new PlayerData();
    }

    @Override
    public void onTick() {
        if(updatelatch.flip())
        {
            getData().update(this);
        }

        if(savelatch.flip())
        {
            save();
        }

        if(barlatch.flip())
        {
            tickBars();
        }

        getServer().takeSpatial(this);
    }

    public void tickBars()
    {
        for(PlayerSkillLine ps : getData().getSkillLines().v())
        {
            Skill s = getServer().getSkillRegistry().getSkill(ps.getLine());

            if(s == null)
            {
                Adapt.warn("No Skill for " + ps.getLine());
                continue;
            }

            if(ps.hasEarnedWithin(3500))
            {
                if(!bars.containsKey(s.getName()))
                {
                    BossBar bb = s.newBossBar();
                    bars.put(s.getName(), bb);
                    bb.addPlayer(getPlayer());
                }

                bars.get(s.getName()).setProgress(ps.getLevelProgress());
                bars.get(s.getName()).setTitle(s.getDisplayName() + C.RESET + " " + ps.getLevel() + (ps.getMultiplier() != 1D ? (" (" +(ps.getMultiplier()-1D < 0 ? "" : "+") + Form.pc(ps.getMultiplier()-1D, 0) + ")") : ""));
            }

            else
            {
                if(bars.containsKey(s.getName()))
                {
                    BossBar b = bars.remove(s.getName());
                    b.removeAll();
                    b.setVisible(false);
                }
            }
        }
    }
}
