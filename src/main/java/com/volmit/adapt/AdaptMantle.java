package com.volmit.adapt;

import com.volmit.adapt.util.J;
import com.volmit.adapt.util.KMap;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.cyberpwn.spatial.mantle.Mantle;
import org.cyberpwn.spatial.util.Consume;

import java.io.File;

public class AdaptMantle implements Listener {
    private final KMap<World, Mantle> mantles;

    public AdaptMantle() {
        mantles = new KMap<>();

        for(World i : Bukkit.getWorlds())
        {
            mantles.put(i, new Mantle(new File(i.getWorldFolder(), "adapt"), i.getMaxHeight() - i.getMinHeight()));
        }
    }

    public <T> void iterate(Chunk chunk, Class<T> type, Consume.Four<Integer, Integer, Integer, T> iterator) {
        mantles.get(chunk.getWorld()).iterateChunk(chunk.getX(), chunk.getZ(), type, (x, y, z, t) -> iterator.accept(x, y + chunk.getWorld().getMinHeight(), z, t));
    }

    public <T> T get(Block block, Class<T> type) {
        return mantles.get(block.getWorld()).get(block.getX(), block.getY() - block.getWorld().getMinHeight(), block.getZ(), type);
    }

    public <T> void set(Block block, T value) {
        mantles.get(block.getWorld()).set(block.getX(), block.getY() - block.getWorld().getMinHeight(), block.getZ(), value);
    }

    public void close() {
        for(Mantle i : mantles.values()) {
            i.close();
        }

        mantles.clear();
    }

    @EventHandler
    public void on(WorldLoadEvent e) {
        mantles.put(e.getWorld(), new Mantle(new File(e.getWorld().getWorldFolder(), "adapt"), e.getWorld().getMaxHeight() - e.getWorld().getMinHeight()));
    }

    @EventHandler
    public void on(WorldUnloadEvent e) {
        Mantle m = mantles.remove(e.getWorld());
        J.a(m::close);
    }
}
