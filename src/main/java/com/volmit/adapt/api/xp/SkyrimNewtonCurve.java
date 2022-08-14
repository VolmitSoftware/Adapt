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

public class SkyrimNewtonCurve implements NewtonCurve {
    @Override
    public double getXPForLevel(double level) {
        double f = 0;

        for(int i = 1; i < level; i++) {
            f += getNextLevelCost(i);
        }

        return f;
    }

    private double getNextLevelCost(double currentLevel) {
        return Math.pow(currentLevel - 1, 1.95) + 300;
    }
}
