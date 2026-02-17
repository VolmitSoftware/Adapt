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
import art.arcane.adapt.util.common.function.NastyFunction;
import art.arcane.adapt.util.common.function.NastyFuture;
import art.arcane.adapt.util.common.function.NastyRunnable;
import art.arcane.adapt.util.common.parallel.MultiBurst;
import art.arcane.volmlib.util.math.FinalInteger;
import art.arcane.volmlib.util.scheduling.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class J {
    private static final long TICK_MS = 50L;
    private static final AtomicInteger TASK_IDS = new AtomicInteger(1);
    private static final Map<Integer, Runnable> REPEATING_CANCELLERS = new ConcurrentHashMap<>();
    private static final StartupQueueSupport STARTUP_QUEUE = new StartupQueueSupport();

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
        JSupport.executeAfterStartupQueue(STARTUP_QUEUE, J::s, J::a);
    }

    public static void ass(Runnable r) {
        JSupport.enqueueAfterStartupSync(STARTUP_QUEUE, r, J::s);
    }

    public static void asa(Runnable r) {
        JSupport.enqueueAfterStartupAsync(STARTUP_QUEUE, r, J::a);
    }

    public static boolean isPrimaryThread() {
        return FoliaScheduler.isPrimaryThread();
    }

    public static boolean isFoliaThreading() {
        return FoliaScheduler.isFoliaThreading(Bukkit.getServer());
    }

    public static boolean isOwnedByCurrentRegion(Entity entity) {
        return FoliaScheduler.isOwnedByCurrentRegion(entity);
    }

    public static boolean runEntity(Entity entity, Runnable runnable) {
        if (entity == null || runnable == null || !isPluginActive()) {
            return false;
        }

        if (isFoliaThreading()) {
            if (isOwnedByCurrentRegion(entity)) {
                runnable.run();
                return true;
            }

            return runEntityImmediate(entity, runnable);
        }

        if (isPrimaryThread()) {
            runnable.run();
            return true;
        }

        return runEntityImmediate(entity, runnable);
    }

    public static boolean runEntity(Entity entity, Runnable runnable, int delayTicks) {
        if (entity == null || runnable == null || !isPluginActive()) {
            return false;
        }

        if (delayTicks <= 0) {
            return runEntity(entity, runnable);
        }

        if (isFoliaThreading() && runEntityDelayed(entity, runnable, delayTicks)) {
            return true;
        }

        s(() -> runEntity(entity, runnable), delayTicks);
        return true;
    }

    public static boolean teleport(Entity entity, Location location) {
        return teleport(entity, location, null);
    }

    public static boolean teleport(Entity entity, Location location, PlayerTeleportEvent.TeleportCause cause) {
        if (entity == null || location == null) {
            return false;
        }

        if (isFoliaThreading()) {
            Object asyncWithCause = null;
            if (cause != null) {
                asyncWithCause = invokeNoThrow(
                        entity,
                        "teleportAsync",
                        new Class<?>[]{Location.class, PlayerTeleportEvent.TeleportCause.class},
                        location,
                        cause
                );
            }

            if (asyncWithCause != null) {
                return true;
            }

            Object async = invokeNoThrow(entity, "teleportAsync", new Class<?>[]{Location.class}, location);
            if (async != null) {
                return true;
            }
        }

        try {
            if (cause != null) {
                return entity.teleport(location, cause);
            }

            return entity.teleport(location);
        } catch (UnsupportedOperationException e) {
            Adapt.warn("Failed to teleport entity synchronously on this server; teleportAsync was unavailable. Entity="
                    + entity.getUniqueId() + " world=" + (location.getWorld() == null ? "null" : location.getWorld().getName()));
            return false;
        }
    }

    public static boolean runAt(Location location, Runnable runnable) {
        if (location == null || runnable == null) {
            return false;
        }

        if (runRegionImmediate(location, runnable)) {
            return true;
        }

        if (isFoliaThreading()) {
            World world = location.getWorld();
            Adapt.verbose("Failed to schedule immediate region task at "
                    + (world == null ? "null" : world.getName())
                    + "@" + (location.getBlockX() >> 4) + "," + (location.getBlockZ() >> 4) + " on Folia.");
            return false;
        }

        s(runnable);
        return true;
    }

    public static boolean runAt(Location location, Runnable runnable, int delayTicks) {
        if (location == null || runnable == null) {
            return false;
        }

        if (delayTicks <= 0) {
            return runAt(location, runnable);
        }

        if (runRegionDelayed(location, runnable, delayTicks)) {
            return true;
        }

        if (isFoliaThreading()) {
            World world = location.getWorld();
            Adapt.verbose("Failed to schedule delayed region task at "
                    + (world == null ? "null" : world.getName())
                    + "@" + (location.getBlockX() >> 4) + "," + (location.getBlockZ() >> 4)
                    + " (" + delayTicks + "t) on Folia.");
            return false;
        }

        s(runnable, delayTicks);
        return true;
    }

    public static void cancelPluginTasks() {
        Plugin plugin = Adapt.instance;
        if (plugin == null) {
            return;
        }

        for (Runnable cancelAction : REPEATING_CANCELLERS.values()) {
            try {
                cancelAction.run();
            } catch (Throwable ex) {
                Adapt.verbose("Failed to run cancel action: " + ex.getClass().getSimpleName()
                        + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
            }
        }
        REPEATING_CANCELLERS.clear();

        FoliaScheduler.cancelTasks(plugin);

        try {
            Bukkit.getScheduler().cancelTasks(plugin);
        } catch (UnsupportedOperationException | IllegalPluginAccessException ex) {
            // Folia blocks BukkitScheduler usage.
            Adapt.verbose("Skipping BukkitScheduler#cancelTasks for Adapt on this server.");
        }
    }

    public static void s(Runnable r) {
        if (!isPluginActive()) {
            return;
        }

        if (!runGlobalImmediate(r)) {
            try {
                Bukkit.getScheduler().scheduleSyncDelayedTask(Adapt.instance, r);
            } catch (IllegalPluginAccessException e) {
                if (!isPluginActive()) {
                    return;
                }

                throw new IllegalStateException("Failed to schedule global sync task while plugin is enabled.", e);
            } catch (UnsupportedOperationException e) {
                throw new IllegalStateException("Failed to schedule global sync task on this server (Folia scheduler unavailable, BukkitScheduler unsupported).", e);
            }
        }
    }

    public static void s(Runnable r, int delay) {
        if (delay <= 0) {
            s(r);
            return;
        }

        if (!isPluginActive()) {
            return;
        }

        if (!runGlobalDelayed(r, delay)) {
            try {
                Bukkit.getScheduler().scheduleSyncDelayedTask(Adapt.instance, r, delay);
            } catch (IllegalPluginAccessException e) {
                if (!isPluginActive()) {
                    return;
                }

                throw new IllegalStateException("Failed to schedule delayed global sync task while plugin is enabled.", e);
            } catch (UnsupportedOperationException e) {
                throw new IllegalStateException("Failed to schedule delayed global sync task on this server (Folia scheduler unavailable, BukkitScheduler unsupported).", e);
            }
        }
    }

    public static void csr(int id) {
        cancelRepeatingTask(id);
    }

    public static int sr(Runnable r, int interval) {
        int safeInterval = Math.max(1, interval);
        RepeatingState state = new RepeatingState();
        int taskId = trackRepeatingTask(() -> state.cancelled = true);

        Runnable[] loop = new Runnable[1];
        loop[0] = () -> {
            if (state.cancelled || !isPluginActive()) {
                REPEATING_CANCELLERS.remove(taskId);
                return;
            }

            r.run();
            if (state.cancelled || !isPluginActive()) {
                REPEATING_CANCELLERS.remove(taskId);
                return;
            }

            s(loop[0], safeInterval);
        };

        s(loop[0]);
        return taskId;
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
        if (!isPluginActive()) {
            return;
        }

        if (delay <= 0) {
            if (!runAsyncImmediate(r)) {
                a(r);
            }
            return;
        }

        if (!runAsyncDelayed(r, delay)) {
            a(() -> {
                if (sleep(ticksToMilliseconds(delay))) {
                    r.run();
                }
            });
        }
    }

    public static void car(int id) {
        cancelRepeatingTask(id);
    }

    public static int ar(Runnable r, int interval) {
        int safeInterval = Math.max(1, interval);
        RepeatingState state = new RepeatingState();
        int taskId = trackRepeatingTask(() -> state.cancelled = true);

        Runnable[] loop = new Runnable[1];
        loop[0] = () -> {
            if (state.cancelled || !isPluginActive()) {
                REPEATING_CANCELLERS.remove(taskId);
                return;
            }

            r.run();
            if (state.cancelled || !isPluginActive()) {
                REPEATING_CANCELLERS.remove(taskId);
                return;
            }

            a(loop[0], safeInterval);
        };

        a(loop[0], 0);
        return taskId;
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

    private static int trackRepeatingTask(Runnable cancelAction) {
        int id = TASK_IDS.getAndIncrement();
        REPEATING_CANCELLERS.put(id, cancelAction);
        return id;
    }

    private static void cancelRepeatingTask(int id) {
        Runnable cancelAction = REPEATING_CANCELLERS.remove(id);
        if (cancelAction != null) {
            cancelAction.run();
        }
    }

    private static long ticksToMilliseconds(int ticks) {
        return Math.max(0L, ticks) * TICK_MS;
    }

    private static boolean runGlobalImmediate(Runnable runnable) {
        return FoliaScheduler.runGlobal(Adapt.instance, runnable);
    }

    private static boolean runGlobalDelayed(Runnable runnable, int delayTicks) {
        return FoliaScheduler.runGlobal(Adapt.instance, runnable, Math.max(0, delayTicks));
    }

    private static boolean runRegionImmediate(Location location, Runnable runnable) {
        return FoliaScheduler.runRegion(Adapt.instance, location, runnable);
    }

    private static boolean runRegionDelayed(Location location, Runnable runnable, int delayTicks) {
        return FoliaScheduler.runRegion(Adapt.instance, location, runnable, Math.max(0, delayTicks));
    }

    private static boolean runAsyncImmediate(Runnable runnable) {
        return FoliaScheduler.runAsync(Adapt.instance, runnable);
    }

    private static boolean runAsyncDelayed(Runnable runnable, int delayTicks) {
        return FoliaScheduler.runAsync(Adapt.instance, runnable, Math.max(0, delayTicks));
    }

    private static boolean runEntityImmediate(Entity entity, Runnable runnable) {
        return FoliaScheduler.runEntity(Adapt.instance, entity, runnable);
    }

    private static boolean runEntityDelayed(Entity entity, Runnable runnable, int delayTicks) {
        return FoliaScheduler.runEntity(Adapt.instance, entity, runnable, Math.max(0, delayTicks));
    }

    private static Object invokeNoThrow(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (Throwable ex) {
            Adapt.verbose("Reflective call failed for method '" + methodName + "' on " + target.getClass().getName()
                    + ": " + ex.getClass().getSimpleName()
                    + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
            return null;
        }
    }

    private static final class RepeatingState {
        private volatile boolean cancelled;
    }

    private static boolean isPluginActive() {
        Adapt adapt = Adapt.instance;
        return adapt != null && adapt.isEnabled();
    }
}
