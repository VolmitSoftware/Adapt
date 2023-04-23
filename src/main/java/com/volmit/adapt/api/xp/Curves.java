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

import java.util.function.Function;

public enum Curves {
    // Strange ones
    QLOG(resolved(level -> Math.pow(level, 2) * Math.log(level), xp -> Math.sqrt(xp / Math.log(xp)), 0.001)),
    ELIN(resolved(level -> 1000 * Math.exp(0.001 * level), xp -> Math.log(xp / 1000) / 0.001, 0.001)),
    CUBRT(resolved(level -> Math.pow(level, 1 / 3.0), xp -> Math.pow(xp, 3), 0.001)),
    HYPER(resolved(level -> 1000 / (2 - level), xp -> 2 - (1000 / xp), 0.001)),
    SIGM(resolved(level -> 1000 / (1 + Math.exp(-0.01 * (level - 50))), xp -> 50 + Math.log(xp / (1000 - xp)) / -0.01, 0.001)),

    // Normal ones
    X1D2(resolved(level -> Math.pow(level, 1.2), xp -> Math.pow(xp, 1D / 1.2D), 0.001)),
    X1D5(resolved(level -> Math.pow(level, 1.5), xp -> Math.pow(xp, 1D / 1.5D), 0.001)),
    X2(resolved(level -> Math.pow(level, 2), xp -> Math.pow(xp, 1D / 2D), 0.001)),
    X3(resolved(level -> Math.pow(level, 3), xp -> Math.pow(xp, 1D / 3D), 0.001)),
    X4(resolved(level -> Math.pow(level, 4), xp -> Math.pow(xp, 1D / 4D), 0.001)),
    X5(resolved(level -> Math.pow(level, 5), xp -> Math.pow(xp, 1D / 5D), 0.001)),
    X6(resolved(level -> Math.pow(level, 6), xp -> Math.pow(xp, 1D / 6D), 0.001)),
    X7(resolved(level -> Math.pow(level, 7), xp -> Math.pow(xp, 1D / 7D), 0.001)),
    L1K(resolved(level -> level * 1000D, xp -> xp / 1000D, 0.001)),
    L4K(resolved(level -> level * 4000D, xp -> xp / 4000D, 0.001)),
    L8K(resolved(level -> level * 8000D, xp -> xp / 8000D, 0.001)),
    L16K(resolved(level -> level * 16000D, xp -> xp / 16000D, 0.001)),

    // Game ones
    SKYRIM(SkyrimNewtonCurve.create()),
    WOW(WOWNewtonCurve.create()),

    // Adapt ones
    XL05L7(level -> ((537 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137),
    XL1L7(level -> ((1337 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137),
    XL15L7(level -> ((1837 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137),
    XL2L7(level -> ((2337 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137),
    XL3L7(level -> ((3337 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137),
    XL4L7(level -> ((4337 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137),
    XL5L7(level -> ((5337 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137),
    XL6L7(level -> ((6337 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137),
    XL7L7(level -> ((7337 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137),
    XL8L7(level -> ((8337 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137),
    XL9L7(level -> ((9337 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137),
    XL20L7(level -> ((20337 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137),
    XL40L7(level -> ((40337 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137),
    XL80L7(level -> ((80337 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137),
    XL160L7(level -> ((160337 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137),
    XL100L7(level -> ((1000337 * level) + Math.pow(level * 0.95, Math.PI)) / 1.137),
    LINEAR_EXPONENTIAL_1(resolved(level -> 1000 * level + 100 * Math.pow(level, 2), xp -> {
        double a = 1000;
        double b = 100;
        return (-a + Math.sqrt(a * a + 4 * b * xp)) / (2 * b);
    }, 0.001)),

    LINEAR_EXPONENTIAL_2(resolved(level -> 2000 * level + 50 * Math.pow(level, 2.5), xp -> {
        double a = 2000;
        double b = 50;
        double lvl = (-a + Math.sqrt(a * a + 4 * b * xp)) / (2 * b);
        return Math.pow((xp - a * lvl) / b, 1 / 2.5);
    }, 0.001)),

    LINEAR_EXPONENTIAL_3(resolved(level -> 500 * level + 200 * Math.pow(level, 1.5), xp -> {
        double a = 500;
        double b = 200;
        double lvl = (-a + Math.sqrt(a * a + 4 * b * xp)) / (2 * b);
        return Math.pow((xp - a * lvl) / b, 1 / 1.5);
    }, 0.001));


    @Getter
    private final NewtonCurve curve;

    Curves(NewtonCurve curve) {
        this.curve = curve;
    }

    private static NewtonCurve resolved(Function<Double, Double> xpForLevel, Function<Double, Double> levelForXP, double maxError) {
        return new NewtonCurve() {
            @Override
            public double getXPForLevel(double level) {
                return xpForLevel.apply(level);
            }

            @Override
            public double computeLevelForXP(double xp, double maxError) {
                return levelForXP.apply(xp);
            }
        };
    }

    public static class SkyrimNewtonCurve {
        public static NewtonCurve create() {
            Function<Double, Double> xpForLevel = level -> {
                double f = 0;
                for (int i = 1; i < level; i++) {
                    f += getNextLevelCost(i);
                }
                return f;
            };

            Function<Double, Double> levelForXP = xp -> {
                double currentLevel = 1;
                while (xp >= getNextLevelCost(currentLevel)) {
                    xp -= getNextLevelCost(currentLevel);
                    currentLevel++;
                }
                return currentLevel;
            };

            return Curves.resolved(xpForLevel, levelForXP, 0.001);
        }

        private static double getNextLevelCost(double currentLevel) {
            return Math.pow(currentLevel - 1, 1.95) + 300;
        }
    }


    public class WOWNewtonCurve {
        public static NewtonCurve create() {
            Function<Double, Double> xpForLevel = level -> {
                double f = 0;
                for (int i = 1; i < level; i++) {
                    f += getNextLevelCost(i);
                }
                return f;
            };

            Function<Double, Double> levelForXP = xp -> {
                double currentLevel = 1;
                while (xp >= getNextLevelCost(currentLevel)) {
                    xp -= getNextLevelCost(currentLevel);
                    currentLevel++;
                }
                return currentLevel;
            };

            return Curves.resolved(xpForLevel, levelForXP, 0.001);
        }

        private static double getNextLevelCost(double currentLevel) {
            return ((8 * currentLevel) + getDiff(currentLevel)) * getMXP(currentLevel) * getDRF(currentLevel);
        }

        private static double getMXP(double currentLevel) {
            return 235 + (5 * currentLevel);
        }

        private static double getDRF(double currentLevel) {
            if (currentLevel >= 11 && currentLevel <= 27) {
                return (1 - (currentLevel - 10) / 100);
            }

            if (currentLevel >= 28 && currentLevel <= 59) {
                return 0.82;
            }

            return 1;
        }

        private static double getDiff(double currentLevel) {
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

}
