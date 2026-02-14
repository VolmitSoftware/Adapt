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

package art.arcane.adapt.util.common.misc;

import art.arcane.adapt.Adapt;
import org.bukkit.ChatColor;

import art.arcane.adapt.util.common.format.HiddenStringUtils;
import art.arcane.adapt.util.common.io.Json;

public class DirtyString {

    public static String write(Object data) {
        return write(Json.toJson(data, false));
    }

    public static <T> T fromJson(String data, Class<T> t) {
        return Json.fromJson(read(data), t);
    }

    public static boolean has(String data) {
        if (!HiddenStringUtils.hasHiddenString(data)) {
            Adapt.info("Not has in " + data.replaceAll("\\Q" + ChatColor.COLOR_CHAR + "\\E", "&"));
        }
        return HiddenStringUtils.hasHiddenString(data);
    }

    public static String write(String data) {
        return HiddenStringUtils.encodeString(data);
    }

    public static String read(String data) {
        return HiddenStringUtils.extractHiddenString(data);
    }
}

