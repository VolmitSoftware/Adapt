package com.volmit.adapt.util;

@SuppressWarnings("hiding")
@FunctionalInterface
public interface Consumer2<A, B>
{
	public void accept(A a, B b);
}
