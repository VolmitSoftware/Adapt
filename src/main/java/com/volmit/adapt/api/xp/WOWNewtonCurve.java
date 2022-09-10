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

package com.volmit.adapt.api.xp;

public class WOWNewtonCurve implements NewtonCurve {
    @Override
    public double getXPForLevel(double level) {
        double f = 0;

        for (int i = 1; i < level; i++) {
            f += getNextLevelCost(i);
        }

        return f;
    }

    private double getNextLevelCost(double currentLevel) {
        return ((8 * currentLevel) + getDiff(currentLevel)) * getMXP(currentLevel) * getDRF(currentLevel);
    }

    private double getMXP(double currentLevel) {
        return 235 + (5 * currentLevel);
    }

    private double getDRF(double currentLevel) {
        if (currentLevel >= 11 && currentLevel <= 27) {
            return (1 - (currentLevel - 10) / 100);
        }

        if (currentLevel >= 28 && currentLevel <= 59) {
            return 0.82;
        }

        return 1;
    }

    private double getDiff(double currentLevel) {
        if (currentLevel <= 28) {
            return 0;
        }
        if (currentLevel == 29) {
            return 1;
        }
        if (currentLevel == 30) {
            return 3;
        }
        if (currentLevel == 31) {
            return 6;
        }

        return 5 * (Math.min(currentLevel, 59) - 30);
    }
}
