package art.arcane.adapt.engine.support;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.platform.ContentAliases;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeGameContent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Content that changes an attribute 1.20.1 lacks must say so with a need ({@code attribute} when the emulator stands in,
 * {@code partialAttribute} or {@code nativeAttribute} otherwise) or check {@code hasAttribute} itself, so the catalog
 * never reports it as working unchanged. Attributes 26.1.2 already lacks predate the legacy versions and are left out, an
 * attribute 1.20.1 keeps for mounts only (jump strength) counts as present, adaptations 1.20.1 disables are skipped, and the golem engine checks {@code hasAttribute} through {@code GolemAttribute}.
 */
class AttributeNeedCoverageTest {
  private static final Path CONTENT = Path.of("../content/src/main/java/art/arcane/adapt/content");
  private static final Path GOLEM_ENGINE = CONTENT.resolve("golem");
  private static final Path CATALOG = Path.of("src/test/resources/platform-support/1.20.1.tsv");
  private static final Pattern ADAPTATION_NAME = Pattern.compile(
      "(?m)^  (?:  super\\(|public static final Id ID = )Id\\.adapt\\(\"([a-z0-9_/]+)\"\\)");

  @Test
  void contentDeclaresEveryAttributeTheOldestVersionLacks() throws IOException, IllegalAccessException {
    Map<String, Id> missing = missingOnlyOnLegacy();
    Set<String> disabled = disabledOnLegacy();
    List<String> undeclared = new ArrayList<>();
    List<Path> files;
    try (Stream<Path> walk = Files.walk(CONTENT)) {
      files = walk.filter((Path path) -> path.toString().endsWith(".java") && !path.startsWith(GOLEM_ENGINE)).sorted().toList();
    }
    for (Path file : files) {
      String source = Files.readString(file, StandardCharsets.UTF_8);
      Matcher name = ADAPTATION_NAME.matcher(source);
      if (name.find() && disabled.contains("adapt:" + name.group(1))) {
        continue;
      }
      for (Map.Entry<String, Id> attribute : missing.entrySet()) {
        String constant = "Attributes." + attribute.getKey();
        if (!Pattern.compile(Pattern.quote(constant) + "\\b").matcher(source).find()) {
          continue;
        }
        boolean declared = Pattern.compile("(attribute|partialAttribute|nativeAttribute)\\(" + Pattern.quote(constant) + "\\b").matcher(source).find();
        boolean guarded = source.contains("hasAttribute(" + constant + ")");
        if (!declared && !guarded) {
          undeclared.add(CONTENT.relativize(file) + " " + attribute.getValue());
        }
      }
    }

    assertThat(undeclared).isEmpty();
  }

  private static Map<String, Id> missingOnlyOnLegacy() throws IllegalAccessException {
    FakeGameContent oldest = FakeGameContent.v1_20_1();
    FakeGameContent modern = FakeGameContent.v26_1_2();
    Map<String, Id> missing = new LinkedHashMap<>();
    for (Field field : Attributes.class.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers()) || field.getType() != Id.class) {
        continue;
      }
      Id id = (Id) field.get(null);
      Id mount = ContentAliases.mountAttribute(id);
      boolean legacy = oldest.exists(RegistryKind.ATTRIBUTE, id) || mount != null && oldest.exists(RegistryKind.ATTRIBUTE, mount);
      if (!legacy && modern.exists(RegistryKind.ATTRIBUTE, id)) {
        missing.put(field.getName(), id);
      }
    }
    assertThat(missing).isNotEmpty();
    return missing;
  }

  private static Set<String> disabledOnLegacy() throws IOException {
    Set<String> disabled = new HashSet<>();
    for (String line : Files.readAllLines(CATALOG, StandardCharsets.UTF_8)) {
      String[] columns = line.split("\t");
      if (columns.length >= 3 && columns[2].equals("disabled")) {
        disabled.add(columns[1]);
      }
    }
    assertThat(disabled).isNotEmpty();
    return disabled;
  }
}
