package com.volmit.adapt.util;

public interface NastyFunction<T, R> {
    R run(T t) throws Throwable;
}
