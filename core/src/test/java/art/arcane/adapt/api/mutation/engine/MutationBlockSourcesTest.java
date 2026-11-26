package art.arcane.adapt.api.mutation.engine;

import art.arcane.adapt.api.mutation.blocks.BlockRef;
import art.arcane.adapt.api.mutation.blocks.BlockSlot;
import art.arcane.adapt.api.mutation.blocks.SkillBlockCatalog;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrades;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Block sources sit under {@code content/adaptation/<package>/blocks/} with the conventional class name and a literal
 * id, each is registered once by its own skill's {@code registerBlocks}, and every skill with blocks has its test data
 * in {@code src/test/resources/mutation-blocks/<skill>.json}: the six classes, the events each listens to, the existing
 * classes each one reuses, the versions it is unavailable on, and its behaviour test.
 */
class MutationBlockSourcesTest {
  private static final Path ROOT = Path.of(System.getProperty("user.dir"));
  private static final Path SOURCE_ROOT = ROOT.resolve("src/main/java");
  private static final Path TEST_ROOT = ROOT.resolve("src/test/java");
  private static final Path CONTENT = SOURCE_ROOT.resolve("art/arcane/adapt/content");
  private static final Path DATA = ROOT.resolve("src/test/resources/mutation-blocks");
  private static final Pattern BLOCK_CLASS = Pattern.compile(
      "(?m)^public\\s+(?:final\\s+)?class\\s+(\\w+)\\s+extends\\s+(?:Trigger|Strike|Boon|Fuel|State)Block<");
  private static final Pattern BLOCK_ID = Pattern.compile("\\bsuper\\(\"(block-[a-z0-9-]+)\"\\)");
  private static final Pattern SKILL_ID = Pattern.compile("\\bsuper\\(\"([a-z]+)\"\\s*,");
  private static final Pattern REGISTER_BLOCKS = Pattern.compile("registerBlocks\\(([^;]*)\\);");
  private static final Pattern HANDLER = Pattern.compile("@Handle[^\\n]*\\n\\s*public\\s+void\\s+\\w+\\((\\w+)\\s");

  @Test
  void everyBlockSourceFollowsTheConventionAndIsRegisteredByItsSkill() throws IOException {
    Map<String, String> skillSources = skillSources();
    Map<BlockRef, Path> found = blockSources();
    for (Map.Entry<BlockRef, Path> entry : found.entrySet()) {
      BlockRef ref = entry.getKey();
      Path path = entry.getValue();
      String className = SOURCE_ROOT.relativize(path).toString().replace('/', '.').replace(".java", "");
      assertThat(className).as("class of %s", ref).isEqualTo(MutationBlocks.className(ref.skillId(), ref.slot()));
      String simpleName = path.getFileName().toString().replace(".java", "");
      List<String> registeredBy = new ArrayList<>();
      for (Map.Entry<String, String> skill : skillSources.entrySet()) {
        Matcher register = REGISTER_BLOCKS.matcher(skill.getValue());
        if (register.find() && register.group(1).matches("(?s).*\\bnew " + simpleName + "\\(\\).*")) {
          registeredBy.add(skill.getKey());
        }
      }
      assertThat(registeredBy).as("skills registering %s", ref).containsExactly(ref.skillId());
    }
  }

  @Test
  void everySkillWithBlocksHasMatchingTestData() throws IOException {
    Map<BlockRef, Path> found = blockSources();
    Set<String> skillsWithBlocks = new TreeSet<>();
    for (BlockRef ref : found.keySet()) {
      skillsWithBlocks.add(ref.skillId());
    }
    Set<String> skillsWithData = new TreeSet<>();
    if (Files.isDirectory(DATA)) {
      try (Stream<Path> files = Files.list(DATA)) {
        for (Path file : files.filter((Path path) -> path.toString().endsWith(".json")).sorted().toList()) {
          skillsWithData.add(checkData(file, found));
        }
      }
    }
    assertThat(skillsWithData).as("skills with block test data").isEqualTo(skillsWithBlocks);
  }

