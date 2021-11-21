package com.volmit.adapt.util;

@FunctionalInterface
public interface NoiseInjector {
    double[] combine(double src, double value);
}
