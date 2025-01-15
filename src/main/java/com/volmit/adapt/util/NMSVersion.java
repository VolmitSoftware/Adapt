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

package com.volmit.adapt.util;

import java.util.ArrayList;
import java.util.List;

public enum NMSVersion {
    R1_16,
    R1_15,
    R1_14,
    R1_13,
    R1_13_1,
    R1_12,
    R1_11,
    R1_10,
    R1_9_4,
    R1_9_2,
    R1_8;

    public static NMSVersion getMinimum() {
        return values()[values().length - 1];
    }

    public static NMSVersion getMaximum() {
        return values()[0];
    }

    public static NMSVersion current() {
        if (tryVersion("1_8_R3")) {
            return R1_8;
        }

        if (tryVersion("1_9_R1")) {
            return R1_9_2;
        }

        if (tryVersion("1_9_R2")) {
            return R1_9_4;
        }

        if (tryVersion("1_10_R1")) {
            return R1_10;
        }

        if (tryVersion("1_11_R1")) {
            return R1_11;
        }

        if (tryVersion("1_12_R1")) {
            return R1_12;
        }

        if (tryVersion("1_13_R1")) {
            return R1_13;
        }

        if (tryVersion("1_13_R2")) {
            return R1_13_1;
        }

        if (tryVersion("1_14_R1")) {
            return R1_14;
        }

        if (tryVersion("1_15_R1")) {
            return R1_15;
        }

        if (tryVersion("1_16_R1")) {
            return R1_16;
        }
        return null;
    }

    private static boolean tryVersion(String v) {
        try {
            Class.forName("org.bukkit.craftbukkit.v" + v + ".CraftWorld");
            return true;
        } catch (Throwable ignored) {

        }

        return false;
    }

    public List<NMSVersion> getAboveInclusive() {
        List<NMSVersion> n = new ArrayList<>();

        for (NMSVersion i : values()) {
            if (i.ordinal() >= ordinal()) {
                n.add(i);
            }
        }

        return n;
    }

    public List<NMSVersion> betweenInclusive(NMSVersion other) {
        List<NMSVersion> n = new ArrayList<>();

        for (NMSVersion i : values()) {
            if (i.ordinal() <= Math.max(other.ordinal(), ordinal()) && i.ordinal() >= Math.min(ordinal(), other.ordinal())) {
                n.add(i);
            }
        }

        return n;
    }

    public List<NMSVersion> getBelowInclusive() {
        List<NMSVersion> n = new ArrayList<>();

        for (NMSVersion i : values()) {
            if (i.ordinal() <= ordinal()) {
                n.add(i);
            }
        }

        return n;
    }
}
