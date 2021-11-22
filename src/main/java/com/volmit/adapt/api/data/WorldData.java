package com.volmit.adapt.api.data;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.KMap;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.cyberpwn.spatial.mantle.Mantle;

public class WorldData extends TickedObject
{
    private static final KMap<World, WorldData> mantles = new KMap<>();
    private final World world;
    private final Mantle mantle;

    public WorldData(World world)
    {
        super("world-data", world.getUID().toString(), 30_000);
        this.world = world;
        mantle = new Mantle(Adapt.instance.getDataFolder("data", "mantle"), 256);
    }

    public void unregister()
    {
        super.unregister();
        J.a(mantle::close);
        mantles.remove(world);
    }

    @EventHandler
    public void on(WorldSaveEvent e)
    {
        J.a(mantle::saveAll);
    }

    @EventHandler
    public void on(WorldUnloadEvent e)
    {
        unregister();
    }

    @Override
    public void onTick() {
        mantle.trim(60_000);
    }

    public static WorldData of(World world)
    {
        return mantles.computeIfAbsent(world, WorldData::new);
    }
}
