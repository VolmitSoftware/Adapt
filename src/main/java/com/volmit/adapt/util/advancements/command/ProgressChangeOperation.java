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

package com.volmit.adapt.util.advancements.command;

public enum ProgressChangeOperation {

    SET {
        @Override
        public int apply(int base, int amount) {
            return amount;
        }
    },

    ADD {
        @Override
        public int apply(int base, int amount) {
            return base + amount;
        }
    },

    REMOVE {
        @Override
        public int apply(int base, int amount) {
            return base - amount;
        }
    },

    MULTIPLY {
        @Override
        public int apply(int base, int amount) {
            return base * amount;
        }
    },

    DIVIDE {
        @Override
        public int apply(int base, int amount) {
            return (int) Math.floor(base * 1d / amount);
        }
    },

    POWER {
        @Override
        public int apply(int base, int amount) {
            return (int) Math.pow(base, amount);
        }
    },

    ;

    /**
     * Parses the ProgressChangeOperation from a given Input
     *
     * @param input The Input to parse from
     * @return The parsed Operation or {@link ProgressChangeOperation#SET} if parsing fails
     */
    public static ProgressChangeOperation parse(String input) {
        for (ProgressChangeOperation op : values()) {
            if (op.name().equalsIgnoreCase(input)) {
                return op;
            }
        }
        return SET;
    }

    /**
     * Applies this Operation to a given base with the specified amount
     *
     * @param base   The Base to use
     * @param amount The Amount to use
     * @return The Output
     */
    public abstract int apply(int base, int amount);

}