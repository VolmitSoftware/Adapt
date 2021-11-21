package com.volmit.adapt.util;

@FunctionalInterface
public interface NoiseProvider {
    double noise(double x, double z);
}