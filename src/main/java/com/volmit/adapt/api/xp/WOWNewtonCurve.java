package com.volmit.adapt.api.xp;

public class WOWNewtonCurve implements NewtonCurve {
    @Override
    public double getXPForLevel(double level) {
        double f = 0;

        for(int i = 1; i < level; i++) {
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
        if(currentLevel >= 11 && currentLevel <= 27) {
            return (1 - (currentLevel - 10) / 100);
        }

        if(currentLevel >= 28 && currentLevel <= 59) {
            return 0.82;
        }

        return 1;
    }

    private double getDiff(double currentLevel) {
        if(currentLevel <= 28) {
            return 0;
        }
        if(currentLevel == 29) {
            return 1;
        }
        if(currentLevel == 30) {
            return 3;
        }
        if(currentLevel == 31) {
            return 6;
        }

        return 5 * (Math.min(currentLevel, 59) - 30);
    }
}
