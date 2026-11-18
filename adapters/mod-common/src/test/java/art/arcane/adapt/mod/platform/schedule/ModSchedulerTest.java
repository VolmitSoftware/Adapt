package art.arcane.adapt.mod.platform.schedule;

import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.entity.EntityRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModSchedulerTest {
    private final ModScheduler scheduler = new ModScheduler(proxy(PlatformLog.class, null));

    @AfterEach
    void shutdown() {
        scheduler.shutdown();
    }

    @Test
    void ownedEntityTaskWithoutDelayRunsImmediately() {
        scheduler.bind(Thread.currentThread());
        AtomicInteger runs = new AtomicInteger();
        assertTrue(scheduler.runEntity(entity(true), runs::incrementAndGet, 0L, null));
        assertEquals(1, runs.get());
    }

    @Test
    void nestedOwnedEntityTasksFinishInTheSameTick() {
        scheduler.bind(Thread.currentThread());
        EntityRef target = entity(true);
        AtomicInteger depth = new AtomicInteger();
        scheduler.runEntity(target, () -> scheduler.runEntity(target, () -> scheduler.runEntity(target, depth::incrementAndGet, 0L, null), 0L, null), 0L, null);
        assertEquals(1, depth.get());
    }

    @Test
    void removedEntityRunsRetiredImmediately() {
        scheduler.bind(Thread.currentThread());
        AtomicInteger runs = new AtomicInteger();
        AtomicInteger retired = new AtomicInteger();
        assertTrue(scheduler.runEntity(entity(false), runs::incrementAndGet, 0L, retired::incrementAndGet));
        assertEquals(0, runs.get());
        assertEquals(1, retired.get());
    }

    @Test
    void delayedTaskWaitsForTheTick() {
        scheduler.bind(Thread.currentThread());
        AtomicInteger runs = new AtomicInteger();
        assertTrue(scheduler.runEntity(entity(true), runs::incrementAndGet, 1L, null));
        assertEquals(0, runs.get());
        scheduler.tick();
        assertEquals(1, runs.get());
    }

    @Test
    void taskFromAnotherThreadWaitsForTheTick() {
        AtomicInteger runs = new AtomicInteger();
        assertTrue(scheduler.runEntity(entity(true), runs::incrementAndGet, 0L, null));
        assertEquals(0, runs.get());
        scheduler.bind(Thread.currentThread());
        scheduler.tick();
        assertEquals(1, runs.get());
    }

    @Test
    void removedEntityIsRefusedOffThread() {
        assertFalse(scheduler.runEntity(entity(false), () -> {
        }, 0L, null));
    }

    private static EntityRef entity(boolean valid) {
        return proxy(EntityRef.class, valid);
    }

    private static <T> T proxy(Class<T> type, Boolean valid) {
        Object instance = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (Object self, Method method, Object[] args) -> {
            if (method.getName().equals("isValid")) {
                return valid;
            }
            if (method.getName().equals("hashCode")) {
                return System.identityHashCode(self);
            }
            if (method.getName().equals("equals")) {
                return self == args[0];
            }
            return null;
        });
        return type.cast(instance);
    }
}
