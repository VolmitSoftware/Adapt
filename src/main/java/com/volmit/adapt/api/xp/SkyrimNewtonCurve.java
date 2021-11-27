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
