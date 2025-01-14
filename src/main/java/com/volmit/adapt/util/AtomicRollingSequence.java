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

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;

public class AtomicRollingSequence extends AtomicAverage {
    private double median;
    private double max;
    private double min;
    private boolean dirtyMedian;
    private int dirtyExtremes;
    @Setter
    @Getter
    private boolean precision;

    public AtomicRollingSequence(int size) {
        super(size);
        median = 0;
        min = 0;
        max = 0;
        setPrecision(false);
    }

    public double addLast(int amt) {
        double f = 0;

        for (int i = 0; i < Math.min(values.length(), amt); i++) {
            f += values.get(i);
        }

        return f;
    }

    public double getMin() {
        if (dirtyExtremes > (isPrecision() ? 0 : values.length())) {
            resetExtremes();
        }

        return min;
    }

    public double getMax() {
        if (dirtyExtremes > (isPrecision() ? 0 : values.length())) {
            resetExtremes();
        }

        return max;
    }

    public double getMedian() {
        if (dirtyMedian) {
            recalculateMedian();
        }

        return median;
    }

    private void recalculateMedian() {
        double[] a = new double[values.length()];
        for (int i = 0; i < a.length; i++) {
            a[i] = values.get(i);
        }
        median = new ArrayList<Double>().forceAdd(a).sort().middleValue();
        dirtyMedian = false;
    }

    public void resetExtremes() {
        max = Integer.MIN_VALUE;
        min = Integer.MAX_VALUE;

        for (int i = 0; i < values.length(); i++) {
            double v = values.get(i);
            max = M.max(max, v);
            min = M.min(min, v);
        }

        dirtyExtremes = 0;
    }

    public void put(double i) {
        super.put(i);
        dirtyMedian = true;
        dirtyExtremes++;
        max = M.max(max, i);
        min = M.min(min, i);
    }
}
