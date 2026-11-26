package art.arcane.adapt.api.mutation.sound;

import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeGameContent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SoundPalettesTest {
  private static final Path SKILL_SOURCES = Path.of("src/main/java/art/arcane/adapt/content/skill");
  private static final Pattern SKILL_ID = Pattern.compile("super\\(\"([a-z_]+)\"");
  private static final int MAX_CUE_MILLIS = 700;

  @Test
  void everySkillHasFourCues() throws IOException {
    Set<String> palettes = new TreeSet<>();
    for (SoundPalette palette : SoundPalettes.all()) {
      palettes.add(palette.skill());
      assertThat(palette.sounds()).hasSize(4).doesNotContainNull();
      assertThat(SoundPalettes.of(palette.skill())).isSameAs(palette);
    }

    assertThat(palettes).hasSize(24).isEqualTo(skillIds());
  }

  @Test
  void everySoundExistsOnEverySupportedVersion() {
    Map<String, FakeGameContent> versions = Map.of(
        "1.20.1", FakeGameContent.v1_20_1(),
        "1.21.1", FakeGameContent.v1_21_1(),
        "26.1.2", FakeGameContent.v26_1_2(),
        "26.2", FakeGameContent.v26_2(),
        "26.3", FakeGameContent.v26_3());
    Set<Id> sounds = new TreeSet<>();
    for (SoundPalette palette : SoundPalettes.all()) {
      sounds.addAll(palette.sounds());
    }
    sounds.add(SoundPalettes.CONVERGENCE_HIT);

    List<String> missing = new ArrayList<>();
    for (Map.Entry<String, FakeGameContent> version : versions.entrySet()) {
      Set<Id> registered = version.getValue().ids(RegistryKind.SOUND);
      for (Id sound : sounds) {
        if (!registered.contains(sound)) {
          missing.add(sound + " on " + version.getKey());
        }
      }
    }

    assertThat(missing).isEmpty();
  }

  @Test
  void noSoundIsUsedTwice() {
    Map<Id, String> owners = new HashMap<>();
    List<String> repeats = new ArrayList<>();
    for (SoundPalette palette : SoundPalettes.all()) {
      for (Id sound : palette.sounds()) {
        String earlier = owners.putIfAbsent(sound, palette.skill());
        if (earlier != null) {
          repeats.add(sound + " in " + earlier + " and " + palette.skill());
        }
      }
    }

    assertThat(repeats).isEmpty();
    assertThat(owners).doesNotContainKey(SoundPalettes.CONVERGENCE_HIT);
  }

  @Test
  void everySoundIsShort() throws IOException {
    Map<Id, Integer> lengths = lengths();
    List<String> tooLong = new ArrayList<>();
    for (SoundPalette palette : SoundPalettes.all()) {
      for (Id sound : palette.sounds()) {
        Integer millis = lengths.get(sound);
        if (millis == null || millis > MAX_CUE_MILLIS) {
          tooLong.add(palette.skill() + " " + sound + " " + (millis == null ? "unmeasured" : millis + " ms"));
        }
      }
    }

    assertThat(tooLong).isEmpty();
  }

  private static Map<Id, Integer> lengths() throws IOException {
    Map<Id, Integer> lengths = new HashMap<>();
    try (InputStream stream = SoundPalettesTest.class.getResourceAsStream("/sound-lengths.tsv")) {
      assertThat(stream).isNotNull();
      for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
        if (line.isBlank() || line.startsWith("#")) {
          continue;
        }
        String[] columns = line.split("\t");
        lengths.put(Id.minecraft(columns[0]), Integer.parseInt(columns[1].trim()));
      }
    }
    return lengths;
  }

  private static Set<String> skillIds() throws IOException {
    Set<String> ids = new TreeSet<>();
    try (Stream<Path> files = Files.list(SKILL_SOURCES)) {
      for (Path file : files.filter((Path path) -> path.getFileName().toString().startsWith("Skill")).toList()) {
        Matcher matcher = SKILL_ID.matcher(Files.readString(file, StandardCharsets.UTF_8));
        if (matcher.find()) {
          ids.add(matcher.group(1));
        }
      }
    }
    return ids;
  }
}
