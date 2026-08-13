package art.arcane.adapt.util.config;

import art.arcane.adapt.AdaptConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigDocumentationTest {
  @Test
  @DisplayName("an explicit annotated impact still emits its Effect line")
  void annotatedImpactEmitsAnEffectLine() throws Exception {
    List<String> lines = comments("annotated", false);

    assertThat(lines).containsExactly("Explicit summary.", "Effect: Explicit impact.");
  }

  @Test
  @DisplayName("an annotation without an impact emits the summary alone")
  void annotationWithoutImpactEmitsSummaryOnly() throws Exception {
    List<String> lines = comments("annotatedWithoutImpact", false);

    assertThat(lines).containsExactly("Explicit summary.");
  }

  @Test
  @DisplayName("a generic annotated impact is suppressed instead of falling back")
  void genericAnnotatedImpactIsSuppressed() throws Exception {
    List<String> lines = comments("annotatedWithGenericImpact", false);

    assertThat(lines).containsExactly("Explicit summary.");
  }

  @Test
  @DisplayName("unannotated fields keep a generated summary but never gain an Effect line")
  void unannotatedFieldsNeverGainAnEffectLine() throws Exception {
    assertThat(comments("unannotatedFlag", false)).hasSize(1);
    assertThat(comments("unannotatedCooldownMillis", 0L)).hasSize(1);
    assertThat(comments("unannotatedOverrides", new LinkedHashMap<String, String>())).hasSize(1);
    assertThat(comments("unannotatedFlag", false).getFirst()).doesNotStartWith("Effect:");
    assertThat(comments("unannotatedCooldownMillis", 0L).getFirst()).doesNotStartWith("Effect:");
    assertThat(comments("unannotatedOverrides", new LinkedHashMap<String, String>()).getFirst()).doesNotStartWith("Effect:");
  }

  @Test
  @DisplayName("the generated core config carries no keyword-guessed Effect boilerplate")
  void generatedCoreConfigCarriesNoImpactBoilerplate() {
    String canonical = TomlCodec.toToml(new AdaptConfig(), "core-config");

    assertThat(canonical)
        .doesNotContain("Effect: True enables this behavior and false disables it.")
        .doesNotContain("Effect: Higher values increase intensity or limits; lower values reduce them.")
        .doesNotContain("Effect: Higher values grant more progression; lower values slow progression.")
        .doesNotContain("Effect: Changing this selects a different operating mode.")
        .doesNotContain("Effect: Changing this alters the identifier or text used by the feature.")
        .doesNotContain("Effect: Edit entries to control per-key overrides for this feature.")
        .doesNotContain("Effect: Add or remove entries to control which values are included.")
        .doesNotContain("Effect: Set to false to disable behavior without uninstalling files.");
  }

  @Test
  @DisplayName("annotated storage keys keep their Effect lines in the generated core config")
  void annotatedStorageKeysKeepTheirEffectLines() {
    String canonical = TomlCodec.toToml(new AdaptConfig(), "core-config");

    assertThat(canonical)
        .contains("# Stores player data in a MySQL-compatible database instead of local json files.")
        .contains("# Synchronizes player data between servers over Redis pub/sub.")
        .contains("# Effect: Used to build the jdbc:mysql url; only read while sql.enabled is true.")
        .contains("# Effect: Used for both the pub/sub subscriber and the publisher connection.");
  }

  @Test
  @DisplayName("player-facing sound controls remain visible in generated settings")
  void playerFacingSoundControlsRemainVisible() throws Exception {
    Field field = Fixture.class.getDeclaredField("immunitySoundVolume");

    assertThat(ConfigDocumentation.shouldExposeField("adaptation:nether-ashwalker", "", field, 0D)).isTrue();
  }

  private static List<String> comments(String fieldName, Object value) throws Exception {
    Field field = Fixture.class.getDeclaredField(fieldName);
    return ConfigDocumentation.buildFieldComments("core-config", "fixture", field, value);
  }

  private static final class Fixture {
    @ConfigDoc(value = "Explicit summary.", impact = "Explicit impact.")
    private boolean annotated = false;
    @ConfigDoc(value = "Explicit summary.")
    private boolean annotatedWithoutImpact = false;
    @ConfigDoc(value = "Explicit summary.", impact = "True enables this behavior and false disables it.")
    private boolean annotatedWithGenericImpact = false;
    private boolean unannotatedFlag = false;
    private long unannotatedCooldownMillis = 0;
    private double immunitySoundVolume = 0;
    private Map<String, String> unannotatedOverrides = new LinkedHashMap<>();
  }
}
