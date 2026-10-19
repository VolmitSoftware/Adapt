package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.item.GolemItemLooks;
import art.arcane.adapt.api.recipe.ConfiguredShapedRecipe;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GolemancyMediaCatalogTest extends AdaptTestBase {
  private static final Path MATRIX = Path.of("src", "test", "gameplay", "adaptation-matrix.json");
  private static final Gson GSON = new Gson();

  private JsonObject catalog;

  @BeforeEach
  void buildCatalog() {
    catalog = GSON.fromJson(json(), JsonObject.class);
  }

  @Test
  void everyCraftingRecipeOfTheAdaptationConfigsIsExported() throws IllegalAccessException {
    List<GolemancyMediaCatalog.AdaptationSource> adaptations = GolemancyMediaCatalog.adaptationSources();
    List<ConfiguredShapedRecipe> declared = new ArrayList<>();
    for (GolemancyMediaCatalog.AdaptationSource adaptation : adaptations) {
      for (Field field : adaptation.config().getClass().getDeclaredFields()) {
        if (field.getType() == ConfiguredShapedRecipe.class) {
          field.setAccessible(true);
          declared.add((ConfiguredShapedRecipe) field.get(adaptation.config()));
        }
      }
    }
    List<ConfiguredShapedRecipe> exported = new ArrayList<>();
    for (GolemancyMediaCatalog.RecipeSource source : GolemancyMediaCatalog.recipeSources(adaptations)) {
      exported.add(source.spec());
    }

    assertThat(exported).hasSize(22).containsExactlyInAnyOrderElementsOf(declared);
    assertThat(catalog.getAsJsonArray("recipes")).hasSize(22);
  }

  @Test
  void recipesCarryTheDefaultShapeKeysYieldAndTithe() {
    JsonObject crook = byId(catalog.getAsJsonArray("recipes"), "golemancy-crook");

    assertThat(strings(crook.getAsJsonArray("pattern"))).containsExactly(" A ", "RHR", " C ");
    assertThat(crook.getAsJsonObject("key").get("H").getAsString()).isEqualTo("IRON_HOE");
    assertThat(crook.get("result").getAsString()).isEqualTo("golemancy:crook");
    assertThat(crook.get("yield").getAsInt()).isEqualTo(1);
    assertThat(crook.get("tithe").getAsInt()).isZero();
    JsonObject blankNode = byId(catalog.getAsJsonArray("recipes"), "golemancy-blank-node");
    assertThat(blankNode.get("yield").getAsInt()).isEqualTo(4);
    JsonObject guardian = byId(catalog.getAsJsonArray("recipes"), "golemancy-guardian-core");
    assertThat(guardian.get("tithe").getAsInt()).isEqualTo(2);
    assertThat(guardian.getAsJsonObject("key").get("B").getAsString()).isEqualTo("golemancy:blank_core");
  }

  @Test
  void everyRecipePatternIsAGridWhoseSymbolsAreKeyed() {
    for (JsonElement element : catalog.getAsJsonArray("recipes")) {
      JsonObject recipe = element.getAsJsonObject();
      List<String> rows = strings(recipe.getAsJsonArray("pattern"));
      Set<String> keys = new HashSet<>();
      recipe.getAsJsonObject("key").entrySet().forEach(entry -> keys.add(entry.getKey()));

      assertThat(rows).describedAs(recipe.get("id").getAsString()).isNotEmpty().hasSizeLessThanOrEqualTo(3);
      assertThat(rows.stream().map(String::length).distinct()).describedAs(recipe.get("id").getAsString()).hasSize(1);
      for (String row : rows) {
        for (char symbol : row.toCharArray()) {
          assertThat(symbol == ' ' || keys.contains(String.valueOf(symbol))).describedAs("%s symbol '%s'", recipe.get("id"), symbol).isTrue();
        }
      }
    }
  }

  @Test
  void customItemsResolveToTheirBaseMaterialNameAndCoreTint() {
    JsonObject items = catalog.getAsJsonObject("items");

    assertThat(items.getAsJsonObject("golemancy:crook").get("base").getAsString()).isEqualTo("IRON_HOE");
    assertThat(items.getAsJsonObject("golemancy:crook").get("name").getAsString()).isEqualTo("Artificer's Crook");
    assertThat(items.getAsJsonObject("golemancy:blank_core").get("base").getAsString()).isEqualTo("FIREWORK_STAR");
    assertThat(items.getAsJsonObject("golemancy:blank_core").get("tint").getAsString()).isEqualTo("#808080");
    assertThat(items.getAsJsonObject("golemancy:core/guardian").get("tint").getAsString()).isEqualTo("#3C6FD1");
    assertThat(items.getAsJsonObject("golemancy:core/guardian").get("name").getAsString()).isEqualTo("Guardian Core");
    assertThat(items.getAsJsonObject("golemancy:blank_node").get("base").getAsString()).isEqualTo("CLAY_BALL");
    assertThat(items.getAsJsonObject("golemancy:node/guard").get("base").getAsString()).isEqualTo("BRICK");
    assertThat(items.getAsJsonObject("golemancy:blank_rune").get("base").getAsString()).isEqualTo("DISC_FRAGMENT_5");
    assertThat(items.getAsJsonObject("golemancy:masterwork_heart").get("base").getAsString()).isEqualTo("HEART_OF_THE_SEA");
    assertThat(items.getAsJsonObject("golemancy:node/guard").get("tint").isJsonNull()).isTrue();
  }

  @Test
  void everyCustomItemANamedRecipeMentionsHasAnItemEntry() {
    JsonObject items = catalog.getAsJsonObject("items");
    List<String> mentioned = new ArrayList<>();
    for (JsonElement element : catalog.getAsJsonArray("recipes")) {
      JsonObject recipe = element.getAsJsonObject();
      mentioned.add(recipe.get("result").getAsString());
      recipe.getAsJsonObject("key").entrySet().forEach(entry -> mentioned.add(entry.getValue().getAsString()));
    }
    for (JsonElement element : catalog.getAsJsonArray("infusions")) {
      JsonObject infusion = element.getAsJsonObject();
      mentioned.add(infusion.get("result").getAsString());
      mentioned.add(infusion.get("center").getAsString());
      infusion.getAsJsonArray("pedestals").forEach(value -> mentioned.add(value.getAsString()));
    }

    for (String item : mentioned) {
      if (item.startsWith("golemancy:")) {
        assertThat(items.has(item)).describedAs(item).isTrue();
      }
    }
  }

  @Test
  void infusionsExportEveryDefaultRuneTierAndTheMasterworkHeart() {
    JsonArray infusions = catalog.getAsJsonArray("infusions");

    assertThat(infusions).hasSize(22);
    JsonObject swiftness = byId(infusions, "swiftness_2");
    assertThat(swiftness.get("center").getAsString()).isEqualTo("golemancy:rune/swiftness/1");
    assertThat(strings(swiftness.getAsJsonArray("pedestals"))).containsExactly("RABBIT_FOOT", "RABBIT_FOOT", "PHANTOM_MEMBRANE", "PHANTOM_MEMBRANE");
    assertThat(swiftness.get("result").getAsString()).isEqualTo("golemancy:rune/swiftness/2");
    assertThat(swiftness.get("essenceLevels").getAsInt()).isEqualTo(5);
    assertThat(swiftness.get("durationTicks").getAsInt()).isEqualTo(160);
    JsonObject heart = byId(infusions, "masterwork_heart");
    assertThat(heart.get("center").getAsString()).isEqualTo("HEART_OF_THE_SEA");
    assertThat(strings(heart.getAsJsonArray("pedestals"))).containsExactly("DIAMOND_BLOCK", "IRON_BLOCK", "COPPER_BLOCK", "SNOW_BLOCK");
    assertThat(heart.get("essenceLevels").getAsInt()).isEqualTo(15);
    assertThat(heart.get("durationTicks").getAsInt()).isEqualTo(300);
    for (JsonElement element : infusions) {
      assertThat(element.getAsJsonObject().getAsJsonArray("pedestals")).hasSize(4);
    }
    JsonObject items = catalog.getAsJsonObject("items");
    assertThat(items.getAsJsonObject("golemancy:rune/swiftness/2").get("tier").getAsInt()).isEqualTo(2);
    assertThat(items.getAsJsonObject("golemancy:rune/swiftness/2").get("name").getAsString()).isEqualTo("Swiftness Rune");
  }

  @Test
  void adaptationsMatchTheMatrixAndCarryTheirDefaultRequirements() throws IOException {
    JsonArray adaptations = catalog.getAsJsonArray("adaptations");
    List<String> expected = new ArrayList<>();
    for (JsonElement element : GSON.fromJson(Files.readString(MATRIX), JsonObject.class).getAsJsonArray("adaptations")) {
      JsonObject entry = element.getAsJsonObject();
      if ("golemancy".equals(entry.get("skill").getAsString())) {
        expected.add(entry.get("name").getAsString());
      }
    }
    List<String> exported = new ArrayList<>();
    adaptations.forEach(element -> exported.add(element.getAsJsonObject().get("id").getAsString()));

    assertThat(exported).hasSize(19).containsExactlyInAnyOrderElementsOf(expected);
    assertThat(byId(adaptations, "golem-animus-binding").getAsJsonArray("requires")).isEmpty();
    assertThat(byId(adaptations, "golem-animus-binding").get("name").getAsString()).isEqualTo("Animus Binding");
    JsonObject weaving = byId(adaptations, "golem-node-weaving");
    assertThat(weaving.getAsJsonArray("requires")).hasSize(1);
    assertThat(weaving.getAsJsonArray("requires").get(0).getAsJsonObject().get("id").getAsString()).isEqualTo("golem-animus-binding");
    assertThat(weaving.getAsJsonArray("requires").get(0).getAsJsonObject().get("level").getAsInt()).isEqualTo(1);
    List<String> masterwork = new ArrayList<>();
    byId(adaptations, "golem-masterwork").getAsJsonArray("requires").forEach(element -> masterwork.add(element.getAsJsonObject().get("id").getAsString()));
    assertThat(masterwork).containsExactly("golem-runic-augments", "golem-animus-binding");
  }

  @Test
  void theSameDefaultsGiveTheSameBytes() {
    assertThat(json()).isEqualTo(json());
  }

  private static String json() {
    try (GolemItemLooks looks = GolemItemLooks.open()) {
      return GolemancyMediaCatalog.toJson(GolemancyMediaCatalog.build(looks));
    }
  }

  private static JsonObject byId(JsonArray entries, String id) {
    for (JsonElement element : entries) {
      if (id.equals(element.getAsJsonObject().get("id").getAsString())) {
        return element.getAsJsonObject();
      }
    }
    throw new AssertionError("No entry " + id);
  }

  private static List<String> strings(JsonArray array) {
    List<String> values = new ArrayList<>(array.size());
    array.forEach(element -> values.add(element.getAsString()));
    return values;
  }
}
