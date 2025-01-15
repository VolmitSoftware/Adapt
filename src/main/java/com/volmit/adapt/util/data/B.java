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

package com.volmit.adapt.util.data;


import art.arcane.chrono.ChronoLatch;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.util.collection.KList;
import com.volmit.adapt.util.collection.KMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.bukkit.Material.DIRT_PATH;

public class B {
    private static final KMap<String, BlockData> custom = new KMap<>();

    private static final Material AIR_MATERIAL = Material.AIR;
    private static final BlockData AIR = AIR_MATERIAL.createBlockData();
    private static final ChronoLatch clw = new ChronoLatch(1000);

    public static BlockData getAir() {
        return AIR;
    }

    public static Material getMaterialOrNull(String bdx) {
        try {
            return Material.valueOf(bdx.trim().toUpperCase());
        } catch (Throwable e) {
            e.printStackTrace();
            if (clw.flip()) {
                Adapt.warn("Unknown Material: " + bdx);
            }
            return null;
        }
    }

    public static Material getMaterial(String bdx) {
        Material m = getMaterialOrNull(bdx);

        if (m == null) {
            return AIR_MATERIAL;
        }

        return m;
    }

    public static BlockData getOrNull(String bdxf) {
        try {
            String bd = bdxf.trim();

            if (!custom.isEmpty() && custom.containsKey(bd)) {
                return custom.get(bd);
            }

            if (bd.startsWith("minecraft:cauldron[level=")) {
                bd = bd.replaceAll("\\Q:cauldron[\\E", ":water_cauldron[");
            }

            if (bd.equals("minecraft:grass_path")) {
                return DIRT_PATH.createBlockData();
            }

            BlockData bdx = parseBlockData(bd);

            if (bdx == null) {
                return AIR;
            }

            return bdx;
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    public static BlockData get(String bdxf) {
        BlockData bd = getOrNull(bdxf);

        if (bd != null) {
            return bd;
        }

        return AIR;
    }

    private static synchronized BlockData createBlockData(String s) {
        try {
            return Bukkit.createBlockData(s);
        } catch (IllegalArgumentException e) {
            if (s.contains("[")) {
                return createBlockData(s.split("\\Q[\\E")[0]);
            }
        }

        return null;
    }

    private static BlockData parseBlockData(String ix) {
        try {
            BlockData bx = null;

            try {
                bx = createBlockData(ix.toLowerCase());
            } catch (Throwable e) {
                e.printStackTrace();
            }

            if (bx == null) {
                try {
                    bx = createBlockData("minecraft:" + ix.toLowerCase());
                } catch (Throwable ignored) {

                }
            }

            if (bx == null) {
                try {
                    bx = Material.valueOf(ix.toUpperCase()).createBlockData();
                } catch (Throwable ignored) {

                }
            }

            if (bx == null) {
                return null;
            }

            if (bx instanceof Leaves) {
                ((Leaves) bx).setPersistent(false);
            }

            return bx;
        } catch (Throwable e) {
            String block = ix.contains(":") ? ix.split(":")[1].toLowerCase() : ix.toLowerCase();
            String state = block.contains("[") ? block.split("\\Q[\\E")[1].split("\\Q]\\E")[0] : "";
            Map<String, String> stateMap = new HashMap<>();
            if (!state.isEmpty()) {
                Arrays.stream(state.split(",")).forEach(s -> stateMap.put(s.split("=")[0], s.split("=")[1]));
            }
            block = block.split("\\Q[\\E")[0];

            switch (block) {
                case "cauldron" -> block = "water_cauldron";
                case "grass_path" -> block = "dirt_path";
                case "concrete" -> block = "white_concrete";
                case "wool" -> block = "white_wool";
                case "beetroots" -> {
                    if (stateMap.containsKey("age")) {
                        String updated = stateMap.get("age");
                        switch (updated) {
                            case "7" -> updated = "3";
                            case "3", "4", "5" -> updated = "2";
                            case "1", "2" -> updated = "1";
                        }
                        stateMap.put("age", updated);
                    }
                }
            }

            Map<String, String> newStates = new HashMap<>();
            for (String key : stateMap.keySet()) { //Iterate through every state and check if its valid
                try {
                    String newState = block + "[" + key + "=" + stateMap.get(key) + "]";
                    createBlockData(newState);
                    newStates.put(key, stateMap.get(key));

                } catch (IllegalArgumentException ignored) {
                }
            }

            //Combine all the "good" states again
            state = newStates.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining(","));
            if (!state.isEmpty()) state = "[" + state + "]";
            String newBlock = block + state;
            Adapt.debug("Converting " + ix + " to " + newBlock);

            try {
                return createBlockData(newBlock);
            } catch (Throwable e1) {
                e1.printStackTrace();
            }

            return null;
        }
    }

    public static KList<BlockData> get(KList<String> find) {
        KList<BlockData> b = new KList<>();

        for (String i : find) {
            BlockData bd = get(i);

            if (bd != null) {
                b.add(bd);
            }
        }

        return b;
    }


}
