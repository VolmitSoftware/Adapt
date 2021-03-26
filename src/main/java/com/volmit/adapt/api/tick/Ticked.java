package com.volmit.adapt.api.tick;

import com.volmit.adapt.api.world.AdaptComponent;
import com.volmit.adapt.util.M;

public interface Ticked extends AdaptComponent
{
    default void retick()
    {
        burst(1);
    }

    default void skip()
    {
        skip(1);
    }

    void unregister();

    boolean isBursting();

    boolean isSkipping();

    void stopBursting();

    void stopSkipping();

    long getTickCount();

    long getAge();

    void burst(int ticks);

    void skip(int ticks);

    long getLastTick();

    long getInterval();

    void setInterval(long ms);

    void tick();

    String getGroup();

    String getId();

    default boolean shouldTick()
    {
        return M.ms() - getLastTick() > getInterval();
    }
}
