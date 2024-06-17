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

import com.volmit.adapt.util.decree.DecreeParameterHandler;
import com.volmit.adapt.util.decree.exceptions.DecreeParsingException;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public class OptionalWorldHandler implements DecreeParameterHandler<String> {
    @Override
    public List<String> getPossibilities() {
        List<String> options = new ArrayList<>();
        options.add("ALL");
        for (World world : Bukkit.getWorlds()) {
            if (!world.getName().toLowerCase().startsWith("iris/")) {
                options.add(world.getName());
            }
        }
        return options;
    }

    @Override
    public String toString(String world) {
        return world;
    }

    @Override
    public String parse(String in, boolean force) throws DecreeParsingException {
        return in;
    }

    @Override
    public boolean supports(Class<?> type) {
        return false;
    }

    @Override
    public String getRandomDefault() {
        return "ALL";
    }
}
