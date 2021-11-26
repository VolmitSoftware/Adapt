package com.volmit.adapt.api.xp;

public interface ResolvedNewtonCurve extends NewtonCurve{
    double getLevelForXP(double xp);

    default double computeLevelForXP(double xp, double maxError)
    {
        return getLevelForXP(xp);
    }
}
