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
//    X1D2(resolved(level -> Math.pow(level, 1.2), xp -> Math.pow(xp, 1D / 1.2D))), // Inverse might not be accurate below
//    X1D5(resolved(level -> Math.pow(level, 1.5), xp -> Math.pow(xp, 1D / 1.5D))),// NOT WORKING
//    X2(resolved(level -> Math.pow(level, 2), xp -> Math.pow(xp, 1D / 2D))),// NOT WORKING
//    X3(resolved(level -> Math.pow(level, 3), xp -> Math.pow(xp, 1D / 3D))),// NOT WORKING
//    X4(resolved(level -> Math.pow(level, 4), xp -> Math.pow(xp, 1D / 4D))),// NOT WORKING
//    X5(resolved(level -> Math.pow(level, 5), xp -> Math.pow(xp, 1D / 5D))),// NOT WORKING
//    X6(resolved(level -> Math.pow(level, 6), xp -> Math.pow(xp, 1D / 6D))),// NOT WORKING
//    X7(resolved(level -> Math.pow(level, 7), xp -> Math.pow(xp, 1D / 7D))),// NOT WORKING
//    L1K(resolved(level -> level * 1000D, xp -> xp / 1000D)),// NOT WORKING
//    L4K(resolved(level -> level * 4000D, xp -> xp / 4000D)),// NOT WORKING
//    L8K(resolved(level -> level * 8000D, xp -> xp / 8000D)),// NOT WORKING
//    L16K(resolved(level -> level * 16000D, xp -> xp / 16000D)),// NOT WORKING
//    SKYRIM(new SkyrimNewtonCurve()),// NOT WORKING
//    WOW(new WOWNewtonCurve()), // NOT WORKING

    //    XL05L7(level -> ((537 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137), // Working
    XL1L7(level -> ((1337 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137), // Working
    XL15L7(level -> ((1837 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137), // Working
    XL2L7(level -> ((2337 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137), // Working
    XL3L7(level -> ((3337 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137), // Working
    XL4L7(level -> ((4337 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137), // Working
    XL5L7(level -> ((5337 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137), // Working
    XL6L7(level -> ((6337 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137), // Working
    XL7L7(level -> ((7337 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137), // Working
    XL8L7(level -> ((8337 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137), // Working
    XL9L7(level -> ((8337 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137); // Working

    @Getter
    private final NewtonCurve curve;

    Curves(NewtonCurve curve) {
        this.curve = curve;
    }

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
}
