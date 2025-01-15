/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.volmit.adapt.util.decree.handlers;

import com.volmit.adapt.util.collection.KList;
import com.volmit.adapt.util.decree.DecreeParameterHandler;
import com.volmit.adapt.util.decree.exceptions.DecreeParsingException;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.List;

public class WorldHandler implements DecreeParameterHandler<World> {
    @Override
    public KList<World> getPossibilities() {
        KList<World> options = new KList<>();
        for (World world : Bukkit.getWorlds()) {
            if (!world.getName().toLowerCase().startsWith("adapt/")) {
                options.add(world);
            }
        }
        return options;
    }

    @Override
    public String toString(World world) {
        return world.getName();
    }

    @Override
    public World parse(String in, boolean force) throws DecreeParsingException {
        List<World> options = getPossibilities(in);

        if (options.isEmpty()) {
            throw new DecreeParsingException("Unable to find World \"" + in + "\"");
        }
        try {
            return options.stream().filter((i) -> toString(i).equalsIgnoreCase(in)).toList().get(0);
        } catch (Throwable e) {
            throw new DecreeParsingException("Unable to filter which Biome \"" + in + "\"");
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(World.class);
    }

    @Override
    public String getRandomDefault() {
        return "world";
    }
}
