package art.arcane.adapt.content;

import art.arcane.adapt.platform.ContentAliases;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeGameContent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every vanilla id, tag and item or block name (the {@code COPPER_CHEST} form config defaults, recipes and block lists
 * are written with) core names in its sources must be handled on each older supported version: it exists there
 * (directly or under an older name or FX substitute from {@link ContentAliases}), a fallback that exists sits on the
 * same line (a declared fallback, or a set of ids compared with live types), the file is an adaptation that version
 * disables, or the use is listed below with the reason it is harmless.
 */
class ContentIdCoverageTest {
  private static final List<Path> SOURCES = List.of(Path.of("src/main/java/art/arcane/adapt"),
      Path.of("../core/src/main/java/art/arcane/adapt"), Path.of("../api/src/main/java/art/arcane/adapt"));
  private static final Path CATALOGS = Path.of("../core/src/test/resources/platform-support");
  private static final Pattern ID = Pattern.compile("Id\\.minecraft\\(\"([a-z0-9_./]+)\"\\)|\"minecraft:([a-z0-9_./]+)\"");
  private static final Pattern TAG = Pattern.compile("\"#(?:minecraft:)?([a-z0-9_/]+)\"");
  private static final Pattern MATERIAL = Pattern.compile("\"([A-Z][A-Z0-9_]*)\"");
  private static final Pattern ADAPTATION_NAME = Pattern.compile("static final Id ID = Id\\.adapt\\(\"([a-z0-9_/]+)\"\\);");
  private static final Map<String, String> HARMLESS = Map.ofEntries(
      Map.entry("api/attribute/Attributes.java", "attribute ids: AdaptAttributeService emulates or skips missing ones and LivingRef reports absence"),
      Map.entry("engine/fx/WorldEmitter.java#tinted_leaves", "compared with a particle type that is never sent"),
      Map.entry("content/golem/plinth/PlinthStructure.java", "chiseled copper pedestals, replaced by cut copper through defaultPedestals"),
      Map.entry("content/golem/GolemBody.java#copper_golem", "body type of a golem that cannot be built without the copper golem"),
      Map.entry("content/skill/golemancy/CopperCare.java#copper_golem", "compared with entity types"),
      Map.entry("content/adaptation/golemancy/BreakerRules.java#copper_pickaxe", "compared with held tool types"),
      Map.entry("content/adaptation/axe/AxeCraftLogSwap.java#pale_oak_log", "optional wood, filtered by availableWoods"),
      Map.entry("content/adaptation/axe/AxeCraftLogSwap.java#pale_oak_sapling", "optional wood, filtered by availableWoods"),
      Map.entry("content/adaptation/discovery/DiscoveryKeenEye.java#trial_spawner", "compared with block types"),
      Map.entry("content/adaptation/golemancy/GathererWork.java#bundle", "compared with item types"),
      Map.entry("content/adaptation/crafting/CraftingPreferences.java#bundle", "preference of Crafting Backpacks, disabled without bundles"),
      Map.entry("content/item/BackpackItem.java#bundle", "item made only by Crafting Backpacks, disabled without bundles"));

  @Test
  void everyIdCoreNamesIsHandledOn1_20_1() throws IOException {
    assertThat(unhandled("1.20.1", FakeGameContent.v1_20_1(), new HashSet<>())).isEmpty();
  }

  @Test
  void everyIdCoreNamesIsHandledOn1_21_1() throws IOException {
    assertThat(unhandled("1.21.1", FakeGameContent.v1_21_1(), new HashSet<>())).isEmpty();
  }

  @Test
  void everyIdCoreNamesIsHandledOn26_1_2() throws IOException {
    assertThat(unhandled("26.1.2", FakeGameContent.v26_1_2(), new HashSet<>())).isEmpty();
  }

  @Test
  void tagSnapshotsTellOldTagsFromNewOnes() {
    assertThat(FakeGameContent.v1_20_1().hasTag(RegistryKind.BLOCK, Id.minecraft("logs"))).isTrue();
    assertThat(FakeGameContent.v1_20_1().hasTag(RegistryKind.ITEM, Id.minecraft("pickaxes"))).isTrue();
    assertThat(FakeGameContent.v1_20_1().hasTag(RegistryKind.BLOCK, Id.minecraft("bee_attractive"))).isFalse();
    assertThat(FakeGameContent.v26_3().hasTag(RegistryKind.BLOCK, Id.minecraft("bee_attractive"))).isTrue();
  }

  @Test
  void everyHarmlessEntryIsStillNeeded() throws IOException {
    Set<String> used = new HashSet<>();
    unhandled("1.20.1", FakeGameContent.v1_20_1(), used);
    unhandled("1.21.1", FakeGameContent.v1_21_1(), used);
    unhandled("26.1.2", FakeGameContent.v26_1_2(), used);
    assertThat(used).containsExactlyInAnyOrderElementsOf(HARMLESS.keySet());
  }

