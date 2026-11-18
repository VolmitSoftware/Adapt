package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.util.config.ConfigFileSupport;
import art.arcane.adapt.util.config.TomlCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InfusionRecipeTomlTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void theDefaultTableRoundTripsThroughTomlConfigFiles() throws IOException {
    Path configPath = temporaryDirectory.resolve("golemancy.toml");
    Holder defaults = new Holder();

    ConfigFileSupport.load(configPath.toFile(), null, Holder.class, defaults, true, "skill:test-infusions", "created");
    String raw = Files.readString(configPath);
    Holder loaded = ConfigFileSupport.load(configPath.toFile(), null, Holder.class, new Holder(), false, "skill:test-infusions", "unused");

    assertThat(raw)
        .contains("[infusionRecipes.swiftness_1]")
        .contains("result = \"golemancy:rune/swiftness/1\"")
        .contains("pedestals = [\"SUGAR\", \"SUGAR\", \"FEATHER\", \"FEATHER\"]")
        .contains("[infusionRecipes.masterwork_heart]")
        .contains("unlockAdaptation = \"golem-masterwork\"");
    assertThat(loaded).usingRecursiveComparison().isEqualTo(defaults);
  }

  @Test
  void editedTomlReplacesTheWholeTable() throws IOException {
    String raw = "[infusionRecipes.quick_order]\n"
        + "result = \"golemancy:rune/order/1\"\n"
        + "center = \"golemancy:blank_rune\"\n"
        + "pedestals = [\"STONE\", \"STONE\", \"DIRT\", \"DIRT\"]\n"
        + "essenceLevels = 1\n"
        + "durationTicks = 40\n"
        + "unlockAdaptation = \"golem-runic-augments\"\n"
        + "unlockLevel = 2\n"
        + "enabled = false\n";

    Map<String, InfusionRecipe> recipes = TomlCodec.fromToml(raw, Holder.class).infusionRecipes;

    assertThat(recipes).containsOnlyKeys("quick_order");
    InfusionRecipe recipe = recipes.get("quick_order");
    assertThat(recipe.getResult()).isEqualTo("golemancy:rune/order/1");
    assertThat(recipe.getCenter()).isEqualTo("golemancy:blank_rune");
    assertThat(recipe.getPedestals()).containsExactly("STONE", "STONE", "DIRT", "DIRT");
    assertThat(recipe.getEssenceLevels()).isEqualTo(1);
    assertThat(recipe.getDurationTicks()).isEqualTo(40);
    assertThat(recipe.getUnlockAdaptation()).isEqualTo("golem-runic-augments");
    assertThat(recipe.getUnlockLevel()).isEqualTo(2);
    assertThat(recipe.isEnabled()).isFalse();
  }

  public static class Holder {
    private Map<String, InfusionRecipe> infusionRecipes = InfusionRecipe.defaults();
  }
}
