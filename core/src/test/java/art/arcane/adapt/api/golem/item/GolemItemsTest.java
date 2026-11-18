package art.arcane.adapt.api.golem.item;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.plinth.Infusion;
import art.arcane.adapt.api.golem.plinth.InfusionRecipe;
import art.arcane.adapt.api.golem.plinth.InfusionRecipes;
import art.arcane.adapt.api.recipe.ExactIngredient;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.TooltipPart;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.util.common.misc.CustomModel;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GolemItemsTest extends AdaptTestBase {
  private static final UUID GOLEM = UUID.fromString("00000000-0000-0000-0000-00000000a001");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000b002");
  private static final DataKey<String> CROOK_CHANNEL_KEY = DataKey.of(Id.of("adapt", "crook_channel"), DataCodecs.STRING);
  private static final PackedGolem PACKED = new PackedGolem(GOLEM, OWNER, "{\"body\":\"IRON\"}");
  private static final Id BLANK_RUNE_BASE = Id.minecraft("scrape_pottery_sherd");
  private static final Map<GolemRuneType, Id> RUNE_BASES = new EnumMap<>(Map.of(
      GolemRuneType.SWIFTNESS, Id.minecraft("guster_pottery_sherd"),
      GolemRuneType.MIGHT, Id.minecraft("arms_up_pottery_sherd"),
      GolemRuneType.EMBER, Id.minecraft("burn_pottery_sherd"),
      GolemRuneType.TIDE, Id.minecraft("angler_pottery_sherd"),
      GolemRuneType.ORDER, Id.minecraft("sheaf_pottery_sherd"),
      GolemRuneType.ENTROPY, Id.minecraft("danger_pottery_sherd"),
      GolemRuneType.BULWARK, Id.minecraft("shelter_pottery_sherd")));

  private static final Rgb GRAY = Rgb.of(0x808080);
  private static final Rgb BLACK = Rgb.of(0x000000);
  private final List<List<String>> modelPaths = new ArrayList<>();
  private final Map<List<String>, Id> materialOverrides = new HashMap<>();
  private MockedStatic<CustomModel> models;

  @BeforeEach
  void installItemFactory() {
    models = mockStatic(CustomModel.class);
    models.when(() -> CustomModel.get(any(Id.class), any(String[].class)))
        .thenAnswer(call -> model(call.getArgument(0), (String[]) call.getRawArguments()[1]));
  }

  @AfterEach
  void removeItemFactory() {
    models.close();
  }

  @Test
  void crookIsAnUnbreakableSingleStackHoeWithHiddenAttributes() {
    ItemRef crook = GolemItems.crook();

    assertThat(crook.type()).isEqualTo(Id.minecraft("iron_hoe"));
    assertThat(GolemItems.kindOf(crook)).contains(GolemItemKind.CROOK);
    FakeItem meta = (FakeItem) crook;
    assertThat(meta.isUnbreakable()).isTrue();
    assertThat(meta.hiddenTooltip()).contains(TooltipPart.ATTRIBUTES, TooltipPart.UNBREAKABLE);
    assertThat(meta.maxStackSize()).isEqualTo(1);
    assertThat(meta.hasGlint()).isTrue();
    assertThat(meta.displayName()).contains("Artificer's Crook");
    assertThat(GolemItemStacks.stored(crook, GolemItemStacks.KIND_KEY)).isEqualTo("crook");
  }

  @Test
  void coresRoundTripTheirRoleAsFireworkStars() {
    for (GolemCoreType role : GolemCoreType.values()) {
      ItemRef core = GolemItems.core(role);

      assertThat(core.type()).isEqualTo(Id.minecraft("firework_star"));
      assertThat(GolemItems.kindOf(core)).contains(role == GolemCoreType.BLANK ? GolemItemKind.BLANK_CORE : GolemItemKind.CORE);
      assertThat(GolemItems.coreOf(core)).contains(role);
      assertThat(core.maxStackSize()).isEqualTo(16);
    }
    assertThat(GolemItems.kindOf(GolemItems.blankCore())).contains(GolemItemKind.BLANK_CORE);
  }

  @Test
  void coreTintsAreGrayForBlankBlackForShatteredAndDistinctPerRole() {
    Set<Rgb> roleTints = new HashSet<>();
    for (GolemCoreType role : GolemCoreType.values()) {
      if (role != GolemCoreType.BLANK) {
        roleTints.add(tint(GolemItems.core(role)));
      }
    }

    assertThat(tint(GolemItems.blankCore())).isEqualTo(GRAY);
    assertThat(tint(GolemItems.shatteredCore(PACKED))).isEqualTo(BLACK);
    assertThat(roleTints).hasSize(GolemCoreType.values().length - 1).doesNotContain(GRAY, BLACK);
  }

  @Test
  void nodesAreClayWhenBlankAndBrickWhenTyped() {
    ItemRef blank = GolemItems.blankNode();

    assertThat(blank.type()).isEqualTo(Id.minecraft("clay_ball"));
    assertThat(GolemItems.kindOf(blank)).contains(GolemItemKind.BLANK_NODE);
    assertThat(GolemItems.nodeTypeOf(blank)).isEmpty();
    for (NodeType type : NodeType.values()) {
      ItemRef node = GolemItems.node(type);

      assertThat(node.type()).isEqualTo(Id.minecraft("brick"));
      assertThat(GolemItems.kindOf(node)).contains(GolemItemKind.NODE);
      assertThat(GolemItems.nodeTypeOf(node)).contains(type);
    }
  }

  @Test
  void runesRoundTripTheirAspectAndTier() {
    ItemRef blank = GolemItems.blankRune();

    assertThat(blank.type()).isEqualTo(BLANK_RUNE_BASE);
    assertThat(GolemItems.kindOf(blank)).contains(GolemItemKind.BLANK_RUNE);
    assertThat(GolemItems.runeOf(blank)).isEmpty();
    for (GolemRuneType aspect : GolemRuneType.values()) {
      for (int tier = GolemRuneType.MIN_TIER; tier <= GolemRuneType.MAX_TIER; tier++) {
        GolemRune expected = new GolemRune(aspect, tier);
        ItemRef rune = GolemItems.rune(expected);

        assertThat(rune.type()).describedAs("%s tier %d base", aspect, tier).isEqualTo(RUNE_BASES.get(aspect));
        assertThat(GolemItems.kindOf(rune)).contains(GolemItemKind.RUNE);
        assertThat(GolemItems.runeOf(rune)).contains(expected);
      }
    }
  }

  @Test
  void everyRuneAspectAndTheBlankRuneBuildOnTheirOwnPotterySherd() {
    Set<Id> bases = new HashSet<>(RUNE_BASES.values());
    bases.add(BLANK_RUNE_BASE);

    assertThat(RUNE_BASES).containsOnlyKeys(GolemRuneType.values());
    assertThat(bases).hasSize(GolemRuneType.values().length + 1).allSatisfy(base -> assertThat(base.path()).endsWith("_pottery_sherd"));
    assertThat(bases).doesNotContain(Id.minecraft("disc_fragment_5"), Id.minecraft("brick"), Id.minecraft("clay_ball"));
  }

  @Test
  void runeOfRecognisesEveryAspectOnItsSherd() {
    for (GolemRuneType aspect : GolemRuneType.values()) {
      for (int tier = GolemRuneType.MIN_TIER; tier <= GolemRuneType.MAX_TIER; tier++) {
        ItemRef tagged = GolemItemStacks.tagged(RUNE_BASES.get(aspect), "rune", "{\"aspect\":\"" + aspect.id() + "\",\"tier\":" + tier + "}");

        assertThat(GolemItems.runeOf(tagged)).describedAs("%s tier %d on %s", aspect, tier, tagged.type()).contains(new GolemRune(aspect, tier));
      }
    }
    assertThat(GolemItems.runeOf(GolemItemStacks.plain(Id.minecraft("guster_pottery_sherd")))).isEmpty();
    assertThat(GolemItems.runeOf(GolemItemStacks.tagged(BLANK_RUNE_BASE, "blank_rune", null))).isEmpty();
  }

  @Test
  void runeTiersOutsideOneToThreeAreRejected() {
    assertThatThrownBy(() -> new GolemRune(GolemRuneType.MIGHT, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GolemRune(GolemRuneType.MIGHT, 4)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void dormantGolemsKeepTheirBlockVariantAndPackedGolem() {
    for (Id block : List.of(Id.minecraft("snow_block"), Id.minecraft("iron_block"), Id.minecraft("copper_block"), Id.minecraft("waxed_oxidized_copper"))) {
      ItemRef dormant = GolemItems.dormantGolem(dormant(block));

      assertThat(dormant.type()).isEqualTo(block);
      assertThat(GolemItems.kindOf(dormant)).contains(GolemItemKind.DORMANT_GOLEM);
      assertThat(GolemItems.packedGolemOf(dormant)).contains(PACKED);
      assertThat(dormant.maxStackSize()).isEqualTo(1);
    }
    assertThat(modelPaths).containsExactly(
        List.of("golemancy", "dormant_golem", "snow_block"),
        List.of("golemancy", "dormant_golem", "iron_block"),
        List.of("golemancy", "dormant_golem", "copper_block"),
        List.of("golemancy", "dormant_golem", "waxed_oxidized_copper"));
  }

  @Test
  void dormantGolemLoreListsCoresRunesAndHealth() {
    ItemRef dormant = GolemItems.dormantGolem(dormant(Id.minecraft("iron_block")));

    assertThat(String.join("\n", dormant.lore()))
        .contains("Core: Guardian")
        .contains("Core: Gatherer")
        .contains("Runes: Might ◆◆◇, Tide ◆◇◇")
        .contains("Health: 80/100");
  }

  @Test
  void dormantGolemsRejectBlocksOutsideTheBodyMaterials() {
    assertThatThrownBy(() -> new DormantGolem(PACKED, Id.minecraft("gold_block"), List.of(), List.of(), 1D, 1D))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void copperBlocksMatchOxidationStageAndWax() {
    assertThat(GolemItems.copperBlock(0, false)).isEqualTo(Id.minecraft("copper_block"));
    assertThat(GolemItems.copperBlock(1, false)).isEqualTo(Id.minecraft("exposed_copper"));
    assertThat(GolemItems.copperBlock(2, false)).isEqualTo(Id.minecraft("weathered_copper"));
    assertThat(GolemItems.copperBlock(3, false)).isEqualTo(Id.minecraft("oxidized_copper"));
    assertThat(GolemItems.copperBlock(0, true)).isEqualTo(Id.minecraft("waxed_copper_block"));
    assertThat(GolemItems.copperBlock(1, true)).isEqualTo(Id.minecraft("waxed_exposed_copper"));
    assertThat(GolemItems.copperBlock(2, true)).isEqualTo(Id.minecraft("waxed_weathered_copper"));
    assertThat(GolemItems.copperBlock(3, true)).isEqualTo(Id.minecraft("waxed_oxidized_copper"));
    assertThatThrownBy(() -> GolemItems.copperBlock(4, false)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void dormantBlocksFollowTheBodyAndTheCopperStage() {
    assertThat(GolemItems.dormantBlock(GolemBody.FROST, 2, true)).isEqualTo(Id.minecraft("snow_block"));
    assertThat(GolemItems.dormantBlock(GolemBody.IRON, 0, false)).isEqualTo(Id.minecraft("iron_block"));
    assertThat(GolemItems.dormantBlock(GolemBody.COPPER, 0, false)).isEqualTo(Id.minecraft("copper_block"));
    assertThat(GolemItems.dormantBlock(GolemBody.COPPER, 2, true)).isEqualTo(Id.minecraft("waxed_weathered_copper"));
    assertThat(GolemItems.dormantBlock(GolemBody.COPPER, 9, false)).isEqualTo(Id.minecraft("oxidized_copper"));
    assertThat(GolemItems.dormantBlock(GolemBody.BONE, 0, false)).isEqualTo(Id.minecraft("bone_block"));
  }

  @Test
  void shatteredCoresAndMasterworkHeartsRoundTrip() {
    ItemRef shattered = GolemItems.shatteredCore(PACKED);
    ItemRef heart = GolemItems.masterworkHeart();

    assertThat(shattered.type()).isEqualTo(Id.minecraft("firework_star"));
    assertThat(GolemItems.kindOf(shattered)).contains(GolemItemKind.SHATTERED_CORE);
    assertThat(GolemItems.packedGolemOf(shattered)).contains(PACKED);
    assertThat(GolemItems.coreOf(shattered)).isEmpty();
    assertThat(heart.type()).isEqualTo(Id.minecraft("heart_of_the_sea"));
    assertThat(GolemItems.kindOf(heart)).contains(GolemItemKind.MASTERWORK_HEART);
    assertThat(GolemItems.packedGolemOf(heart)).isEmpty();
  }

  @Test
  void everyItemUsesItsKindModelPathAndGlint() {
    List<ItemRef> items = everyItem();

    List<List<String>> expected = new ArrayList<>();
    for (GolemItemKind kind : GolemItemKind.values()) {
      expected.add(switch (kind) {
        case DORMANT_GOLEM -> List.of("golemancy", kind.id(), "snow_block");
        case BLANK_RUNE -> List.of("golemancy", "rune", "blank");
        case RUNE -> List.of("golemancy", "rune", "order");
        default -> List.of("golemancy", kind.id());
      });
    }
    assertThat(modelPaths).containsExactlyElementsOf(expected);
    for (ItemRef item : items) {
      assertThat(item.hasGlint()).isTrue();
    }
  }

  @Test
  void everyItemHidesTheVanillaAdditionalTooltip() {
    for (ItemRef item : everyItem()) {
      assertThat(((FakeItem) item).hiddenTooltip())
          .describedAs("%s tooltip flags", item.type())
          .contains(TooltipPart.ADDITIONAL);
    }
  }

  @Test
  void everyRuneOnItsSherdHidesTheVanillaAdditionalTooltip() {
    List<ItemRef> runes = new ArrayList<>();
    runes.add(GolemItems.blankRune());
    for (GolemRuneType aspect : GolemRuneType.values()) {
      for (int tier = GolemRuneType.MIN_TIER; tier <= GolemRuneType.MAX_TIER; tier++) {
        runes.add(GolemItems.rune(new GolemRune(aspect, tier)));
      }
    }

    for (ItemRef rune : runes) {
      FakeItem meta = (FakeItem) rune;

      assertThat(rune.type()).isNotEqualTo(Id.minecraft("disc_fragment_5"));
      assertThat(meta.hiddenTooltip()).describedAs("%s tooltip flags", rune.type()).contains(TooltipPart.ADDITIONAL);
      assertThat(meta.hasDisplayName()).isTrue();
    }
  }

  @Test
  void hidingTheVanillaTooltipKeepsTheItemsOwnNameAndLore() {
    for (ItemRef item : everyItem()) {
      FakeItem meta = (FakeItem) item;

      assertThat(meta.hiddenTooltip()).isSubsetOf(TooltipPart.ADDITIONAL, TooltipPart.ATTRIBUTES, TooltipPart.UNBREAKABLE);
      assertThat(meta.hasDisplayName()).isTrue();
      assertThat(meta.lore()).isNotEmpty();
    }
  }

  @Test
  void tagsUseTheExplicitAdaptKeysWithAGsonPayload() {
    ItemRef core = GolemItems.core(GolemCoreType.GUARDIAN);
    ItemRef crook = GolemItems.crook();

    assertThat(GolemItemStacks.stored(core, GolemItemStacks.KIND_KEY)).isEqualTo("core");
    JsonObject payload = new Gson().fromJson(GolemItemStacks.stored(core, GolemItemStacks.DATA_KEY), JsonObject.class);
    assertThat(payload.get("role").getAsString()).isEqualTo("guardian");
    assertThat(GolemItemStacks.stored(crook, GolemItemStacks.KIND_KEY)).isEqualTo("crook");
    assertThat(GolemItemStacks.stored(crook, GolemItemStacks.DATA_KEY)).isNull();
  }

  @Test
  void resolverBuildsEveryDocumentedGolemancyId() {
    assertThat(GolemItems.kindOf(resolve("golemancy:crook"))).contains(GolemItemKind.CROOK);
    assertThat(GolemItems.kindOf(resolve("golemancy:blank_core"))).contains(GolemItemKind.BLANK_CORE);
    assertThat(GolemItems.kindOf(resolve("golemancy:blank_node"))).contains(GolemItemKind.BLANK_NODE);
    assertThat(GolemItems.kindOf(resolve("golemancy:blank_rune"))).contains(GolemItemKind.BLANK_RUNE);
    for (GolemCoreType role : GolemCoreType.values()) {
      if (role != GolemCoreType.BLANK) {
        assertThat(GolemItems.coreOf(resolve("golemancy:core/" + role.id()))).contains(role);
      }
    }
    for (NodeType type : NodeType.values()) {
      assertThat(GolemItems.nodeTypeOf(resolve("golemancy:node/" + type.id()))).contains(type);
    }
    for (GolemRuneType aspect : GolemRuneType.values()) {
      for (int tier = GolemRuneType.MIN_TIER; tier <= GolemRuneType.MAX_TIER; tier++) {
        assertThat(GolemItems.runeOf(resolve("golemancy:rune/" + aspect.id() + "/" + tier))).contains(new GolemRune(aspect, tier));
      }
    }
  }

  @Test
  void resolverReturnsNullForUnknownIds() {
    for (String id : List.of(
        "minecraft:crook",
        "golemancy:core",
        "golemancy:core/blank",
        "golemancy:core/unknown",
        "golemancy:crook/extra",
        "golemancy:node",
        "golemancy:node/guard/extra",
        "golemancy:rune/might",
        "golemancy:rune/might/0",
        "golemancy:rune/might/4",
        "golemancy:rune/might/x",
        "golemancy:dormant_golem",
        "golemancy:shattered_core",
        "golemancy:masterwork_heart")) {
      assertThat(resolve(id)).describedAs(id).isNull();
    }
    assertThat(GolemItems.resolve(null)).isNull();
  }

  @Test
  void enumIdsAreStableLowercaseStrings() {
    assertThat(GolemCoreType.GUARDIAN.id()).isEqualTo("guardian");
    assertThat(NodeType.COLLECTION.id()).isEqualTo("collection");
    assertThat(GolemRuneType.BULWARK.id()).isEqualTo("bulwark");
    assertThat(GolemCoreType.fromId("sorter")).contains(GolemCoreType.SORTER);
    assertThat(NodeType.fromId("FISHING")).isEmpty();
    assertThat(GolemRuneType.fromId("ember")).contains(GolemRuneType.EMBER);
  }

  @Test
  void ingredientsMatchTheirExactVariantOnTheBaseMaterial() {
    assertIngredient("golemancy:crook", Id.minecraft("iron_hoe"), GolemItems.crook(), GolemItems.blankCore());
    assertIngredient("golemancy:blank_core", Id.minecraft("firework_star"), GolemItems.blankCore(), GolemItems.core(GolemCoreType.GUARDIAN));
    assertIngredient("golemancy:core/guardian", Id.minecraft("firework_star"), GolemItems.core(GolemCoreType.GUARDIAN), GolemItems.core(GolemCoreType.SORTER));
    assertIngredient("golemancy:blank_node", Id.minecraft("clay_ball"), GolemItems.blankNode(), GolemItems.node(NodeType.GUARD));
    assertIngredient("golemancy:node/deposit", Id.minecraft("brick"), GolemItems.node(NodeType.DEPOSIT), GolemItems.node(NodeType.INPUT));
    assertIngredient("golemancy:blank_rune", BLANK_RUNE_BASE, GolemItems.blankRune(), GolemItems.rune(new GolemRune(GolemRuneType.TIDE, 1)));
    assertIngredient("golemancy:rune/tide/2", Id.minecraft("angler_pottery_sherd"),
        GolemItems.rune(new GolemRune(GolemRuneType.TIDE, 2)), GolemItems.rune(new GolemRune(GolemRuneType.TIDE, 3)));
  }

  @Test
  void runeIngredientsUseTheSherdOfTheirAspectAtEveryTier() {
    for (GolemRuneType aspect : GolemRuneType.values()) {
      GolemRuneType other = GolemRuneType.values()[(aspect.ordinal() + 1) % GolemRuneType.values().length];
      for (int tier = GolemRuneType.MIN_TIER; tier <= GolemRuneType.MAX_TIER; tier++) {
        ExactIngredient ingredient = ingredient("golemancy:rune/" + aspect.id() + "/" + tier);

        assertThat(ingredient.base()).describedAs("%s tier %d base", aspect, tier).isEqualTo(RUNE_BASES.get(aspect));
        assertThat(ingredient.matches().test(GolemItems.rune(new GolemRune(aspect, tier)))).isTrue();
        assertThat(ingredient.matches().test(GolemItems.rune(new GolemRune(other, tier)))).describedAs("%s rejects %s", aspect, other).isFalse();
        assertThat(ingredient.matches().test(GolemItems.blankRune())).isFalse();
        assertThat(ingredient.matches().test(GolemItemStacks.plain(RUNE_BASES.get(aspect)))).isFalse();
      }
    }
  }

  @Test
  void defaultInfusionsChainEachAspectOnItsOwnSherd() {
    RegistryLookup registries = mock(RegistryLookup.class);
    when(registries.exists(any(RegistryKind.class), any(Id.class))).thenReturn(true);
    when(core.getPlatform().host().registries()).thenReturn(registries);
    List<String> warnings = new ArrayList<>();
    Map<String, InfusionRecipe> defaults = InfusionRecipe.defaults();
    InfusionRecipes recipes = InfusionRecipes.parse(defaults, InfusionRecipes.bukkitResolver(material -> true), warnings::add);

    assertThat(warnings).isEmpty();
    for (GolemRuneType aspect : GolemRuneType.values()) {
      GolemRuneType other = GolemRuneType.values()[(aspect.ordinal() + 1) % GolemRuneType.values().length];
      for (int tier = GolemRuneType.MIN_TIER; tier <= GolemRuneType.MAX_TIER; tier++) {
        String id = aspect.id() + "_" + tier;
        ItemRef center = tier == GolemRuneType.MIN_TIER ? GolemItems.blankRune() : GolemItems.rune(new GolemRune(aspect, tier - 1));
        ItemRef wrongCenter = tier == GolemRuneType.MIN_TIER ? GolemItems.rune(new GolemRune(aspect, tier)) : GolemItems.rune(new GolemRune(other, tier - 1));
        List<ItemRef> pedestals = new ArrayList<>();
        for (String pedestal : defaults.get(id).getPedestals()) {
          pedestals.add(GolemItemStacks.plain(Id.minecraft(pedestal.toLowerCase(Locale.ROOT))));
        }

        Optional<Infusion> match = recipes.match(center, pedestals);

        assertThat(match).describedAs("%s with its centre rune", id).map(Infusion::id).contains(id);
        assertThat(match.orElseThrow().resultItem().get().type()).describedAs("%s result base", id).isEqualTo(RUNE_BASES.get(aspect));
        assertThat(recipes.match(wrongCenter, pedestals)).describedAs("%s with %s", id, wrongCenter.type()).isEmpty();
        assertThat(recipes.match(GolemItemStacks.plain(center.type()), pedestals)).describedAs("%s with a plain sherd", id).isEmpty();
      }
    }
  }

  @Test
  void runeModelOverridesApplyToOneAspectOnly() {
    materialOverrides.put(List.of("golemancy", "rune", "ember"), Id.minecraft("stick"));
    materialOverrides.put(List.of("golemancy", "rune", "blank"), Id.minecraft("bone"));

    assertThat(ingredient("golemancy:rune/ember/2").base()).isEqualTo(Id.minecraft("stick"));
    assertThat(GolemItems.rune(new GolemRune(GolemRuneType.EMBER, 3)).type()).isEqualTo(Id.minecraft("stick"));
    assertThat(ingredient("golemancy:rune/tide/2").base()).isEqualTo(Id.minecraft("angler_pottery_sherd"));
    assertThat(GolemItems.rune(new GolemRune(GolemRuneType.TIDE, 1)).type()).isEqualTo(Id.minecraft("angler_pottery_sherd"));
    assertThat(ingredient("golemancy:blank_rune").base()).isEqualTo(Id.minecraft("bone"));
    assertThat(GolemItems.blankRune().type()).isEqualTo(Id.minecraft("bone"));
  }

  @Test
  void ingredientsRejectPlainBaseItems() {
    assertThat(ingredient("golemancy:blank_core").matches().test(GolemItemStacks.plain(Id.minecraft("firework_star")))).isFalse();
    assertThat(ingredient("golemancy:crook").matches().test(GolemItemStacks.plain(Id.minecraft("iron_hoe")))).isFalse();
    assertThat(ingredient("golemancy:crook").matches().test(null)).isFalse();
  }

  @Test
  void ingredientsMatchByTagRegardlessOfNameLoreOrLocale() {
    FakeItem english = GolemItemStacks.tagged(Id.minecraft("firework_star"), "core", "{\"role\":\"guardian\"}");
    FakeItem german = GolemItemStacks.tagged(Id.minecraft("firework_star"), "core", "{\"role\":\"guardian\"}");
    english.setDisplayName("Guardian Core");
    german.setDisplayName("Wächter-Kern");
    german.setLore(List.of("Bindet einen Golem an die Rolle Wächter"));

    ExactIngredient guardianCore = ingredient("golemancy:core/guardian");

    assertThat(guardianCore.matches().test(english)).isTrue();
    assertThat(guardianCore.matches().test(german)).isTrue();
  }

  @Test
  void ingredientBasesFollowCustomModelMaterialOverrides() {
    materialOverrides.put(List.of("golemancy", "crook"), Id.minecraft("stick"));

    assertThat(ingredient("golemancy:crook").base()).isEqualTo(Id.minecraft("stick"));
    assertThat(GolemItems.crook().type()).isEqualTo(Id.minecraft("stick"));
  }

  @Test
  void ingredientsReturnNullForUnknownIds() {
    for (String id : List.of("minecraft:crook", "golemancy:core/blank", "golemancy:rune/might/4", "golemancy:dormant_golem", "golemancy:masterwork_heart")) {
      assertThat(ingredient(id)).describedAs(id).isNull();
    }
    assertThat(GolemItems.ingredient(null)).isNull();
    assertThat(GolemItems.resolver().ingredient(Id.of("golemancy", "blank_node")).base()).isEqualTo(Id.minecraft("clay_ball"));
    assertThat(GolemItems.kindOf(GolemItems.resolver().result(Id.of("golemancy", "blank_node")))).contains(GolemItemKind.BLANK_NODE);
  }

  @Test
  void plainAndMalformedItemsReadAsNothing() {
    assertThat(GolemItems.kindOf(null)).isEmpty();
    assertThat(GolemItems.kindOf(GolemItemStacks.plain(Id.minecraft("iron_hoe")))).isEmpty();
    assertThat(GolemItems.isGolemItem(GolemItemStacks.plain(Id.minecraft("iron_hoe")))).isFalse();
    assertThat(GolemItems.kindOf(GolemItemStacks.tagged(Id.minecraft("stone"), "unknown", null))).isEmpty();
    assertThat(GolemItems.isGolemItem(GolemItemStacks.tagged(Id.minecraft("iron_hoe"), "crook", null))).isTrue();

    ItemRef malformedCore = GolemItemStacks.tagged(Id.minecraft("firework_star"), "core", "{not json");
    assertThat(GolemItems.kindOf(malformedCore)).contains(GolemItemKind.CORE);
    assertThat(GolemItems.coreOf(malformedCore)).isEmpty();
    assertThat(GolemItems.coreOf(GolemItemStacks.tagged(Id.minecraft("firework_star"), "core", null))).isEmpty();
    assertThat(GolemItems.coreOf(GolemItemStacks.tagged(Id.minecraft("firework_star"), "core", "{\"role\":\"blank\"}"))).isEmpty();
    assertThat(GolemItems.runeOf(GolemItemStacks.tagged(Id.minecraft("arms_up_pottery_sherd"), "rune", "{\"aspect\":\"might\",\"tier\":7}"))).isEmpty();
    assertThat(GolemItems.packedGolemOf(GolemItemStacks.tagged(Id.minecraft("iron_block"), "dormant_golem", "{\"golem\":\"" + GOLEM + "\"}"))).isEmpty();
    assertThat(GolemItems.nodeTypeOf(GolemItemStacks.tagged(Id.minecraft("brick"), "rune", "{\"type\":\"guard\"}"))).isEmpty();
  }

  @Test
  void aCrookRemembersItsSignalChannelUntilCleared() {
    ItemRef crook = GolemItemStacks.tagged(Id.minecraft("iron_hoe"), "crook", null);

    assertThat(GolemItems.crookChannel(crook)).isEmpty();
    assertThat(GolemItems.setCrookChannel(crook, DyeColor.RED)).isTrue();
    assertThat(GolemItems.crookChannel(crook)).contains(DyeColor.RED);
    assertThat(GolemItemStacks.stored(crook, CROOK_CHANNEL_KEY)).isEqualTo("RED");
    assertThat(GolemItems.setCrookChannel(crook, null)).isTrue();
    assertThat(GolemItems.crookChannel(crook)).isEmpty();
    assertThat(GolemItemStacks.stored(crook, CROOK_CHANNEL_KEY)).isNull();
  }

  @Test
  void onlyCrooksTakeASignalChannelAndUnknownValuesReadAsNone() {
    ItemRef core = GolemItemStacks.tagged(Id.minecraft("firework_star"), "core", "{\"role\":\"guardian\"}");
    ItemRef crook = GolemItemStacks.tagged(Id.minecraft("iron_hoe"), "crook", null);
    crook.data().set(CROOK_CHANNEL_KEY, "PLAID");

    assertThat(GolemItems.setCrookChannel(core, DyeColor.RED)).isFalse();
    assertThat(GolemItemStacks.stored(core, CROOK_CHANNEL_KEY)).isNull();
    assertThat(GolemItems.setCrookChannel(GolemItemStacks.plain(Id.minecraft("iron_hoe")), DyeColor.RED)).isFalse();
    assertThat(GolemItems.setCrookChannel(null, DyeColor.RED)).isFalse();
    assertThat(GolemItems.crookChannel(core)).isEmpty();
    assertThat(GolemItems.crookChannel(null)).isEmpty();
    assertThat(GolemItems.crookChannel(crook)).isEmpty();
  }

  private CustomModel model(Id fallback, String[] path) {
    List<String> modelPath = List.of(path);
    modelPaths.add(modelPath);
    Id material = materialOverrides.getOrDefault(modelPath, fallback);
    CustomModel model = mock(CustomModel.class);
    when(model.material()).thenReturn(material);
    when(model.toItemStack()).thenAnswer(call -> new FakeItem(material, 1));
    when(model.toItemStack(any(ItemRef.class))).thenAnswer(call -> call.getArgument(0));
    return model;
  }

  private static List<ItemRef> everyItem() {
    return List.of(
        GolemItems.crook(),
        GolemItems.blankCore(),
        GolemItems.core(GolemCoreType.ANGLER),
        GolemItems.blankNode(),
        GolemItems.node(NodeType.FILTER),
        GolemItems.blankRune(),
        GolemItems.rune(new GolemRune(GolemRuneType.ORDER, 2)),
        GolemItems.dormantGolem(dormant(Id.minecraft("snow_block"))),
        GolemItems.shatteredCore(PACKED),
        GolemItems.masterworkHeart());
  }

  private static DormantGolem dormant(Id block) {
    return new DormantGolem(PACKED, block, List.of(GolemCoreType.GUARDIAN, GolemCoreType.GATHERER),
        List.of(new GolemRune(GolemRuneType.MIGHT, 2), new GolemRune(GolemRuneType.TIDE, 1)), 79.6D, 100D);
  }

  private static ItemRef resolve(String id) {
    return GolemItems.resolve(Id.parse(id));
  }

  private static ExactIngredient ingredient(String id) {
    return GolemItems.ingredient(Id.parse(id));
  }

  private static void assertIngredient(String id, Id base, ItemRef variant, ItemRef otherVariant) {
    ExactIngredient ingredient = ingredient(id);

    assertThat(ingredient.base()).describedAs(id).isEqualTo(base);
    assertThat(ingredient.matches().test(variant)).describedAs("%s matches its variant", id).isTrue();
    assertThat(ingredient.matches().test(otherVariant)).describedAs("%s rejects another variant", id).isFalse();
  }

  private static Rgb tint(ItemRef item) {
    return ((FakeItem) item).fireworkStarColor();
  }
}
