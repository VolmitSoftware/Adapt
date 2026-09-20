package com.fren_gor.ultimateAdvancementAPI;

import com.fren_gor.ultimateAdvancementAPI.util.Versions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class VersionsTest {
    @ParameterizedTest
    @ValueSource(strings = {"26.1", "26.1.2", "26.2", "26.2.1", "26.3", "26.3.1", "26.3-pre1"})
    void supportedReleasesResolveTheAdvancementImplementation(String version) {
        assertThat(Versions.normalizeMinecraftVersion(version)).isEqualTo("1.21.11");
    }

    @ParameterizedTest
    @ValueSource(strings = {"26.30", "26.4", "1.21.10", "27.1", ""})
    void unrelatedReleasesKeepTheirOwnVersion(String version) {
        assertThat(Versions.normalizeMinecraftVersion(version)).isEqualTo(version);
    }
}
