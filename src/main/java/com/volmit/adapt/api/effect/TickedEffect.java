package com.volmit.adapt.api.effect;

import com.volmit.adapt.api.tick.TickedObject;

import java.util.UUID;

public abstract class TickedEffect<T> extends TickedObject
{
    private final T t;

    public TickedEffect(T t, long interval, int ticks)
    {
        super("ticked-effect", UUID.randomUUID().toString(), interval);
        dieAfter(ticks);
        this.t = t;
    }

    public abstract void onTick(T t);

    @Override
    public void onTick() {
        onTick(t);
    }
}
