package com.fren_gor.ultimateAdvancementAPI;

import art.arcane.volmlib.nativelib.NativeVersion;
import com.fren_gor.ultimateAdvancementAPI.util.Versions;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class VersionsTest {
    @Test
    void advancementReleasesUseTheNativeVersionCatalog() {
        for (NativeVersion version : NativeVersion.values()) {
            assertThat(Versions.getNMSVersionsList(version.packageName()))
                    .isSameAs(version.minecraftVersions());
        }
    }

    @Test
    void unknownBackendHasNoAdvertisedVersions() {
        assertThat(Versions.getNMSVersionsList("unsupported")).isNull();
        assertThat(Versions.getNMSVersionsRange("unsupported")).isNull();
    }
}
