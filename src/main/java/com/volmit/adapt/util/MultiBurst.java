package com.volmit.adapt.util;

import com.volmit.adapt.Adapt;
import lombok.Getter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MultiBurst {
    public static MultiBurst burst = new MultiBurst(Runtime.getRuntime().availableProcessors());
    @Getter
    private final ExecutorService service;
    private int tid;

    public MultiBurst(int tc) {
        service = Executors.newFixedThreadPool(tc, r -> {
            tid++;
            Thread t = new Thread(r);
            t.setName("Adapt Workgroup " + tid);
            t.setPriority(Thread.MAX_PRIORITY);
            t.setUncaughtExceptionHandler((et, e) ->
            {
                Adapt.info("Exception encountered in " + et.getName());
                e.printStackTrace();
            });

            return t;
        });
    }

    public void burst(Runnable... r) {
        burst(r.length).queue(r).complete();
    }

    public void sync(Runnable... r) {
        for(Runnable i : r) {
            i.run();
        }
    }

    public BurstExecutor burst(int estimate) {
        return new BurstExecutor(service, estimate);
    }

    public BurstExecutor burst() {
        return burst(16);
    }

    public void lazy(Runnable o) {
        service.execute(o);
    }
}
