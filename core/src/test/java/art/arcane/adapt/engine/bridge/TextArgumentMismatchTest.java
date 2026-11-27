package art.arcane.adapt.engine.bridge;

import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.TextBundle;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.volmlib.util.localization.TextKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** An addon message whose placeholders do not match the arguments it is given renders anyway, visibly. */
class TextArgumentMismatchTest {
  @TempDir
  Path data;

  private TestAdapt adapt;
  private TestPlatform platform;

  @AfterEach
  void stop() {
    AdaptLanguage.texts().uninstall("typo");
    if (adapt != null) {
      adapt.close();
    }
  }

  private void start() {
    platform = TestPlatform.builder(data).build();
    adapt = TestAdapt.start(platform);
    AdaptLanguage.texts().install(TextBundle.declared("typo", List.of(
        TextKey.of("typo.braces", "You hold {count} gears"),
        TextKey.of("typo.angles", "You hold <count> gears"),
        TextKey.of("typo.two", "{who} holds {count} gears"),
        TextKey.of("typo.once", "Only {count} gears")), List.of()));
  }

  @Test
  void aMissingArgumentLeavesItsPlaceholderAsWritten() {
    start();

    String shown = Text.key("typo.braces").resolve();

    assertThat(shown).contains("You hold {count} gears");
  }

  @Test
  void anArgumentTheMessageDoesNotNameIsDroppedAndTheRestStillFill() {
    start();

    assertThat(Text.key("typo.two").with("who", "Ada").with("size", 3).resolve())
        .contains("Ada holds {count} gears");
    assertThat(Text.key("typo.angles").with("count", 3).resolve("en_US")).contains("gears");
  }

  @Test
  void aMismatchIsLoggedOncePerKey() {
    start();

    Text.key("typo.once").resolve();
    Text.key("typo.once").resolve();
    Text.key("typo.once").with("count", 2).resolve();

    List<String> logged = platform.log().warnings().stream().filter(line -> line.contains("typo.once")).toList();
    assertThat(logged).hasSize(1);
    assertThat(logged.get(0)).contains("{count}");
  }

  @Test
  void matchingArgumentsRenderNormallyAndLogNothing() {
    start();

    assertThat(Text.key("typo.braces").with("count", 4).resolve()).contains("You hold 4 gears");
    assertThat(platform.log().warnings()).noneMatch(line -> line.contains("typo.braces"));
  }
}
