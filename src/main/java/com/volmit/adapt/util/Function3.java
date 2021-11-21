package com.volmit.adapt.util;

@SuppressWarnings("hiding")
@FunctionalInterface
public interface Function3<A, B, C, R> {
    R apply(A a, B b, C c);
}
