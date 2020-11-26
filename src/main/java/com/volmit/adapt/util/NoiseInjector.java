package com.volmit.adapt.util;

@FunctionalInterface
public interface NoiseInjector
{
	public double[] combine(double src, double value);
}
