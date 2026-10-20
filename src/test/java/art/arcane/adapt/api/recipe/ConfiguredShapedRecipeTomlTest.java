package art.arcane.adapt.api.recipe;

import art.arcane.adapt.util.config.ConfigFileSupport;
import art.arcane.adapt.util.config.TomlCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguredShapedRecipeTomlTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void recipeSpecsRoundTripThroughTomlConfigFiles() throws IOException {
    Path configPath = temporaryDirectory.resolve("recipes.toml");
    Holder defaults = new Holder();

    ConfigFileSupport.load(configPath.toFile(), null, Holder.class, defaults, true, "adaptation:test-recipes", "created");
    String raw = Files.readString(configPath);
    Holder loaded = ConfigFileSupport.load(configPath.toFile(), null, Holder.class, new Holder(), false, "adaptation:test-recipes", "unused");

    assertThat(raw)
        .contains("[crook]")
        .contains("rows = [\" A \", \"RHR\", \" C \"]")
        .contains("[crook.keys]")
        .contains("H = \"IRON_HOE\"")
        .contains("[recipes.node.keys]")
        .contains("\"#\" = \"#minecraft:planks\"");
    assertThat(loaded).usingRecursiveComparison().isEqualTo(defaults);
  }

  @Test
  void editedTomlValuesLoadIntoRecipeSpecs() throws IOException {
    String raw = "[crook]\n"
        + "rows = [\"#P\", \"P#\"]\n"
        + "result = \"golemancy:crook\"\n"
        + "amount = 2\n"
        + "enabled = false\n"
        + "\n"
        + "[crook.keys]\n"
        + "\"#\" = \"#planks\"\n"
        + "P = \"golemancy:blank_core\"\n";

    ConfiguredShapedRecipe crook = TomlCodec.fromToml(raw, Holder.class).crook;

    assertThat(crook.getRows()).containsExactly("#P", "P#");
    assertThat(crook.getKeys()).containsOnly(Map.entry("#", "#planks"), Map.entry("P", "golemancy:blank_core"));
    assertThat(crook.getResult()).isEqualTo("golemancy:crook");
    assertThat(crook.getAmount()).isEqualTo(2);
    assertThat(crook.isEnabled()).isFalse();
  }

  public static class Holder {
    private ConfiguredShapedRecipe crook = new ConfiguredShapedRecipe()
        .rows(" A ", "RHR", " C ")
        .key('H', "IRON_HOE")
        .key('A', "AMETHYST_SHARD")
        .key('R', "REDSTONE")
        .key('C', "COPPER_INGOT")
        .result("golemancy:artificers_crook", 1);
    private Map<String, ConfiguredShapedRecipe> recipes = new LinkedHashMap<>(Map.of("node", new ConfiguredShapedRecipe()
        .rows("#R#", "RAR", "#R#")
        .key('#', "#minecraft:planks")
        .key('R', "REDSTONE")
        .key('A', "AMETHYST_SHARD")
        .result("golemancy:blank_node", 4)));
  }
}
