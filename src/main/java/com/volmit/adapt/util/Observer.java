package com.volmit.adapt.util;

@FunctionalInterface
public interface Observer<T>
{
	public void onChanged(T from, T to);
}
