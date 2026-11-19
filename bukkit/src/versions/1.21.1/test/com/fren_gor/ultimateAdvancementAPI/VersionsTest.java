package com.fren_gor.ultimateAdvancementAPI;

import com.fren_gor.ultimateAdvancementAPI.util.Versions;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** The legacy jars keep the advancement library's own version catalogue instead of the native one. */
class VersionsTest {
    @Test
    void bundledCatalogueKnowsTheLegacyServerVersions() {
        assertThat(Versions.getNMSVersionsList("v1_21_R1")).contains("1.21.1");
        assertThat(Versions.getNMSVersionsList("v1_20_R1")).contains("1.20.1");
    }

    @Test
    void unknownBackendHasNoAdvertisedVersions() {
        assertThat(Versions.getNMSVersionsList("unsupported")).isNull();
        assertThat(Versions.getNMSVersionsRange("unsupported")).isNull();
    }
}
