package com.volmit.adapt.util;

public interface NastyFuture<R>
{
	public R run() throws Throwable;
}
