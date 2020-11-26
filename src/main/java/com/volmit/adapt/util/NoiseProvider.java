package com.volmit.adapt.util;
@FunctionalInterface
public interface NoiseProvider
{
	public double noise(double x, double z);
}