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

package com.volmit.adapt.nms;

import com.google.common.collect.ImmutableMap;
import com.volmit.adapt.Adapt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class NMS {

    private static final Map<String, Impl> VERSIONS = new ImmutableMap.Builder<String, Impl>()
            .put("1.20.4", new NMS_1_20_3())
            .build();

    private static String version;
    private static Impl impl;

    public static void init() {
        version = Bukkit.getBukkitVersion().split("-")[0];
        impl = VERSIONS.getOrDefault(version(), new NMS_Default());

        if (impl instanceof NMS_Default) {
            Adapt.error("Failed to bind NMS for Version " + version() + "!");
        } else {
            Adapt.info("Successfully bound NMS for Version " + version() + ".");
        }
    }

    public static String version() {
        return version;
    }

    public static Impl get() {
        return impl;
    }

    public interface Impl {

        String serializeStack(ItemStack is);

        ItemStack deserializeStack(String s);

        void sendCooldown(Player p, Material m, int tick);
    }
}
