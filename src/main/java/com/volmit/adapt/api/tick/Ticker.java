package com.volmit.adapt.api.tick;

import com.volmit.adapt.util.BurstExecutor;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.MultiBurst;
import java.util.concurrent.atomic.AtomicInteger;

public class Ticker
{
    private final KList<Ticked> ticklist;
    private final KList<Ticked> newTicks;
    private final KList<String> removeTicks;
    private volatile boolean ticking;

    public Ticker()
    {
        this.ticklist = new KList<>(4096);
        this.newTicks = new KList<>(128);
        this.removeTicks = new KList<>(128);
        ticking = false;
        J.ar(() -> {
            if(!ticking)
            {
                tick();
            }
        }, 0);
    }

    public void register(Ticked ticked)
    {
        synchronized (newTicks)
        {
            newTicks.add(ticked);
        }
    }
    public void unregister(Ticked ticked)
    {
        synchronized (removeTicks)
        {
            removeTicks.add(ticked.getId());
        }
    }

    private void tick()
    {
        ticking = true;
        AtomicInteger tc = new AtomicInteger(0);
        BurstExecutor e = MultiBurst.burst.burst(ticklist.size());
        for(int i = 0; i < ticklist.size(); i++)
        {
            int ii = i;
            e.queue(() -> {
                Ticked t = ticklist.get(ii);

                if(t.shouldTick())
                {
                    tc.incrementAndGet();
                    try
                    {
                        t.tick();
                    }

                    catch(Throwable exxx)
                    {
                        exxx.printStackTrace();
                    }
                }
            });
        }

        e.complete();

       synchronized (newTicks)
       {
           while(newTicks.hasElements())
           {
               tc.incrementAndGet();
               ticklist.add(newTicks.popRandom());
           }
       }

       synchronized (removeTicks)
       {
           while(removeTicks.hasElements())
           {
               tc.incrementAndGet();
               String id = removeTicks.popRandom();

               for(int i = 0; i < ticklist.size(); i++)
               {
                   if(ticklist.get(i).getId().equals(id))
                   {
                       ticklist.remove(i);
                       break;
                   }
               }
           }
       }

        ticking = false;

        tc.get();
    }
}
