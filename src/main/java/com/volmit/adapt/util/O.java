package com.volmit.adapt.util;

import java.util.ArrayList;
import java.util.List;

public class O<T> implements Observable<T> {
    private T t = null;
    private List<Observer<T>> observers;

    @Override
    public T get() {
        return t;
    }

    @Override
    public O<T> set(T t) {
        T x = t;
        this.t = t;

        if(observers != null && observers.isNotEmpty()) {
            observers.forEach((o) -> o.onChanged(x, t));
        }

        return this;
    }

    @Override
    public boolean has() {
        return t != null;
    }

    @Override
    public O<T> clearObservers() {
        observers.clear();
        return this;
    }

    @Override
    public O<T> observe(Observer<T> t) {
        if(observers == null) {
            observers = new ArrayList<>();
        }

        observers.add(t);

        return this;
    }
}
