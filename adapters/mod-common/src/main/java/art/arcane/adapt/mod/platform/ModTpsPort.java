package art.arcane.adapt.mod.platform;

import art.arcane.adapt.engine.fx.TpsPort;
import net.minecraft.server.MinecraftServer;

public final class ModTpsPort implements TpsPort {
    private static final double NANOS_PER_SECOND = 1_000_000_000D;

    @Override
    public double recentTps() {
        MinecraftServer server = ModPlatform.server();
        if (server == null) {
            return 20.0D;
        }
        double target = server.tickRateManager().tickrate();
        long average = server.getAverageTickTimeNanos();
        return average <= 0L ? target : Math.min(target, NANOS_PER_SECOND / average);
    }
}
