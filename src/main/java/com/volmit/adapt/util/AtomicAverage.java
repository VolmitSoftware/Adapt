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

import com.google.common.util.concurrent.AtomicDoubleArray;
import lombok.Getter;

/**
 * Provides an incredibly fast averaging object. It swaps values from a sum
 * using an array. Averages do not use any form of looping. An average of 10,000
 * entries is the same speed as an average with five entries.
 *
 * @author cyberpwn
 */
public class AtomicAverage {
    protected final AtomicDoubleArray values;
    protected int cursor;
    private double lastSum;
    @Getter
    private boolean dirty;
    private boolean brandNew;

    /**
     * Create an average holder
     *
     * @param size the size of entries to keep
     */
    public AtomicAverage(int size) {
        values = new AtomicDoubleArray(size);
        DoubleArrayUtils.fill(values, 0);
        brandNew = true;
        cursor = 0;
        lastSum = 0;
        dirty = false;
    }

    /**
     * Put a value into the average (rolls over if full)
     *
     * @param i the value
     */
    public void put(double i) {

        dirty = true;

        if (brandNew) {
            DoubleArrayUtils.fill(values, i);
            lastSum = size() * i;
            brandNew = false;
            return;
        }

        double current = values.get(cursor);
        lastSum = (lastSum - current) + i;
        values.set(cursor, i);
        cursor = cursor + 1 < size() ? cursor + 1 : 0;
    }

    public int size() {
        return values.length();
    }

}
