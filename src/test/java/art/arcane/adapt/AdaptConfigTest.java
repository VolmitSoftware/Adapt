package art.arcane.adapt;

import art.arcane.adapt.util.config.TomlCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptConfigTest {
    @Test
    @DisplayName("inspired popup config is disabled by default and replaces the old always-on key")
    void inspiredPopupConfigMigratesToQuietDefaults() throws IOException {
        String canonical = TomlCodec.toToml(new AdaptConfig(), "core-config");

        assertThat(canonical)
            .contains("inspiredPopupEnabled = false")
            .contains("inspiredCooldownMillis = 300000")
            .doesNotContain("inspiredNotifyEnabled");

        String establishedConfig = canonical.replace(
            "inspiredPopupEnabled = false",
            "inspiredNotifyEnabled = true"
        );
        AdaptConfig migrated = TomlCodec.fromToml(establishedConfig, AdaptConfig.class);
        String rewritten = TomlCodec.toToml(migrated, "core-config");

        assertThat(migrated.getXpIntegrity().isInspiredPopupEnabled()).isFalse();
        assertThat(migrated.getXpIntegrity().getInspiredCooldownMillis()).isEqualTo(300000L);
        assertThat(rewritten)
            .contains("inspiredPopupEnabled = false")
            .doesNotContain("inspiredNotifyEnabled");
    }
}
