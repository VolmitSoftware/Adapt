package art.arcane.adapt;

import art.arcane.adapt.platform.event.Handle;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ShrinkRulesTest {
  private static final Path SHRINK_RULES = Path.of("packaging/shrink.pro");

  @Test
  void busHandlerMethodsSurviveShrinkingInEveryClass() throws IOException {
    String rules = Files.readString(SHRINK_RULES, StandardCharsets.UTF_8);
    Pattern keep = Pattern.compile("-keepclassmembers\\s+class\\s+\\*\\s*\\{\\s*@" + Pattern.quote(Handle.class.getName())
        + "\\s+<methods>;\\s*}");
    Matcher matcher = keep.matcher(rules);

    assertThat(matcher.find())
        .as("@Handle methods are only reached through reflection; ProGuard must keep them in every class")
        .isTrue();
  }
}
