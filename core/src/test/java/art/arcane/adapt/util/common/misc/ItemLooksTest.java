package art.arcane.adapt.util.common.misc;

import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.MutationTools;
import art.arcane.adapt.content.CoreContent;
import art.arcane.adapt.content.item.ChalkWandItem;
import art.arcane.adapt.testkit.FakeGameContent;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Every mutation tool and chalk wand has a look on its own material, and the client mod ships that look for every version. */
class ItemLooksTest {
  private static final Path CLIENT = Path.of("../adapters/client-common/src");
  private static final List<String> MODEL_VERSIONS = List.of("1.20.1", "1.21.1");
  private static final List<String> DEFINITION_VERSIONS = List.of("26.1.2", "26.2", "26.3");

  @TempDir
  Path dataFolder;

  @Test
  void everyToolHasALookOnItsMaterial() {
    TestPlatform platform = TestPlatform.builder(dataFolder).build();
    platform.host().runGame(FakeGameContent.v26_3());
    List<String> wrong = new ArrayList<>();
    try (TestAdapt adapt = TestAdapt.start(platform, CoreContent.skillTypes())) {
      for (MutationTool<?> tool : MutationTools.all(adapt.core().getAdaptServer().getSkillRegistry())) {
        ItemLooks.Look look = ItemLooks.LOOKS.stream().filter((ItemLooks.Look entry) -> entry.key().equals(tool.id())).findFirst()
            .orElse(null);
        if (look == null || !look.material().equals(tool.material())) {
          wrong.add(tool.id() + " on " + tool.material());
        }
      }
    }
    for (ChalkWandItem.Tool wand : ChalkWandItem.Tool.values()) {
      if (ItemLooks.number("chalk-" + wand.id().replace('_', '-')) == 0) {
        wrong.add("chalk " + wand.id());
      }
    }
    assertThat(wrong).as("tools without a look").isEmpty();
  }

  @Test
  void numbersAreDistinctAndTheClientShipsEveryLook() throws IOException {
    Set<Integer> numbers = new HashSet<>();
    List<String> missing = new ArrayList<>();
    for (ItemLooks.Look look : ItemLooks.LOOKS) {
      int number = ItemLooks.number(look.key());
      assertThat(numbers.add(number)).as(look.key()).isTrue();
      assertThat((int) (float) number).as("custom model data as a float on 1.21.4+").isEqualTo(number);
      for (String file : List.of("textures/item/" + look.key() + ".png", "models/item/" + look.key() + ".json")) {
        if (!Files.isRegularFile(CLIENT.resolve("main/resources/assets/adapt").resolve(file))) {
          missing.add(file);
        }
      }
      String material = look.material().path();
      for (String version : MODEL_VERSIONS) {
        expect(version + "/resources/assets/minecraft/models/item/" + material + ".json", "\"custom_model_data\": " + number, missing);
      }
      for (String version : DEFINITION_VERSIONS) {
        expect(version + "/resources/assets/minecraft/items/" + material + ".json", "\"threshold\": " + number, missing);
      }
    }
    assertThat(missing).as("item look files").isEmpty();
  }

  private static void expect(String versionFile, String text, List<String> missing) throws IOException {
    Path file = CLIENT.resolve("versions").resolve(versionFile);
    if (!Files.isRegularFile(file) || !Files.readString(file).contains(text)) {
      missing.add(versionFile + " -> " + text);
    }
  }
}
