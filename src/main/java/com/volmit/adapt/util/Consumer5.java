package com.volmit.adapt.util;

@SuppressWarnings("hiding")
@FunctionalInterface
public interface Consumer5<A, B, C, D, E> {
    void accept(A a, B b, C c, D d, E e);
}
