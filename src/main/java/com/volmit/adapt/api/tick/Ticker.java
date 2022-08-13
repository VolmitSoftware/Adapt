package com.volmit.adapt.api.tick;

import com.volmit.adapt.util.BurstExecutor;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.MultiBurst;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Ticker {
    private final List<Ticked> ticklist;
    private final List<Ticked> newTicks;
    private final List<String> removeTicks;
    private volatile boolean ticking;

    public Ticker() {
        this.ticklist = new ArrayList<>(4096);
        this.newTicks = new ArrayList<>(128);
        this.removeTicks = new ArrayList<>(128);
        ticking = false;
        J.ar(() -> {
            if(!ticking) {
                tick();
            }
        }, 0);
    }

    public void register(Ticked ticked) {
        synchronized(newTicks) {
            newTicks.add(ticked);
        }
    }

    public void unregister(Ticked ticked) {
        synchronized(removeTicks) {
            removeTicks.add(ticked.getId());
        }
    }

    private void tick() {
        ticking = true;
//        int ix = 0;
        AtomicInteger tc = new AtomicInteger(0);
        BurstExecutor e = MultiBurst.burst.burst(ticklist.size());
        for(int i = 0; i < ticklist.size(); i++) {
            int ii = i;
//            ix++;
            e.queue(() -> {
                Ticked t = ticklist.get(ii);

                if(t != null && t.shouldTick()) {
                    tc.incrementAndGet();
                    try {
                        t.tick();
                    } catch(Throwable exxx) {
                        exxx.printStackTrace();
                    }
                }
            });
        }

        e.complete();
//        Adapt.info(ix + "");

        synchronized(newTicks) {
            while(newTicks.isNotEmpty()) {
                tc.incrementAndGet();
                ticklist.add(newTicks.popRandom());
            }
        }

        synchronized(removeTicks) {
            while(removeTicks.isNotEmpty()) {
                tc.incrementAndGet();
                String id = removeTicks.popRandom();

                for(int i = 0; i < ticklist.size(); i++) {
                    if(ticklist.get(i).getId().equals(id)) {
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
