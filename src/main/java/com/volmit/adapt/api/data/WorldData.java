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

package com.volmit.adapt.api.data;

import art.arcane.spatial.mantle.Mantle;
import art.arcane.spatial.matter.ClassReader;
import art.arcane.spatial.matter.SpatialMatter;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.data.unit.Earnings;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.collection.KMap;
import lombok.Getter;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public class WorldData extends TickedObject {
    private static final KMap<World, WorldData> mantles = new KMap<>();

    static {
        SpatialMatter.registerSliceType(new Earnings.EarningsMatter());
        ClassReader.add(WorldData.class.getClassLoader());
    }

    private final World world;
    @Getter
    private final Mantle mantle;

    public WorldData(World world) {
        super("world-data", world.getUID().toString(), 30_000);
        this.world = world;
        mantle = new Mantle(Adapt.instance.getDataFolder("data", "mantle", world.getName()), world.getMaxHeight());
    }

    public static void stop() {
        mantles.v().forEach(WorldData::unregister);
    }

    public static WorldData of(World world) {
        return mantles.computeIfAbsent(world, WorldData::new);
    }

    public double getEarningsMultiplier(Block block) {
        Earnings e = mantle.get(block.getX(), block.getY(), block.getZ(), Earnings.class);

        if (e == null) {
            return 1;
        }

        return 1 / (double) (e.getEarnings() == 0 ? 1 : e.getEarnings());
    }

    public double reportEarnings(Block block) {
        Earnings e = mantle.get(block.getX(), block.getY(), block.getZ(), Earnings.class);
        e = e == null ? new Earnings(0) : e;

        if (e.getEarnings() >= 127) {
            return 1 / (double) (e.getEarnings() == 0 ? 1 : e.getEarnings());
        }

        mantle.set(block.getX(), block.getY(), block.getZ(), e.increment());
        return 1 / (double) (e.getEarnings() == 0 ? 1 : e.getEarnings());
    }

    public void unregister() {
        super.unregister();
        mantle.close();
        mantles.remove(world);
    }

    @EventHandler
    public void on(WorldSaveEvent e) {
        if (e.getWorld() != world) return;
        J.a(mantle::saveAll);
    }

    @EventHandler
    public void on(WorldUnloadEvent e) {
        if (e.getWorld() != world) return;
        unregister();
    }

    @Override
    public void onTick() {
        mantle.trim(60_000);
    }
}
