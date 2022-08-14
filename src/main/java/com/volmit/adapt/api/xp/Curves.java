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

import lombok.Getter;

public enum Curves {
    X1D2(resolved(level -> Math.pow(level, 1.2), xp -> Math.pow(xp, 1D / 1.2D))), // Inverse might not be accurate below
    X1D5(resolved(level -> Math.pow(level, 1.5), xp -> Math.pow(xp, 1D / 1.5D))),
    X2(resolved(level -> Math.pow(level, 2), xp -> Math.pow(xp, 1D / 2D))),
    X3(resolved(level -> Math.pow(level, 3), xp -> Math.pow(xp, 1D / 3D))),
    X4(resolved(level -> Math.pow(level, 4), xp -> Math.pow(xp, 1D / 4D))),
    X5(resolved(level -> Math.pow(level, 5), xp -> Math.pow(xp, 1D / 5D))),
    X6(resolved(level -> Math.pow(level, 6), xp -> Math.pow(xp, 1D / 6D))),
    X7(resolved(level -> Math.pow(level, 7), xp -> Math.pow(xp, 1D / 7D))),
    L1K(resolved(level -> level * 1000D, xp -> xp / 1000D)),
    L4K(resolved(level -> level * 4000D, xp -> xp / 4000D)),
    L8K(resolved(level -> level * 8000D, xp -> xp / 8000D)),
    L16K(resolved(level -> level * 16000D, xp -> xp / 16000D)),
    SKYRIM(new SkyrimNewtonCurve()),
    WOW(new WOWNewtonCurve()),
    XL3L7(level -> ((1337 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137);

    @Getter
    private final NewtonCurve curve;

    private static NewtonCurve resolved(NewtonCurve c, NewtonCurve inverse) {
        return new ResolvedNewtonCurve() {
            @Override
            public double getLevelForXP(double xp) {
                return inverse.getXPForLevel(xp);
            }

            @Override
            public double getXPForLevel(double level) {
                return c.getXPForLevel(level);
            }
        };
    }

    Curves(NewtonCurve curve) {
        this.curve = curve;
    }
}
