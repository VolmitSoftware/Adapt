/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.util.common.scheduling;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.util.common.parallel.MultiBurst;
import art.arcane.volmlib.util.function.NastyFunction;
import art.arcane.volmlib.util.function.NastyFuture;
import art.arcane.volmlib.util.function.NastyRunnable;
import art.arcane.volmlib.util.math.FinalInteger;
import art.arcane.volmlib.util.scheduling.*;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class J {
    private static final SchedulerRuntime RUNTIME = new SchedulerRuntime(
            () -> Adapt.instance,
            J::a,
            Adapt::verbose,
            Adapt::warn,
            Throwable::printStackTrace
    );

    static {
        SchedulerBridge.setSyncScheduler(J::s);
        SchedulerBridge.setDelayedSyncScheduler(J::s);
        SchedulerBridge.setAsyncScheduler(J::a);
        SchedulerBridge.setDelayedAsyncScheduler(J::a);
        SchedulerBridge.setSyncRepeatingScheduler(J::sr);
        SchedulerBridge.setAsyncRepeatingScheduler(J::ar);
        SchedulerBridge.setCancelScheduler(J::car);
        SchedulerBridge.setErrorHandler(Throwable::printStackTrace);
        SchedulerBridge.setInfoLogger(Adapt::info);
    }

    public static void dofor(int a, Function<Integer, Boolean> c, int ch, Consumer<Integer> d) {
        JSupport.dofor(a, c, ch, d);
    }

    public static boolean doif(Supplier<Boolean> c, Runnable g) {
        return JSupport.doif(c, g, null);
    }

    public static void a(Runnable a) {
        MultiBurst.burst.lazy(a);
    }

    public static <T> Future<T> a(Callable<T> a) {
        return MultiBurst.burst.getService().submit(a);
    }

    public static void attemptAsync(NastyRunnable r) {
        JSupport.attemptAsync(r::run, J::a);
    }

    public static <R> R attemptResult(NastyFuture<R> r, R onError) {
        return JSupport.attemptResult(r::run, onError, Throwable::printStackTrace);
    }

    public static <T, R> R attemptFunction(NastyFunction<T, R> r, T param, R onError) {
        return JSupport.attemptFunction(r::run, param, onError, e -> Adapt.verbose("Failed to run function: " + e.getMessage()));
    }

    public static boolean sleep(long ms) {
        return JSupport.sleep(ms);
    }

    public static boolean attempt(NastyRunnable r) {
        return JSupport.attempt(r::run);
    }

    public static Throwable attemptCatch(NastyRunnable r) {
        return JSupport.attemptCatch(r::run);
    }

    public static <T> T attempt(Supplier<T> t, T i) {
        return JSupport.attempt(t::get, i, null);
    }

    /**
     * Dont call this unless you know what you are doing!
     */
    public static void executeAfterStartupQueue() {
        RUNTIME.executeAfterStartupQueue(J::s);
    }

    public static void ass(Runnable r) {
        RUNTIME.enqueueAfterStartupSync(r, J::s);
    }

    public static void asa(Runnable r) {
        RUNTIME.enqueueAfterStartupAsync(r);
    }

    public static boolean isPrimaryThread() {
        return FoliaScheduler.isPrimaryThread();
    }

    public static boolean isFoliaThreading() {
        return RUNTIME.isFoliaThreading();
    }

    public static boolean isOwnedByCurrentRegion(Entity entity) {
        return RUNTIME.isOwnedByCurrentRegion(entity);
    }

    public static boolean runEntity(Entity entity, Runnable runnable) {
        return RUNTIME.runEntity(entity, runnable);
    }

    public static boolean runEntity(Entity entity, Runnable runnable, int delayTicks) {
        return RUNTIME.runEntity(entity, runnable, delayTicks);
    }

    public static boolean teleport(Entity entity, Location location) {
        return teleport(entity, location, null);
    }

    public static boolean teleport(Entity entity, Location location, PlayerTeleportEvent.TeleportCause cause) {
        return RUNTIME.teleport(entity, location, cause);
    }

    public static boolean runAt(Location location, Runnable runnable) {
        return RUNTIME.runAt(location, runnable);
    }

    public static boolean runAt(Location location, Runnable runnable, int delayTicks) {
        return RUNTIME.runAt(location, runnable, delayTicks);
    }

    public static void cancelPluginTasks() {
        RUNTIME.cancelPluginTasks();
    }

    public static void s(Runnable r) {
        RUNTIME.s(r);
    }

    public static void s(Runnable r, int delay) {
        RUNTIME.s(r, delay);
    }

    public static void csr(int id) {
        RUNTIME.csr(id);
    }

    public static int sr(Runnable r, int interval) {
        return RUNTIME.sr(r, interval);
    }

    public static void sr(Runnable r, int interval, int intervals) {
        FinalInteger fi = new FinalInteger(0);

        new SR(interval) {
            @Override
            public void run() {
                fi.add(1);
                r.run();

                if (fi.get() >= intervals) {
                    cancel();
                }
            }
        };
    }

    public static void a(Runnable r, int delay) {
        RUNTIME.a(r, delay);
    }

    public static void car(int id) {
        RUNTIME.car(id);
    }

    public static int ar(Runnable r, int interval) {
        return RUNTIME.ar(r, interval);
    }

    public static void ar(Runnable r, int interval, int intervals) {
        FinalInteger fi = new FinalInteger(0);

        new AR(interval) {
            @Override
            public void run() {
                fi.add(1);
                r.run();

                if (fi.get() >= intervals) {
                    cancel();
                }
            }
        };
    }

}
