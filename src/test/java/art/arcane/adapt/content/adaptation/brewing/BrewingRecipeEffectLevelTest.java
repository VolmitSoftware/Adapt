package art.arcane.adapt.content.adaptation.brewing;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BrewingRecipeEffectLevelTest {
  private static final Path BREWING_SOURCE_DIR = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/brewing");
  private static final Pattern ADD_EFFECT = Pattern.compile(
      "\\.addEffect\\s*\\(\\s*PotionEffectTypes?\\s*\\.\\s*(\\w+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,");
  private static final String SATURATION_SOURCE = "BrewingSaturation.java";

  private record BrewedEffect(String effect, int durationTicks, int amplifier) {
  }

  @Test
  void everyBrewingRecipeUsesZeroBasedAmplifiers() throws IOException {
    List<Path> sources = brewingSources();
    assertThat(sources).isNotEmpty();

    for (Path source : sources) {
      String fileName = source.getFileName().toString();
      if (fileName.equals(SATURATION_SOURCE)) {
        continue;
      }
      List<BrewedEffect> effects = parseEffects(source);
      if (effects.isEmpty()) {
        continue;
      }
      assertThat(effects.get(0).amplifier())
          .as("%s tier 1 amplifier (effect level I)", fileName)
          .isZero();
      if (effects.size() > 1) {
        assertThat(effects.get(1).amplifier())
            .as("%s tier 2 amplifier (effect level II)", fileName)
            .isEqualTo(1);
      }
    }
  }

  @Test
  void saturationKeepsMagnitudeAmplifiers() throws IOException {
    List<BrewedEffect> effects = parseEffects(BREWING_SOURCE_DIR.resolve(SATURATION_SOURCE));
    assertThat(effects).hasSize(2);
    assertThat(effects.get(0).amplifier()).isEqualTo(4);
    assertThat(effects.get(1).amplifier()).isEqualTo(8);
  }

  @Test
  void decayDurationsMatchAdvertisedSeconds() throws IOException {
    List<BrewedEffect> effects = parseEffects(BREWING_SOURCE_DIR.resolve("BrewingDecay.java"));
    assertThat(effects).hasSize(2);
    assertThat(effects.get(0).durationTicks()).isEqualTo(320);
    assertThat(effects.get(1).durationTicks()).isEqualTo(160);
  }

  @Test
  void healthBoostTierTwoUsesItsOwnDisplayName() throws IOException {
    String source = Files.readString(BREWING_SOURCE_DIR.resolve("BrewingHealthBoost.java"));
    assertThat(source).contains("BrewingMessages.HEALTH_BOOST_STRONG_NAME");
    assertThat(source.indexOf("BrewingMessages.HEALTH_BOOST_STRONG_NAME"))
        .isGreaterThan(source.indexOf("BrewingMessages.HEALTH_BOOST_NAME"));
  }

  private static List<Path> brewingSources() throws IOException {
    try (Stream<Path> files = Files.list(BREWING_SOURCE_DIR)) {
      return files.filter(path -> path.getFileName().toString().endsWith(".java"))
          .sorted(Comparator.comparing(Path::getFileName))
          .toList();
    }
  }

  private static List<BrewedEffect> parseEffects(Path source) throws IOException {
    Matcher matcher = ADD_EFFECT.matcher(Files.readString(source));
    List<BrewedEffect> effects = new ArrayList<>(2);
    while (matcher.find()) {
      effects.add(new BrewedEffect(
          matcher.group(1),
          Integer.parseInt(matcher.group(2)),
          Integer.parseInt(matcher.group(3))));
    }
    return effects;
  }
}
