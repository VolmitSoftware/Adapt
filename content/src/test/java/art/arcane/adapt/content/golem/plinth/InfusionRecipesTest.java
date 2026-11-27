package art.arcane.adapt.content.golem.plinth;

import art.arcane.adapt.content.golem.GolemRuneType;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class InfusionRecipesTest {
  private static final Map<String, List<List<String>>> RUNE_PEDESTALS = Map.of(
      "swiftness", List.of(List.of("SUGAR", "SUGAR", "FEATHER", "FEATHER"), List.of("RABBIT_FOOT", "RABBIT_FOOT", "PHANTOM_MEMBRANE", "PHANTOM_MEMBRANE"),
          List.of("BREEZE_ROD", "BREEZE_ROD", "BREEZE_ROD", "BREEZE_ROD")),
      "might", List.of(List.of("IRON_INGOT", "IRON_INGOT", "PISTON", "PISTON"), List.of("IRON_BLOCK", "IRON_BLOCK", "STICKY_PISTON", "STICKY_PISTON"),
          List.of("NETHERITE_SCRAP", "NETHERITE_SCRAP", "IRON_BLOCK", "IRON_BLOCK")),
      "ember", List.of(List.of("BLAZE_POWDER", "BLAZE_POWDER", "COAL", "COAL"), List.of("BLAZE_ROD", "BLAZE_ROD", "MAGMA_CREAM", "MAGMA_CREAM"),
          List.of("GHAST_TEAR", "GHAST_TEAR", "BLAZE_ROD", "BLAZE_ROD")),
      "tide", List.of(List.of("PRISMARINE_SHARD", "PRISMARINE_SHARD", "KELP", "KELP"),
          List.of("PRISMARINE_CRYSTALS", "PRISMARINE_CRYSTALS", "NAUTILUS_SHELL", "NAUTILUS_SHELL"),
          List.of("HEART_OF_THE_SEA", "PRISMARINE_CRYSTALS", "PRISMARINE_CRYSTALS", "PRISMARINE_CRYSTALS")),
      "order", List.of(List.of("QUARTZ", "QUARTZ", "REDSTONE", "REDSTONE"), List.of("COMPARATOR", "COMPARATOR", "QUARTZ_BLOCK", "QUARTZ_BLOCK"),
          List.of("ECHO_SHARD", "ECHO_SHARD", "COMPARATOR", "COMPARATOR")),
      "entropy", List.of(List.of("GUNPOWDER", "GUNPOWDER", "FERMENTED_SPIDER_EYE", "FERMENTED_SPIDER_EYE"),
          List.of("ENDER_EYE", "ENDER_EYE", "GUNPOWDER", "GUNPOWDER"), List.of("SHULKER_SHELL", "SHULKER_SHELL", "ENDER_EYE", "ENDER_EYE")),
      "bulwark", List.of(List.of("IRON_INGOT", "IRON_INGOT", "OBSIDIAN", "OBSIDIAN"), List.of("DIAMOND", "DIAMOND", "OBSIDIAN", "OBSIDIAN"),
          List.of("NETHERITE_INGOT", "CRYING_OBSIDIAN", "CRYING_OBSIDIAN", "CRYING_OBSIDIAN")));

  private static final Set<String> KNOWN_ITEMS = Set.of("STONE", "DIAMOND", "DIAMOND_BLOCK", "IRON_BLOCK", "COPPER_BLOCK", "SNOW_BLOCK",
      "HEART_OF_THE_SEA", "SCRAPE_POTTERY_SHERD");

  private final FakeResolver resolver = new FakeResolver();
  private final List<String> warnings = new ArrayList<>();

  @Test
  void theDefaultsFollowThePlanRuneTable() {
    Map<String, InfusionRecipe> defaults = InfusionRecipe.defaults();
    int[] essence = {2, 5, 10};
    int[] duration = {100, 160, 240};
    int[] unlock = {1, 3, 5};

    for (GolemRuneType aspect : GolemRuneType.values()) {
      for (int tier = 1; tier <= 3; tier++) {
        InfusionRecipe recipe = defaults.get(aspect.id() + "_" + tier);
        String label = aspect.id() + " tier " + tier;
        assertThat(recipe).as(label).isNotNull();
        assertThat(recipe.getResult()).as(label).isEqualTo("golemancy:rune/" + aspect.id() + "/" + tier);
        assertThat(recipe.getCenter()).as(label).isEqualTo(tier == 1 ? "golemancy:blank_rune" : "golemancy:rune/" + aspect.id() + "/" + (tier - 1));
        assertThat(recipe.getPedestals()).as(label).containsExactlyInAnyOrderElementsOf(RUNE_PEDESTALS.get(aspect.id()).get(tier - 1));
        assertThat(recipe.getEssenceLevels()).as(label).isEqualTo(essence[tier - 1]);
        assertThat(recipe.getDurationTicks()).as(label).isEqualTo(duration[tier - 1]);
        assertThat(recipe.getUnlockAdaptation()).as(label).isEqualTo(InfusionRecipe.RUNIC_AUGMENTS.toString());
        assertThat(recipe.getUnlockLevel()).as(label).isEqualTo(unlock[tier - 1]);
        assertThat(recipe.isEnabled()).as(label).isTrue();
      }
    }
  }

  @Test
  void theMasterworkHeartDefaultCostsFifteenLevelsAndTakesThreeHundredTicks() {
    InfusionRecipe heart = InfusionRecipe.defaults().get("masterwork_heart");

    assertThat(heart.getResult()).isEqualTo("golemancy:masterwork_heart");
    assertThat(heart.getCenter()).isEqualTo("HEART_OF_THE_SEA");
    assertThat(heart.getPedestals()).containsExactlyInAnyOrder("DIAMOND_BLOCK", "IRON_BLOCK", "COPPER_BLOCK", "SNOW_BLOCK");
    assertThat(heart.getEssenceLevels()).isEqualTo(15);
    assertThat(heart.getDurationTicks()).isEqualTo(300);
    assertThat(heart.getUnlockAdaptation()).isEqualTo(InfusionRecipe.MASTERWORK.toString());
    assertThat(heart.getUnlockLevel()).isEqualTo(1);
    assertThat(InfusionRecipe.defaults()).hasSize(22);
  }

  @Test
  void everyDefaultParses() {
    InfusionRecipes recipes = InfusionRecipes.parse(InfusionRecipe.defaults(), resolver, warnings::add);

    assertThat(warnings).isEmpty();
    for (Map.Entry<String, InfusionRecipe> entry : InfusionRecipe.defaults().entrySet()) {
      InfusionRecipe spec = entry.getValue();
      List<ItemRef> pedestals = spec.getPedestals().stream().map(name -> plain(Id.minecraft(name.toLowerCase(Locale.ROOT)))).toList();
      assertThat(recipes.match(specItem(spec.getCenter()), pedestals)).as(entry.getKey()).map(Infusion::id).contains(entry.getKey());
    }
    Infusion swiftness = recipes.match(resolver.exactItem("golemancy:blank_rune"),
        List.of(plain(Id.minecraft("sugar")), plain(Id.minecraft("sugar")), plain(Id.minecraft("feather")), plain(Id.minecraft("feather")))).orElseThrow();
    assertThat(swiftness.id()).isEqualTo("swiftness_1");
    assertThat(swiftness.result()).isEqualTo(Id.parse("golemancy:rune/swiftness/1"));
    assertThat(swiftness.resultItem().get()).isSameAs(resolver.result("golemancy:rune/swiftness/1"));
    assertThat(swiftness.essenceLevels()).isEqualTo(2);
    assertThat(swiftness.durationTicks()).isEqualTo(100);
    assertThat(swiftness.unlockAdaptation()).isEqualTo(InfusionRecipe.RUNIC_AUGMENTS.toString());
    assertThat(swiftness.unlockLevel()).isEqualTo(1);
  }

  @Test
  void pedestalItemsMatchInAnyOrderAroundTheExactCenter() {
    InfusionRecipes recipes = InfusionRecipes.parse(InfusionRecipe.defaults(), resolver, warnings::add);
    ItemRef blankRune = resolver.exactItem("golemancy:blank_rune");

    Optional<Infusion> match = recipes.match(blankRune, List.of(plain(Id.minecraft("feather")), plain(Id.minecraft("sugar")), plain(Id.minecraft("feather")), plain(Id.minecraft("sugar"))));

    assertThat(match).map(Infusion::id).contains("swiftness_1");
    assertThat(recipes.match(blankRune, List.of(plain(Id.minecraft("feather")), plain(Id.minecraft("sugar")), plain(Id.minecraft("sugar")), plain(Id.minecraft("sugar"))))).isEmpty();
    assertThat(recipes.match(plain(Id.minecraft("scrape_pottery_sherd")), List.of(plain(Id.minecraft("feather")), plain(Id.minecraft("sugar")), plain(Id.minecraft("feather")), plain(Id.minecraft("sugar")))))
        .isEmpty();
    assertThat(recipes.match(blankRune, Arrays.asList(plain(Id.minecraft("feather")), null, plain(Id.minecraft("feather")), plain(Id.minecraft("sugar"))))).isEmpty();
    assertThat(recipes.match(null, List.of(plain(Id.minecraft("feather")), plain(Id.minecraft("sugar")), plain(Id.minecraft("feather")), plain(Id.minecraft("sugar"))))).isEmpty();
  }

  @Test
  void higherTiersNeedTheRuneOfTheTierBelowOfTheSameAspect() {
    InfusionRecipes recipes = InfusionRecipes.parse(InfusionRecipe.defaults(), resolver, warnings::add);
    List<ItemRef> mightTwo = List.of(plain(Id.minecraft("iron_block")), plain(Id.minecraft("sticky_piston")), plain(Id.minecraft("sticky_piston")), plain(Id.minecraft("iron_block")));

    assertThat(recipes.match(resolver.exactItem("golemancy:rune/might/1"), mightTwo)).map(Infusion::id).contains("might_2");
    assertThat(recipes.match(resolver.exactItem("golemancy:rune/ember/1"), mightTwo)).isEmpty();
    assertThat(recipes.match(resolver.exactItem("golemancy:rune/might/2"), mightTwo)).isEmpty();
  }

  @Test
  void theMasterworkHeartNeedsAPlainHeartOfTheSea() {
    InfusionRecipes recipes = InfusionRecipes.parse(InfusionRecipe.defaults(), resolver, warnings::add);
    List<ItemRef> blocks = List.of(plain(Id.minecraft("snow_block")), plain(Id.minecraft("copper_block")), plain(Id.minecraft("iron_block")), plain(Id.minecraft("diamond_block")));

    assertThat(recipes.match(plain(Id.minecraft("heart_of_the_sea")), blocks)).map(Infusion::id).contains("masterwork_heart");
    assertThat(recipes.match(resolver.golem(Id.minecraft("heart_of_the_sea")), blocks)).isEmpty();
  }

  @Test
  void aRuneInfusionKnowsItsTierAndTheHeartHasNone() {
    InfusionRecipes recipes = InfusionRecipes.parse(InfusionRecipe.defaults(), resolver, warnings::add);
    ItemRef blankRune = resolver.exactItem("golemancy:blank_rune");
    List<ItemRef> sugarAndFeathers = List.of(plain(Id.minecraft("sugar")), plain(Id.minecraft("sugar")), plain(Id.minecraft("feather")), plain(Id.minecraft("feather")));
    List<ItemRef> blocks = List.of(plain(Id.minecraft("snow_block")), plain(Id.minecraft("copper_block")), plain(Id.minecraft("iron_block")), plain(Id.minecraft("diamond_block")));

    assertThat(recipes.match(blankRune, sugarAndFeathers).orElseThrow().runeTier()).isEqualTo(1);
    assertThat(recipes.match(resolver.exactItem("golemancy:rune/tide/2"),
        List.of(plain(Id.minecraft("heart_of_the_sea")), plain(Id.minecraft("prismarine_crystals")), plain(Id.minecraft("prismarine_crystals")), plain(Id.minecraft("prismarine_crystals"))))
        .orElseThrow().runeTier()).isEqualTo(3);
    assertThat(recipes.match(plain(Id.minecraft("heart_of_the_sea")), blocks).orElseThrow().runeTier()).isZero();
  }

  @Test
  void aLevelSpanCoversTheCostsOfTheInfusionsAFilterKeeps() {
    Map<String, InfusionRecipe> specs = InfusionRecipe.defaults();
    InfusionRecipes uniform = InfusionRecipes.parse(specs, resolver, warnings::add);

    assertThat(uniform.levelSpan(infusion -> infusion.runeTier() == 1)).contains(new LevelSpan(2, 2));
    assertThat(uniform.levelSpan(infusion -> infusion.runeTier() == 2)).contains(new LevelSpan(5, 5));
    assertThat(uniform.levelSpan(infusion -> infusion.runeTier() == 3)).contains(new LevelSpan(10, 10));
    assertThat(uniform.levelSpan(infusion -> infusion.unlockAdaptation().equals(InfusionRecipe.MASTERWORK.toString()))).contains(new LevelSpan(15, 15));
    assertThat(uniform.levelSpan(infusion -> infusion.runeTier() == 4)).isEmpty();
    assertThat(InfusionRecipes.EMPTY.levelSpan(infusion -> true)).isEmpty();

    specs.get("might_2").essence(8);
    specs.get("ember_2").essence(3);
    InfusionRecipes varied = InfusionRecipes.parse(specs, resolver, warnings::add);

    assertThat(varied.levelSpan(infusion -> infusion.runeTier() == 2)).contains(new LevelSpan(3, 8));
    assertThat(varied.levelSpan(infusion -> infusion.runeTier() == 1)).contains(new LevelSpan(2, 2));
  }

  @Test
  void aLevelSpanLabelIsOneNumberOrARange() {
    assertThat(new LevelSpan(5, 5).label()).isEqualTo("5");
    assertThat(new LevelSpan(3, 8).label()).isEqualTo("3-8");
  }

  @Test
  void theFirstMatchingRecipeWins() {
    Map<String, InfusionRecipe> specs = new LinkedHashMap<>();
    specs.put("first", recipe("golemancy:rune/order/1").pedestals("STONE", "STONE", "STONE", "STONE"));
    specs.put("second", recipe("golemancy:rune/tide/1").pedestals("STONE", "STONE", "STONE", "STONE"));

    InfusionRecipes recipes = InfusionRecipes.parse(specs, resolver, warnings::add);

    assertThat(recipes.match(resolver.exactItem("golemancy:blank_rune"), List.of(plain(Id.minecraft("stone")), plain(Id.minecraft("stone")), plain(Id.minecraft("stone")), plain(Id.minecraft("stone")))))
        .map(Infusion::id).contains("first");
  }

  @Test
  void overlappingRecipesResolveByIdWhateverOrderTheTableHolds() {
    Map<String, InfusionRecipe> specs = new LinkedHashMap<>();
    specs.put("zeta", recipe("golemancy:rune/tide/1").pedestals("STONE", "STONE", "STONE", "STONE"));
    specs.put("alpha", recipe("golemancy:rune/order/1").pedestals("STONE", "STONE", "STONE", "STONE"));

    InfusionRecipes recipes = InfusionRecipes.parse(specs, resolver, warnings::add);

    assertThat(recipes.match(resolver.exactItem("golemancy:blank_rune"), stones())).map(Infusion::id).contains("alpha");
  }

  @Test
  void invalidRecipesAreSkippedWithOneWarningEach() {
    Map<String, InfusionRecipe> specs = new LinkedHashMap<>();
    specs.put("no_result", recipe(""));
    specs.put("not_infusable", recipe("golemancy:crook"));
    specs.put("plain_result", recipe("DIAMOND"));
    specs.put("bad_center", recipe("golemancy:rune/order/1").center("NOT_A_THING"));
    specs.put("unknown_center", recipe("golemancy:rune/order/1").center("golemancy:nothing"));
    specs.put("three_pedestals", recipe("golemancy:rune/order/1").pedestals("STONE", "STONE", "STONE"));
    specs.put("bad_pedestal", recipe("golemancy:rune/order/1").pedestals("STONE", "STONE", "STONE", "NOT_A_THING"));
    specs.put("no_unlock", recipe("golemancy:rune/order/1").unlock(" ", 1));
    specs.put("valid", recipe("golemancy:rune/order/1"));

    InfusionRecipes recipes = InfusionRecipes.parse(specs, resolver, warnings::add);

    assertThat(recipes.match(resolver.exactItem("golemancy:blank_rune"), stones())).map(Infusion::id).contains("valid");
    assertThat(warnings).hasSize(8);
    List<String> skipped = List.of("bad_center", "bad_pedestal", "no_result", "no_unlock", "not_infusable", "plain_result", "three_pedestals", "unknown_center");
    for (int index = 0; index < skipped.size(); index++) {
      assertThat(warnings.get(index)).contains(skipped.get(index));
    }
  }

  @Test
  void numbersAreClampedIntoRange() {
    Map<String, InfusionRecipe> low = Map.of("low", recipe("golemancy:rune/order/1").essence(-3).duration(5).unlock(InfusionRecipe.RUNIC_AUGMENTS.toString(), 0));
    Map<String, InfusionRecipe> high = Map.of("high", recipe("golemancy:rune/order/2").duration(1_000_000));
    ItemRef blankRune = resolver.exactItem("golemancy:blank_rune");

    Infusion clampedLow = InfusionRecipes.parse(low, resolver, warnings::add).match(blankRune, stones()).orElseThrow();
    Infusion clampedHigh = InfusionRecipes.parse(high, resolver, warnings::add).match(blankRune, stones()).orElseThrow();

    assertThat(warnings).isEmpty();
    assertThat(clampedLow.essenceLevels()).isZero();
    assertThat(clampedLow.durationTicks()).isEqualTo(InfusionRecipes.MIN_DURATION_TICKS);
    assertThat(clampedLow.unlockLevel()).isEqualTo(1);
    assertThat(clampedHigh.durationTicks()).isEqualTo(InfusionRecipes.MAX_DURATION_TICKS);
  }

  @Test
  void disabledRecipesAreSkippedSilentlyAndAMissingTableIsEmpty() {
    Map<String, InfusionRecipe> specs = new LinkedHashMap<>();
    specs.put("off", recipe("golemancy:rune/order/1").enabled(false));

    assertThat(InfusionRecipes.parse(specs, resolver, warnings::add).match(resolver.exactItem("golemancy:blank_rune"), stones())).isEmpty();
    assertThat(InfusionRecipes.parse(null, resolver, warnings::add)).isSameAs(InfusionRecipes.EMPTY);
    assertThat(warnings).isEmpty();
    assertThat(InfusionRecipes.EMPTY.match(plain(Id.minecraft("stone")), List.of())).isEmpty();
  }

  @Test
  void specsAcceptLowercaseNamesAndIds() {
    Map<String, InfusionRecipe> specs = new LinkedHashMap<>();
    specs.put("lower", recipe("GOLEMANCY:RUNE/ORDER/1").center("Golemancy:Blank_Rune").pedestals("stone", "Stone", "STONE", " stone "));

    InfusionRecipes recipes = InfusionRecipes.parse(specs, resolver, warnings::add);

    assertThat(warnings).isEmpty();
    assertThat(recipes.match(resolver.exactItem("golemancy:blank_rune"), stones())).map(Infusion::result)
        .contains(Id.parse("golemancy:rune/order/1"));
  }

  private static InfusionRecipe recipe(String result) {
    return new InfusionRecipe()
        .result(result)
        .center("golemancy:blank_rune")
        .pedestals("STONE", "STONE", "STONE", "STONE")
        .essence(2)
        .duration(100)
        .unlock(InfusionRecipe.RUNIC_AUGMENTS.toString(), 1);
  }

  private ItemRef plain(Id material) {
    return resolver.plain(material);
  }

  private ItemRef specItem(String spec) {
    return spec.indexOf(':') >= 0 ? resolver.exactItem(spec) : plain(Id.minecraft(spec.toLowerCase(Locale.ROOT)));
  }

  private List<ItemRef> stones() {
    return List.of(plain(Id.minecraft("stone")), plain(Id.minecraft("stone")), plain(Id.minecraft("stone")), plain(Id.minecraft("stone")));
  }

  private static boolean knownItem(String name) {
    String upper = name.toUpperCase(Locale.ROOT);
    if (KNOWN_ITEMS.contains(upper)) {
      return true;
    }
    for (List<List<String>> tiers : RUNE_PEDESTALS.values()) {
      for (List<String> pedestals : tiers) {
        if (pedestals.contains(upper)) {
          return true;
        }
      }
    }
    return false;
  }

  private static final class FakeResolver implements InfusionRecipes.Resolver {
    private final Map<ItemRef, String> exactItems = new HashMap<>();
    private final Map<String, ItemRef> exactById = new HashMap<>();
    private final Map<String, ItemRef> results = new HashMap<>();

    @Override
    public InfusionIngredient material(String name) {
      if (!knownItem(name)) {
        return null;
      }
      Id material = Id.minecraft(name.toLowerCase(Locale.ROOT));
      return item -> item != null && item.type().equals(material) && !exactItems.containsKey(item);
    }

    @Override
    public InfusionIngredient exact(Id id) {
      String key = id.toString();
      if (!key.equals("golemancy:blank_rune") && !key.startsWith("golemancy:rune/")) {
        return null;
      }
      return item -> key.equals(exactItems.get(item));
    }

    @Override
    public Supplier<ItemRef> result(Id id) {
      String key = id.toString();
      if (!key.equals("golemancy:masterwork_heart") && !key.startsWith("golemancy:rune/")) {
        return null;
      }
      return () -> result(key);
    }

    ItemRef result(String key) {
      return results.computeIfAbsent(key, ignored -> mock(ItemRef.class));
    }

    ItemRef exactItem(String id) {
      return exactById.computeIfAbsent(id, key -> {
        ItemRef item = mock(ItemRef.class);
        lenient().when(item.type()).thenReturn(Id.minecraft("scrape_pottery_sherd"));
        exactItems.put(item, key);
        return item;
      });
    }

    ItemRef golem(Id material) {
      ItemRef item = mock(ItemRef.class);
      lenient().when(item.type()).thenReturn(material);
      exactItems.put(item, "golemancy:masterwork_heart");
      return item;
    }

    ItemRef plain(Id material) {
      ItemRef item = mock(ItemRef.class);
      lenient().when(item.type()).thenReturn(material);
      return item;
    }
  }
}
