package art.arcane.adapt.engine.bridge;

import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.TextBundle;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.volmlib.util.localization.PluralKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A plural message reached through {@link Text} picks its form by the number its selector argument holds. */
class PluralTextTest {
  @TempDir
  Path data;

  private TestAdapt adapt;

  @BeforeEach
  void start() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build());
    AdaptLanguage.texts().install(TextBundle.declared("plural", List.of(PluralKey.of("plural.gears", "count",
        Map.of("one", "{count} gear", "other", "{count} gears"))), List.of()));
  }

  @AfterEach
  void stop() {
    AdaptLanguage.texts().uninstall("plural");
    adapt.close();
  }

  @Test
  void theSelectorArgumentChoosesTheForm() {
    assertThat(Text.key("plural.gears").with("count", 1).resolve()).contains("1 gear").doesNotContain("gears");
    assertThat(Text.key("plural.gears").withMarkup("count", "3").resolve()).contains("3 gears");
  }

  @Test
  void aSelectorThatIsNotANumberCountsAsZero() {
    assertThat(Text.key("plural.gears").with("count", "many").resolve()).contains("0 gears");
  }
}
