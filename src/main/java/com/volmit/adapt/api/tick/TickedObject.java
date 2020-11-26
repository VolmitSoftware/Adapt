package com.volmit.adapt.api.tick;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.util.M;
import org.bukkit.event.Listener;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public abstract class TickedObject implements Ticked, Listener {
    private final AtomicLong lastTick;
    private final AtomicLong interval;
    private final AtomicInteger skip;
    private final AtomicInteger burst;
    private final AtomicLong ticks;
    private final AtomicInteger dieIn;
    private final AtomicBoolean die;
    private final long start;
    private final String group;
    private final String id;

    public TickedObject()
    {
        this("null");
    }

    public TickedObject(String group, String id)
    {
        this(group, id, 1000);
    }

    public TickedObject(String group)
    {
        this(group, UUID.randomUUID().toString(), 1000);
    }

    public TickedObject(String group, long interval)
    {
        this(group, UUID.randomUUID().toString(), interval);
    }

    public TickedObject(String group, String id, long interval)
    {
        this.group = group;
        this.id = id;
        this.die = new AtomicBoolean(false);
        this.dieIn = new AtomicInteger(0);
        this.interval = new AtomicLong(interval);
        this.lastTick = new AtomicLong(M.ms());
        this.burst = new AtomicInteger(0);
        this.skip = new AtomicInteger(0);
        this.ticks = new AtomicLong(0);
        this.start = M.ms();
        Adapt.instance.getTicker().register(this);
        Adapt.instance.registerListener(this);
    }

    public void dieAfter(int ticks)
    {
        dieIn.set(ticks);
        die.set(true);
    }

    @Override
    public void unregister()
    {
        Adapt.instance.getTicker().unregister(this);
        Adapt.instance.unregisterListener(this);
    }

    @Override
    public long getLastTick() {
        return lastTick.get();
    }

    @Override
    public long getInterval() {
        if(burst.get() > 0)
        {
            return 0;
        }

        return interval.get();
    }

    @Override
    public void setInterval(long ms) {
        interval.set(ms);
    }

    @Override
    public void tick() {
        if(skip.getAndDecrement() > 0)
        {
            return;
        }

        if(die.get() && dieIn.decrementAndGet() <= 0)
        {
            unregister();
            return;
        }

        lastTick.set(M.ms());
        burst.decrementAndGet();
        onTick();
    }

    public abstract void onTick();

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public long getTickCount() {
        return ticks.get();
    }

    @Override
    public long getAge() {
        return M.ms() - start;
    }

    @Override
    public boolean isBursting()
    {
        return burst.get() > 0;
    }

    @Override
    public void burst(int ticks) {
        if(burst.get() < 0)
        {
            burst.set(ticks);
            return;
        }

        burst.addAndGet(ticks);
    }

    @Override
    public boolean isSkipping() {
        return skip.get() > 0;
    }

    @Override
    public void stopBursting() {
        burst.set(0);
    }

    @Override
    public void stopSkipping() {
        skip.set(0);
    }

    @Override
    public void skip(int ticks) {
        if(skip.get() < 0)
        {
            skip.set(ticks);
            return;
        }

        skip.addAndGet(ticks);
    }
}
