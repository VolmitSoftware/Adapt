package com.volmit.adapt.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public interface Queue<T> {
    Queue<T> queue(T t);

    Queue<T> queue(List<T> t);

    boolean hasNext(int amt);

    boolean hasNext();

    T next();

    List<T> next(int amt);

    Queue<T> clear();

    int size();

    static <T> Queue<T> create(List<T> t) {
        return new ShurikenQueue<T>().queue(t);
    }

    @SuppressWarnings("unchecked")
    static <T> Queue<T> create(T... t) {
        return new ShurikenQueue<T>().queue(Arrays.stream(t).toList());
    }

    boolean contains(T p);
}
