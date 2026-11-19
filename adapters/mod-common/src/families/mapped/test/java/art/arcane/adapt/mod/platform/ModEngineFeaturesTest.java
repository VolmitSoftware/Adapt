package art.arcane.adapt.mod.platform;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import org.junit.jupiter.api.Test;

class ModEngineFeaturesTest {
    @Test
    void bundlesAreUnsupportedInAVanillaWorld() {
        assertFalse(ModEngineFeatures.bundlesEnabled(FeatureFlags.VANILLA_SET));
    }

    @Test
    void bundlesAreSupportedWithTheBundleExperiment() {
        FeatureFlagSet enabled = FeatureFlagSet.of(FeatureFlags.VANILLA, FeatureFlags.BUNDLE);
        assertTrue(ModEngineFeatures.bundlesEnabled(enabled));
    }
}
