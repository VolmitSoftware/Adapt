package art.arcane.adapt.engine.mutation.engine;

import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.api.mutation.BlockSlot;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.support.PlatformSupport;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.testkit.FakeGameContent;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The blocks each skill cannot run on an older Minecraft version are exactly the slots its test data lists under
 * {@code unavailableOn}, so these stay out of the shared platform-support catalog.
 */
class MutationBlockVersionsTest {
  private static final Path DATA = Path.of(System.getProperty("user.dir")).resolve("src/test/resources/mutation-blocks");

  @TempDir
  Path data;

  @BeforeEach
  @AfterEach
  void forget() {
    PlatformSupport.reset();
  }

  @Test
  void unavailableBlocksOn1_20_1MatchTheTestData() throws IOException {
    check("1.20.1", FakeGameContent.v1_20_1());
  }

  @Test
  void unavailableBlocksOn1_21_1MatchTheTestData() throws IOException {
    check("1.21.1", FakeGameContent.v1_21_1());
  }

  private void check(String version, FakeGameContent game) throws IOException {
    TestPlatform platform = TestPlatform.builder(data).build();
    platform.host().runGame(game);
    try (TestAdapt adapt = TestAdapt.start(platform, new BuiltinAddon());
         Stream<Path> files = Files.isDirectory(DATA) ? Files.list(DATA) : Stream.empty()) {
      SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
      for (Path file : files.filter((Path path) -> path.toString().endsWith(".json")).sorted().toList()) {
        JsonObject entry = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        String skillId = entry.get("skill").getAsString();
        JsonArray listed = entry.getAsJsonObject("unavailableOn").getAsJsonArray(version);
        Set<String> expected = new TreeSet<>();
        if (listed != null) {
          for (JsonElement slot : listed) {
            expected.add(BlockSlot.valueOf(slot.getAsString()).name());
          }
        }
        Skill<?> skill = registry.getAnySkill(Id.adapt(skillId));
        assertThat(skill).as("%s is registered on %s", skillId, version).isNotNull();
        if (!(ComponentHost.find(skill) instanceof SkillHost host)) {
          assertThat(registry.getSkill(Id.adapt(skillId))).as("%s has no runtime only when left out on %s", skillId, version)
              .isNull();
          assertThat(expected).as("%s is left out on %s, so the data lists no single blocks for it", skillId, version)
              .isEmpty();
          continue;
        }
        Set<String> actual = new TreeSet<>();
        for (MutationBlock<?> block : host.getUnavailableBlocks()) {
          actual.add(block.getSlot().name());
        }
        assertThat(actual).as("%s blocks unavailable on %s", skillId, version).isEqualTo(expected);
      }
    }
  }
}