  private static String checkData(Path file, Map<BlockRef, Path> found) throws IOException {
    JsonObject data = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    String skill = data.get("skill").getAsString();
    assertThat(file.getFileName().toString()).as("file of %s", skill).isEqualTo(skill + ".json");
    assertThat(SkillBlockCatalog.byId(skill)).as("catalog skill %s", skill).isPresent();
    Set<BlockSlot> slots = new LinkedHashSet<>();
    for (JsonElement element : data.getAsJsonArray("pieces")) {
      JsonObject piece = element.getAsJsonObject();
      BlockSlot slot = BlockSlot.valueOf(piece.get("slot").getAsString());
      assertThat(slots.add(slot)).as("%s lists %s twice", file, slot).isTrue();
      BlockRef ref = new BlockRef(skill, slot);
      Path source = found.get(ref);
      assertThat(source).as("source of %s", ref).isNotNull();
      assertThat(piece.get("class").getAsString()).as("class of %s", ref)
          .isEqualTo(source.getFileName().toString().replace(".java", ""));
      String code = Files.readString(source);
      assertThat(handlers(code)).as("handled events of %s", ref).isEqualTo(strings(piece.getAsJsonArray("handles")));
      for (String reused : strings(piece.getAsJsonArray("reuses"))) {
        assertThat(Pattern.compile("\\b" + Pattern.quote(reused) + "\\b").matcher(code).find())
            .as("%s uses %s", ref, reused).isTrue();
      }
    }
    assertThat(slots).as("slots in %s", file).containsExactlyInAnyOrder(BlockSlot.values());
    String behaviourTest = data.get("behaviourTest").getAsString();
    Path test = TEST_ROOT.resolve("art/arcane/adapt/content/adaptation/" + SkillUpgrades.contentPackage(skill)
        + "/blocks/" + behaviourTest + ".java");
    assertThat(test).as("behaviour test of %s", skill).exists();
    assertThat(data.getAsJsonObject("unavailableOn")).as("unavailableOn in %s", file).isNotNull();
    return skill;
  }

  private static Map<BlockRef, Path> blockSources() throws IOException {
    Map<BlockRef, Path> found = new LinkedHashMap<>();
    for (Path path : javaFiles(CONTENT.resolve("adaptation"))) {
      String source = Files.readString(path);
      if (!BLOCK_CLASS.matcher(source).find()) {
        continue;
      }
      assertThat(path.getParent().getFileName().toString()).as("folder of %s", path).isEqualTo("blocks");
      Matcher id = BLOCK_ID.matcher(source);
      assertThat(id.find()).as("literal super(\"block-...\") id in %s", path).isTrue();
      BlockRef ref = MutationBlocks.parse(id.group(1));
      assertThat(ref).as("block id %s in %s", id.group(1), path).isNotNull();
      assertThat(found.put(ref, path)).as("second source for %s", ref).isNull();
    }
    return found;
  }

  private static Map<String, String> skillSources() throws IOException {
    Map<String, String> sources = new LinkedHashMap<>();
    for (Path path : javaFiles(CONTENT.resolve("skill"))) {
      String source = Files.readString(path);
      Matcher skillId = SKILL_ID.matcher(source);
      if (skillId.find()) {
        sources.put(skillId.group(1), source);
      }
    }
    return sources;
  }

  private static Set<String> handlers(String code) {
    Set<String> events = new TreeSet<>();
    Matcher matcher = HANDLER.matcher(code);
    while (matcher.find()) {
      events.add(matcher.group(1));
    }
    return events;
  }

  private static Set<String> strings(JsonArray array) {
    Set<String> values = new TreeSet<>();
    for (JsonElement element : array) {
      values.add(element.getAsString());
    }
    return values;
  }

  private static List<Path> javaFiles(Path root) throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      return files.filter((Path path) -> path.toString().endsWith(".java")).sorted().toList();
    }
  }
}
