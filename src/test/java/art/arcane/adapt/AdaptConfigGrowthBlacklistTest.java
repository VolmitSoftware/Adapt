package art.arcane.adapt;

import art.arcane.adapt.util.config.TomlCodec;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptConfigGrowthBlacklistTest extends AdaptTestBase {
  private Field configField;
  private AdaptConfig previousConfig;

  @BeforeEach
  void installDefaultConfig() throws Exception {
    configField = AdaptConfig.class.getDeclaredField("config");
    configField.setAccessible(true);
    previousConfig = (AdaptConfig) configField.get(null);
    configField.set(null, new AdaptConfig());
  }

  @AfterEach
  void restoreConfig() throws Exception {
    configField.set(null, previousConfig);
  }

  @Test
  void blockNamesResolveAndEveryUnusableNameIsReportedInOneWarning() {
    List<String> warnings = new ArrayList<>();
    Set<Material> blocks = Set.of(Material.CAVE_VINES, Material.CAVE_VINES_PLANT);

    Set<Material> blacklist = AdaptConfig.growthBlacklist(
        List.of("cave_vines", " minecraft:CAVE_VINES_PLANT ", "glow berries", "not_a_block"), blocks::contains, warnings::add);

    assertThat(blacklist).containsExactlyInAnyOrder(Material.CAVE_VINES, Material.CAVE_VINES_PLANT);
    assertThat(warnings).hasSize(1);
    assertThat(warnings.getFirst()).contains("glow berries", "not_a_block").doesNotContain("cave_vines");
  }

  @Test
  void emptyAndMissingListsBlacklistNothingWithoutWarnings() {
    List<String> warnings = new ArrayList<>();

    assertThat(AdaptConfig.growthBlacklist(List.of(), material -> true, warnings::add)).isEmpty();
    assertThat(AdaptConfig.growthBlacklist(null, material -> true, warnings::add)).isEmpty();
    assertThat(warnings).isEmpty();
  }

  @Test
  void defaultConfigSerializesAnEmptyBlacklistAndGrowsEverything() throws IOException {
    AdaptConfig config = new AdaptConfig();
    config.normalize();

    assertThat(config.getBlacklistedGrowables()).isEmpty();
    assertThat(config.isGrowthBlacklisted(Material.CAVE_VINES)).isFalse();
    assertThat(config.isGrowthBlacklisted(null)).isFalse();
    assertThat(TomlCodec.toToml(config, "core-config")).contains("blacklistedGrowables = []");
  }

  @Test
  void reloadsReplaceTheBlacklist() {
    File source = new File(dataFolder, "adapt.toml");

    assertThat(AdaptConfig.reloadSnapshot("blacklistedGrowables = [\"CAVE_VINES\", \"cave_vines_plant\"]\n", source)).isTrue();
    assertThat(AdaptConfig.get().isGrowthBlacklisted(Material.CAVE_VINES)).isTrue();
    assertThat(AdaptConfig.get().isGrowthBlacklisted(Material.CAVE_VINES_PLANT)).isTrue();
    assertThat(AdaptConfig.get().isGrowthBlacklisted(Material.WHEAT)).isFalse();

    assertThat(AdaptConfig.reloadSnapshot("blacklistedGrowables = [\"WHEAT\"]\n", source)).isTrue();
    assertThat(AdaptConfig.get().isGrowthBlacklisted(Material.CAVE_VINES)).isFalse();
    assertThat(AdaptConfig.get().isGrowthBlacklisted(Material.WHEAT)).isTrue();

    assertThat(AdaptConfig.reloadSnapshot("welcomeMessage = false\n", source)).isTrue();
    assertThat(AdaptConfig.get().isGrowthBlacklisted(Material.WHEAT)).isFalse();
  }
}
