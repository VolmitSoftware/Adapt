package art.arcane.adapt.engine.mutation.sound;

import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.mutation.kit.DefaultKit;
import art.arcane.adapt.engine.mutation.kit.Kits;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeGameContent;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SoundPalettesTest {
  private static final Path SKILL_SOURCES = Path.of("../content/src/main/java/art/arcane/adapt/content/skill");
  private static final Pattern SKILL_ID = Pattern.compile("\\bId\\s+ID\\s*=\\s*Id\\.adapt\\(\"([a-z_]+)\"");
  private static final int MAX_CUE_MILLIS = 700;
  private static final String GENERATED = "generated kit";

  @TempDir
  static Path data;

  private static TestAdapt adapt;
  private static Map<String, SoundPalette> kits;

  @BeforeAll
  static void start() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), new BuiltinAddon());
    kits = new LinkedHashMap<>();
    for (Skill<?> skill : adapt.core().getAdaptServer().getSkillRegistry().getAllSkills()) {
      kits.put(skill.getId().path(), Kits.of(skill).sounds());
    }
  }

  @AfterAll
  static void stop() {
    adapt.close();
  }

  /** Every registered kit's palette by skill, and the generated kits' palette. */
  private static Map<String, SoundPalette> palettes() {
    Map<String, SoundPalette> palettes = new LinkedHashMap<>(kits);
    palettes.put(GENERATED, DefaultKit.SOUNDS);
    return palettes;
  }

  @Test
  void everySkillHasFourCues() throws IOException {
    Set<String> palettes = new TreeSet<>();
    for (Map.Entry<String, SoundPalette> palette : kits.entrySet()) {
      palettes.add(palette.getKey());
      assertThat(palette.getValue().sounds()).hasSize(4).doesNotContainNull();
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
    for (SoundPalette palette : palettes().values()) {
      sounds.addAll(palette.sounds());
    }
    sounds.add(MutationSoundPlayer.CONVERGENCE_HIT);

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
    for (Map.Entry<String, SoundPalette> palette : palettes().entrySet()) {
      for (Id sound : palette.getValue().sounds()) {
        String earlier = owners.putIfAbsent(sound, palette.getKey());
        if (earlier != null) {
          repeats.add(sound + " in " + earlier + " and " + palette.getKey());
        }
      }
    }

    assertThat(repeats).isEmpty();
    assertThat(owners).doesNotContainKey(MutationSoundPlayer.CONVERGENCE_HIT);
  }

  @Test
  void everySoundIsShort() throws IOException {
    Map<Id, Integer> lengths = lengths();
    List<String> tooLong = new ArrayList<>();
    for (Map.Entry<String, SoundPalette> palette : palettes().entrySet()) {
      for (Id sound : palette.getValue().sounds()) {
        Integer millis = lengths.get(sound);
        if (millis == null || millis > MAX_CUE_MILLIS) {
          tooLong.add(palette.getKey() + " " + sound + " " + (millis == null ? "unmeasured" : millis + " ms"));
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
