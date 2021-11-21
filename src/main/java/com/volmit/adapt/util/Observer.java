package com.volmit.adapt.util;

@FunctionalInterface
public interface Observer<T> {
    void onChanged(T from, T to);
}
