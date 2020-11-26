package com.volmit.adapt.api.tick;

import com.volmit.adapt.api.world.AdaptComponent;
import com.volmit.adapt.util.M;

public interface Ticked extends AdaptComponent
{
    public default void retick()
    {
        burst(1);
    }

    public default void skip()
    {
        skip(1);
    }

    public void unregister();

    public boolean isBursting();

    public boolean isSkipping();

    public void stopBursting();

    public void stopSkipping();

    public long getTickCount();

    public long getAge();

    public void burst(int ticks);

    public void skip(int ticks);

    public long getLastTick();

    public long getInterval();

    public void setInterval(long ms);

    public void tick();

    public String getGroup();

    public String getId();

    public default boolean shouldTick()
    {
        return M.ms() - getLastTick() > getInterval();
    }
}
