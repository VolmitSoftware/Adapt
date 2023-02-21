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

package com.volmit.adapt.api.protection;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ProtectorRegistry {
    private static final List<Protector> protectors = new ArrayList<>();

    public static void registerProtector(Protector protector) {
        protectors.add(protector);
    }

    public static void unregisterProtector(Protector protector) {
        protectors.remove(protector);
    }

    public static List<Protector> getDefaultProtectors() {
        return protectors.stream().filter(Protector::isEnabledByDefault).collect(ImmutableList.toImmutableList());
    }

    public static List<Protector> getAllProtectors() {
        return ImmutableList.copyOf(protectors);
    }
}
