package art.arcane.adapt.mod.platform;

import art.arcane.adapt.api.fx.TpsPort;
import net.minecraft.server.MinecraftServer;

public final class ModTpsPort implements TpsPort {
    private static final double TARGET_TPS = 20.0D;
    private static final double MILLIS_PER_SECOND = 1_000.0D;

    @Override
    public double recentTps() {
        MinecraftServer server = ModPlatform.server();
        if (server == null) {
            return TARGET_TPS;
        }
        double average = server.getAverageTickTime();
        return average <= 0.0D ? TARGET_TPS : Math.min(TARGET_TPS, MILLIS_PER_SECOND / average);
    }
}
