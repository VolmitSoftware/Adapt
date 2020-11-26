package com.volmit.adapt.util;

public interface IRare
{
	public int getRarity();

	public static int get(Object v)
	{
		return v instanceof IRare ? ((IRare) v).getRarity() : 1;
	}
}
