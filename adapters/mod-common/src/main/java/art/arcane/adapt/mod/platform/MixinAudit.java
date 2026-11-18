package art.arcane.adapt.mod.platform;

import org.spongepowered.asm.mixin.MixinEnvironment;

/**
 * Opt-in check with {@code -Dadapt.mixin.audit=true}: once the server has started, Mixin force-loads every target
 * class that no mixin has been applied to yet, so a broken injection fails at boot instead of on first use.
 */
public final class MixinAudit {
    private static final String PROPERTY = "adapt.mixin.audit";

    private MixinAudit() {
    }

    public static void runIfRequested() {
        if (!Boolean.getBoolean(PROPERTY)) {
            return;
        }
        ModPlatform.log().info("Mixin audit: force-loading every pending mixin target");
        MixinEnvironment.getCurrentEnvironment().audit();
        ModPlatform.log().info("Mixin audit finished");
    }
}
