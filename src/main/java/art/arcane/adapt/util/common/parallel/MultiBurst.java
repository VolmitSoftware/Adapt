package art.arcane.adapt.util.common.parallel;

import art.arcane.adapt.Adapt;
import java.util.concurrent.ExecutorService;
import java.util.function.IntSupplier;

public class MultiBurst extends art.arcane.volmlib.util.parallel.MultiBurstSupport {
    private static final long TIMEOUT = Long.getLong("adapt.burst.timeout", 15000);
    public static MultiBurst burst = new MultiBurst(Runtime.getRuntime().availableProcessors());

    public MultiBurst(int tc) {
        this(() -> tc);
    }

    public MultiBurst(IntSupplier parallelism) {
        super("Adapt Workgroup", Thread.MAX_PRIORITY, parallelism, i -> Math.max(i, 1), System::currentTimeMillis,
                e -> {
                    Adapt.info("Exception encountered in burst thread");
                    e.printStackTrace();
                },
                Adapt::info,
                Adapt::warn,
                TIMEOUT);
    }

    public ExecutorService getService() {
        return service();
    }

    @Override
    public BurstExecutor burst(int estimate) {
        return new BurstExecutor(service(), estimate);
    }

    @Override
    public BurstExecutor burst() {
        return burst(16);
    }
}
