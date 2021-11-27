package com.volmit.adapt.api.xp;

@FunctionalInterface
public interface NewtonCurve {
    double getXPForLevel(double level);

    default double computeLevelForXP(double xp, double maxError) {
        double div = 2;
        int iterations = 0;
        double jumpSize = 100;
        double cursor = 0;
        double test;
        boolean last = false;

        while(jumpSize > maxError && iterations < 100) {
            iterations++;
            test = getXPForLevel(cursor);
            if(test < xp) {
                if(last) {
                    jumpSize /= div;
                }

                last = false;
                cursor += jumpSize;
            } else {
                if(!last) {
                    jumpSize /= div;
                }

                last = true;
                cursor -= jumpSize;
            }
        }

        return cursor;
    }
}
