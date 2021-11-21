package com.volmit.adapt.util;

public interface NastyFuture<R> {
    R run() throws Throwable;
}