  private static List<String> unhandled(String version, FakeGameContent game, Set<String> usedHarmless) throws IOException {
    FakeGameContent newest = FakeGameContent.v26_3();
    Set<String> disabled = disabledAdaptations(version);
    List<String> problems = new ArrayList<>();
    Map<Path, Path> files = new TreeMap<>();
    for (Path root : SOURCES) {
      try (Stream<Path> walk = Files.walk(root)) {
        walk.filter((Path path) -> path.toString().endsWith(".java")).forEach((Path path) -> files.put(path, root));
      }
    }
    for (Map.Entry<Path, Path> entry : files.entrySet()) {
      Path file = entry.getKey();
      String relative = entry.getValue().relativize(file).toString().replace('\\', '/');
      String source = Files.readString(file, StandardCharsets.UTF_8);
      Matcher name = ADAPTATION_NAME.matcher(source);
      boolean disabledHere = source.contains("needs(") && name.find() && disabled.contains("adapt:" + name.group(1));
      String[] lines = source.split("\n", -1);
      for (int index = 0; index < lines.length; index++) {
        List<String> missing = new ArrayList<>();
        Matcher match = ID.matcher(lines[index]);
        while (match.find()) {
          Id id = Id.minecraft(match.group(1) != null ? match.group(1) : match.group(2));
          if (known(newest, id)) {
            if (!known(game, id) && !hasFallbackOnLine(game, lines[index], id)) {
              missing.add(id.toString());
            }
          } else if (knownTag(newest, id) && !knownTag(game, id)) {
            missing.add("#" + id);
          }
        }
        Matcher material = MATERIAL.matcher(lines[index]);
        while (material.find()) {
          Id id = Id.minecraft(material.group(1).toLowerCase(Locale.ROOT));
          if (knownMaterial(newest, id) && !knownMaterial(game, id) && !hasFallbackOnLine(game, lines[index], id)) {
            missing.add(id.toString());
          }
        }
        Matcher tag = TAG.matcher(lines[index]);
        while (tag.find()) {
          Id id = Id.minecraft(tag.group(1));
          if (knownTag(newest, id) && !knownTag(game, id)) {
            missing.add("#" + id);
          }
        }
        if (disabledHere) {
          continue;
        }
        for (String id : missing) {
          String path = id.substring(id.indexOf(':') + 1);
          String key = HARMLESS.containsKey(relative) ? relative : relative + "#" + path;
          if (HARMLESS.containsKey(key)) {
            usedHarmless.add(key);
            continue;
          }
          problems.add(relative + ":" + (index + 1) + " " + id);
        }
      }
    }
    return problems;
  }

  private static boolean known(FakeGameContent game, Id id) {
    for (RegistryKind kind : RegistryKind.values()) {
      if (game.exists(kind, id) || (kind == RegistryKind.SOUND || kind == RegistryKind.PARTICLE)
          && ContentAliases.resolveFx(kind, id, (Id candidate) -> game.exists(kind, candidate)) != null) {
        return true;
      }
    }
    return false;
  }

  private static boolean knownMaterial(FakeGameContent game, Id id) {
    return game.exists(RegistryKind.ITEM, id) || game.exists(RegistryKind.BLOCK, id);
  }

  private static boolean knownTag(FakeGameContent game, Id tag) {
    for (RegistryKind kind : RegistryKind.values()) {
      if (game.hasTag(kind, tag)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasFallbackOnLine(FakeGameContent game, String line, Id missing) {
    Matcher match = ID.matcher(line);
    while (match.find()) {
      Id other = Id.minecraft(match.group(1) != null ? match.group(1) : match.group(2));
      if (!other.equals(missing) && known(game, other)) {
        return true;
      }
    }
    Matcher material = MATERIAL.matcher(line);
    while (material.find()) {
      Id other = Id.minecraft(material.group(1).toLowerCase(Locale.ROOT));
      if (!other.equals(missing) && knownMaterial(game, other)) {
        return true;
      }
    }
    return false;
  }

  private static Set<String> disabledAdaptations(String version) throws IOException {
    Path catalog = CATALOGS.resolve(version + ".tsv");
    Set<String> disabled = new HashSet<>();
    if (!Files.exists(catalog)) {
      return disabled;
    }
    for (String row : Files.readAllLines(catalog, StandardCharsets.UTF_8)) {
      String[] columns = row.split("\t", -1);
      if (columns.length >= 3 && columns[2].equals("disabled")) {
        disabled.add(columns[1]);
      }
    }
    return new TreeSet<>(disabled);
  }
}
