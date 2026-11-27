package art.arcane.adapt.mod.platform;

import art.arcane.adapt.platform.Capability;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModCapabilitiesTest {
    @Test
    void aClaimModReportsProtectionPlugins() {
        for (String mod : ModCapabilities.CLAIM_MODS) {
            assertTrue(ModCapabilities.has(Capability.PROTECTION_PLUGINS, Set.of(mod)::contains), mod);
        }
        assertFalse(ModCapabilities.has(Capability.PROTECTION_PLUGINS, Set.of("jei")::contains));
    }

    @Test
    void pluginCapabilitiesAreNeverReported() {
        for (Capability capability : Capability.values()) {
            if (capability != Capability.PROTECTION_PLUGINS) {
                assertFalse(ModCapabilities.has(capability, mod -> true), capability.name());
            }
        }
    }
}
