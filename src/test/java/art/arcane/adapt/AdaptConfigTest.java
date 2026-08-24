package art.arcane.adapt;

import art.arcane.adapt.util.config.ConfigFileSupport;
import art.arcane.adapt.util.config.TomlCodec;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptConfigTest {
    @Test
    void activatorMaterialNormalizationCachesOnlyUsableBlocks() {
        AdaptConfig config = new AdaptConfig();
        config.adaptActivatorBlock = "stone";
        config.normalize();

        assertThat(config.adaptActivatorBlock).isEqualTo("STONE");
        assertThat(config.getAdaptActivatorMaterial()).isEqualTo(Material.STONE);

        assertThat(AdaptConfig.requireActivatorBlock(Material.DIAMOND, false)).isEqualTo(Material.BOOKSHELF);
        assertThat(AdaptConfig.requireActivatorBlock(Material.AIR, true)).isEqualTo(Material.BOOKSHELF);
    }

    @Test
    void progressionNormalizationProducesFiniteSafeDefaults() throws Exception {
        AdaptConfig config = new AdaptConfig();
        config.experienceMaxLevel = -20;
        setDouble(config, "playerXpPerSkillLevelUpBase", -1D);
        setDouble(config, "playerXpPerSkillLevelUpLevelMultiplier", Double.POSITIVE_INFINITY);
        setDouble(config, "powerPerLevel", Double.NEGATIVE_INFINITY);
        config.normalize();

        assertThat(config.experienceMaxLevel).isOne();
        assertThat(config.getPlayerXpPerSkillLevelUpBase()).isZero();
        assertThat(config.getPlayerXpPerSkillLevelUpLevelMultiplier()).isEqualTo(44D);
        assertThat(config.getPowerPerLevel()).isEqualTo(0.65D);
    }

    @Test
    void advancementAudioControlsHaveSafeDefaultsAndRoundTrip() throws IOException {
        AdaptConfig config = new AdaptConfig();
        String canonical = TomlCodec.toToml(config, "core-config");

        assertThat(config.isAdvancementUnlockToasts()).isTrue();
        assertThat(config.getLevelMilestoneSoundVolume()).isEqualTo(0.35D);
        assertThat(canonical)
            .contains("advancementUnlockToasts = true")
            .contains("levelMilestoneSoundVolume = 0.35");

        AdaptConfig configured = TomlCodec.fromToml(
            canonical
                .replace("advancementUnlockToasts = true", "advancementUnlockToasts = false")
                .replace("levelMilestoneSoundVolume = 0.35", "levelMilestoneSoundVolume = 0.1"),
            AdaptConfig.class
        );

        assertThat(configured.isAdvancementUnlockToasts()).isFalse();
        assertThat(configured.getLevelMilestoneSoundVolume()).isEqualTo(0.1D);
    }

    @Test
    void advancementAudioVolumeNormalizationClampsInvalidValues() {
        assertThat(AdaptConfig.normalizeVolume(-1D, 0.35D)).isZero();
        assertThat(AdaptConfig.normalizeVolume(2D, 0.35D)).isOne();
        assertThat(AdaptConfig.normalizeVolume(Double.NaN, 0.35D)).isEqualTo(0.35D);
        assertThat(AdaptConfig.normalizeVolume(Double.POSITIVE_INFINITY, 0.35D)).isEqualTo(0.35D);
    }

    @Test
    @DisplayName("GUI navigation returns on Escape and shows Back buttons by default")
    void guiNavigationDefaultsToEscapeBackWithVisibleButtons() throws IOException {
        AdaptConfig config = new AdaptConfig();
        String canonical = TomlCodec.toToml(config, "core-config");

        assertThat(config.isEscClosesAllGuis()).isFalse();
        assertThat(config.isGuiBackButton()).isTrue();
        assertThat(canonical)
            .contains("escClosesAllGuis = false")
            .contains("guiBackButton = true");

        AdaptConfig configured = TomlCodec.fromToml(
            canonical
                .replace("escClosesAllGuis = false", "escClosesAllGuis = true")
                .replace("guiBackButton = true", "guiBackButton = false"),
            AdaptConfig.class
        );

        assertThat(configured.isEscClosesAllGuis()).isTrue();
        assertThat(configured.isGuiBackButton()).isFalse();
    }

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

    @Test
    @DisplayName("skills gui only lists progressed skills until the show-all key is enabled")
    void skillsGuiShowAllDefaultsToOff() throws IOException {
        AdaptConfig config = new AdaptConfig();
        String canonical = TomlCodec.toToml(config, "core-config");

        assertThat(config.isGuiShowAllSkills()).isFalse();
        assertThat(canonical).contains("guiShowAllSkills = false");

        AdaptConfig enabled = TomlCodec.fromToml(
            canonical.replace("guiShowAllSkills = false", "guiShowAllSkills = true"),
            AdaptConfig.class
        );

        assertThat(enabled.isGuiShowAllSkills()).isTrue();
    }

    @Test
    void vaultLearningEconomyIsOptionalByDefault() throws IOException {
        AdaptConfig config = new AdaptConfig();
        String canonical = TomlCodec.toToml(config, "core-config");

        assertThat(config.getLearningEconomy().isEnabled()).isFalse();
        assertThat(config.getLearningEconomy().getMoneyPerKnowledge()).isEqualTo(1D);
        assertThat(config.getLearningEconomy().getRefundPercent()).isEqualTo(100D);
        assertThat(canonical)
            .contains("[learningEconomy]")
            .contains("enabled = false")
            .contains("moneyPerKnowledge = 1.0")
            .contains("refundPercent = 100.0");
    }

    @Test
    @DisplayName("permission xp multipliers ship disabled with an empty node table")
    void permissionXpMultipliersShipDisabledAndEmpty() throws IOException {
        AdaptConfig config = new AdaptConfig();
        String canonical = TomlCodec.toToml(config, "core-config");

        assertThat(config.getPermissionXpMultipliers().isEnabled()).isFalse();
        assertThat(config.getPermissionXpMultipliers().getMultipliers()).isEmpty();
        assertThat(canonical)
            .contains("[permissionXpMultipliers]")
            .contains("[permissionXpMultipliers.multipliers]");
    }

    @Test
    @DisplayName("configured permission xp multiplier nodes survive a toml round trip")
    void permissionXpMultiplierNodesSurviveTomlRoundTrip() throws IOException {
        String raw = TomlCodec.toToml(new AdaptConfig(), "core-config")
            .replace("[permissionXpMultipliers.multipliers]",
                "[permissionXpMultipliers.multipliers]\n\"adapt.xpmultiplier.vip\" = 1.5\n\"adapt.xpmultiplier.mvp\" = 2.0");

        AdaptConfig parsed = TomlCodec.fromToml(raw, AdaptConfig.class);
        AdaptConfig restored = TomlCodec.fromToml(TomlCodec.toToml(parsed, "core-config"), AdaptConfig.class);

        assertThat(restored.getPermissionXpMultipliers().getMultipliers())
            .containsEntry("adapt.xpmultiplier.vip", 1.5D)
            .containsEntry("adapt.xpmultiplier.mvp", 2.0D);
    }

    @Test
    @DisplayName("permission xp multiplier stacking is off by default")
    void permissionXpMultiplierStackingIsOffByDefault() throws IOException {
        AdaptConfig config = new AdaptConfig();

        assertThat(config.getPermissionXpMultipliers().isStack()).isFalse();

        AdaptConfig stacked = TomlCodec.fromToml(
            "[permissionXpMultipliers]\nenabled = true\nstack = true\n",
            AdaptConfig.class
        );

        assertThat(stacked.getPermissionXpMultipliers().isStack()).isTrue();
    }

    @Test
    @DisplayName("storage toggles live in their own sql and redis sections")
    void storageTogglesLiveInTheirOwnSections() throws IOException {
        AdaptConfig config = new AdaptConfig();
        String canonical = TomlCodec.toToml(config, "core-config");

        assertThat(config.getSql().isEnabled()).isFalse();
        assertThat(config.getRedis().isEnabled()).isFalse();
        assertThat(config.getSql().getPort()).isEqualTo(3306);
        assertThat(config.getSql().getSecondsCheckverify()).isEqualTo(30);
        assertThat(canonical)
            .contains("[sql]")
            .contains("[redis]")
            .contains("secondsCheckverify = 30")
            .doesNotContain("useSql")
            .doesNotContain("useRedis")
            .doesNotContain("sqlSecondsCheckverify");

        AdaptConfig configured = TomlCodec.fromToml(
            "[sql]\nenabled = true\nsecondsCheckverify = 45\n\n[redis]\nenabled = true\n",
            AdaptConfig.class
        );

        assertThat(configured.getSql().isEnabled()).isTrue();
        assertThat(configured.getSql().getSecondsCheckverify()).isEqualTo(45);
        assertThat(configured.getSql().getHost()).isEqualTo("localhost");
        assertThat(configured.getRedis().isEnabled()).isTrue();
        assertThat(configured.getRedis().getPort()).isEqualTo(6379);
        assertThat(TomlCodec.toToml(configured, "core-config"))
            .contains("secondsCheckverify = 45");
    }

    @Test
    @DisplayName("the gui section ships empty customization tables")
    void guiSectionShipsEmptyCustomizationTables() throws IOException {
        AdaptConfig config = new AdaptConfig();
        String canonical = TomlCodec.toToml(config, "core-config");

        assertThat(config.getGui().getSkillsGuiRows()).isZero();
        assertThat(config.getGui().getSkillIcons()).isEmpty();
        assertThat(config.getGui().getAdaptationIcons()).isEmpty();
        assertThat(config.getGui().getSkillOrder()).isEmpty();
        assertThat(config.getGui().getAdaptationOrder()).isEmpty();
        assertThat(canonical)
            .contains("[gui]")
            .contains("skillsGuiRows = 0")
            .contains("skillOrder = []")
            .contains("[gui.skillIcons]")
            .contains("[gui.adaptationIcons]")
            .contains("[gui.adaptationOrder]");
    }

    @Test
    @DisplayName("gui icon and ordering entries survive a toml round trip")
    void guiCustomizationEntriesSurviveTomlRoundTrip() throws IOException {
        String raw = TomlCodec.toToml(new AdaptConfig(), "core-config")
            .replace("skillsGuiRows = 0", "skillsGuiRows = 4")
            .replace("skillOrder = []", "skillOrder = [\"mining\", \"axe\"]")
            .replace("[gui.skillIcons]", "[gui.skillIcons]\n\"mining\" = \"DIAMOND_PICKAXE\"")
            .replace("[gui.adaptationIcons]", "[gui.adaptationIcons]\n\"mining-vein\" = \"GOLD_INGOT\"")
            .replace("[gui.adaptationOrder]", "[gui.adaptationOrder]\n\"mining\" = [\"mining-vein\", \"mining-blind\"]");

        AdaptConfig parsed = TomlCodec.fromToml(raw, AdaptConfig.class);
        AdaptConfig restored = TomlCodec.fromToml(TomlCodec.toToml(parsed, "core-config"), AdaptConfig.class);

        assertThat(restored.getGui().getSkillsGuiRows()).isEqualTo(4);
        assertThat(restored.getGui().getSkillOrder()).containsExactly("mining", "axe");
        assertThat(restored.getGui().getSkillIcons()).containsEntry("mining", "DIAMOND_PICKAXE");
        assertThat(restored.getGui().getAdaptationIcons()).containsEntry("mining-vein", "GOLD_INGOT");
        assertThat(restored.getGui().getAdaptationOrder())
            .containsEntry("mining", List.of("mining-vein", "mining-blind"));
    }

    private static void setDouble(AdaptConfig config, String fieldName, double value) throws Exception {
        Field field = AdaptConfig.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(config, value);
    }
}
