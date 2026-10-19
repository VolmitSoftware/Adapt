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
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.reflect.registries.ItemFlags;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemItemsTest extends AdaptTestBase {
  private static final UUID GOLEM = UUID.fromString("00000000-0000-0000-0000-00000000a001");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000b002");
  private static final NamespacedKey CROOK_CHANNEL_KEY = new NamespacedKey("adapt", "crook_channel");
  private static final PackedGolem PACKED = new PackedGolem(GOLEM, OWNER, "{\"body\":\"IRON\"}");
  private static final Material BLANK_RUNE_BASE = Material.SCRAPE_POTTERY_SHERD;
  private static final Map<GolemRuneType, Material> RUNE_BASES = new EnumMap<>(Map.of(
      GolemRuneType.SWIFTNESS, Material.GUSTER_POTTERY_SHERD,
      GolemRuneType.MIGHT, Material.ARMS_UP_POTTERY_SHERD,
      GolemRuneType.EMBER, Material.BURN_POTTERY_SHERD,
      GolemRuneType.TIDE, Material.ANGLER_POTTERY_SHERD,
      GolemRuneType.ORDER, Material.SHEAF_POTTERY_SHERD,
      GolemRuneType.ENTROPY, Material.DANGER_POTTERY_SHERD,
      GolemRuneType.BULWARK, Material.SHELTER_POTTERY_SHERD));

  private final List<List<String>> modelPaths = new ArrayList<>();
  private final Map<List<String>, Material> materialOverrides = new HashMap<>();
  private MockedConstruction<ItemStack> stacks;
  private MockedStatic<CustomModel> models;

  @BeforeEach
  void installItemFactory() {
    stacks = mockConstruction(ItemStack.class, (item, context) -> GolemItemStacks.wire(item, (Material) context.arguments().getFirst()));
    models = mockStatic(CustomModel.class);
    models.when(() -> CustomModel.get(any(Material.class), any(String[].class)))
        .thenAnswer(call -> model(call.getArgument(0), (String[]) call.getRawArguments()[1]));
  }

  @AfterEach
  void removeItemFactory() {
    models.close();
    stacks.close();
  }

  @Test
  void crookIsAnUnbreakableSingleStackHoeWithHiddenAttributes() {
    ItemStack crook = GolemItems.crook();

    assertThat(crook.getType()).isEqualTo(Material.IRON_HOE);
    assertThat(GolemItems.kindOf(crook)).contains(GolemItemKind.CROOK);
    ItemMeta meta = crook.getItemMeta();
    verify(meta).setUnbreakable(true);
    assertThat(meta.getItemFlags()).contains(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
    verify(meta).setMaxStackSize(1);
    verify(meta).setEnchantmentGlintOverride(true);
    verify(meta).setDisplayName(contains("Artificer's Crook"));
    verify(crook).setItemMeta(meta);
  }

  @Test
  void coresRoundTripTheirRoleAsFireworkStars() {
    for (GolemCoreType role : GolemCoreType.values()) {
      ItemStack core = GolemItems.core(role);

      assertThat(core.getType()).isEqualTo(Material.FIREWORK_STAR);
      assertThat(GolemItems.kindOf(core)).contains(role == GolemCoreType.BLANK ? GolemItemKind.BLANK_CORE : GolemItemKind.CORE);
      assertThat(GolemItems.coreOf(core)).contains(role);
      verify(core.getItemMeta()).setMaxStackSize(16);
    }
    assertThat(GolemItems.kindOf(GolemItems.blankCore())).contains(GolemItemKind.BLANK_CORE);
  }

  @Test
  void coreTintsAreGrayForBlankBlackForShatteredAndDistinctPerRole() {
    Set<Color> roleTints = new HashSet<>();
    for (GolemCoreType role : GolemCoreType.values()) {
      if (role != GolemCoreType.BLANK) {
        roleTints.add(tint(GolemItems.core(role)));
      }
    }

    assertThat(tint(GolemItems.blankCore())).isEqualTo(Color.GRAY);
    assertThat(tint(GolemItems.shatteredCore(PACKED))).isEqualTo(Color.BLACK);
    assertThat(roleTints).hasSize(GolemCoreType.values().length - 1).doesNotContain(Color.GRAY, Color.BLACK);
  }

  @Test
  void nodesAreClayWhenBlankAndBrickWhenTyped() {
    ItemStack blank = GolemItems.blankNode();

    assertThat(blank.getType()).isEqualTo(Material.CLAY_BALL);
    assertThat(GolemItems.kindOf(blank)).contains(GolemItemKind.BLANK_NODE);
    assertThat(GolemItems.nodeTypeOf(blank)).isEmpty();
    for (NodeType type : NodeType.values()) {
      ItemStack node = GolemItems.node(type);

      assertThat(node.getType()).isEqualTo(Material.BRICK);
      assertThat(GolemItems.kindOf(node)).contains(GolemItemKind.NODE);
      assertThat(GolemItems.nodeTypeOf(node)).contains(type);
    }
  }

  @Test
  void runesRoundTripTheirAspectAndTier() {
    ItemStack blank = GolemItems.blankRune();

    assertThat(blank.getType()).isEqualTo(BLANK_RUNE_BASE);
    assertThat(GolemItems.kindOf(blank)).contains(GolemItemKind.BLANK_RUNE);
    assertThat(GolemItems.runeOf(blank)).isEmpty();
    for (GolemRuneType aspect : GolemRuneType.values()) {
      for (int tier = GolemRuneType.MIN_TIER; tier <= GolemRuneType.MAX_TIER; tier++) {
        GolemRune expected = new GolemRune(aspect, tier);
        ItemStack rune = GolemItems.rune(expected);

        assertThat(rune.getType()).describedAs("%s tier %d base", aspect, tier).isEqualTo(RUNE_BASES.get(aspect));
        assertThat(GolemItems.kindOf(rune)).contains(GolemItemKind.RUNE);
        assertThat(GolemItems.runeOf(rune)).contains(expected);
      }
    }
  }

  @Test
  void everyRuneAspectAndTheBlankRuneBuildOnTheirOwnPotterySherd() {
    Set<Material> bases = new HashSet<>(RUNE_BASES.values());
    bases.add(BLANK_RUNE_BASE);

    assertThat(RUNE_BASES).containsOnlyKeys(GolemRuneType.values());
    assertThat(bases).hasSize(GolemRuneType.values().length + 1).allSatisfy(base -> assertThat(base.name()).endsWith("_POTTERY_SHERD"));
    assertThat(bases).doesNotContain(Material.DISC_FRAGMENT_5, Material.BRICK, Material.CLAY_BALL);
  }

  @Test
  void runeOfRecognisesEveryAspectOnItsSherd() {
    for (GolemRuneType aspect : GolemRuneType.values()) {
      for (int tier = GolemRuneType.MIN_TIER; tier <= GolemRuneType.MAX_TIER; tier++) {
        ItemStack tagged = GolemItemStacks.tagged(RUNE_BASES.get(aspect), "rune", "{\"aspect\":\"" + aspect.id() + "\",\"tier\":" + tier + "}");

        assertThat(GolemItems.runeOf(tagged)).describedAs("%s tier %d on %s", aspect, tier, tagged.getType()).contains(new GolemRune(aspect, tier));
      }
    }
    assertThat(GolemItems.runeOf(GolemItemStacks.plain(Material.GUSTER_POTTERY_SHERD))).isEmpty();
    assertThat(GolemItems.runeOf(GolemItemStacks.tagged(BLANK_RUNE_BASE, "blank_rune", null))).isEmpty();
  }

  @Test
  void runeTiersOutsideOneToThreeAreRejected() {
    assertThatThrownBy(() -> new GolemRune(GolemRuneType.MIGHT, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GolemRune(GolemRuneType.MIGHT, 4)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void dormantGolemsKeepTheirBlockVariantAndPackedGolem() {
    for (Material block : List.of(Material.SNOW_BLOCK, Material.IRON_BLOCK, Material.COPPER_BLOCK, Material.WAXED_OXIDIZED_COPPER)) {
      ItemStack dormant = GolemItems.dormantGolem(dormant(block));

      assertThat(dormant.getType()).isEqualTo(block);
      assertThat(GolemItems.kindOf(dormant)).contains(GolemItemKind.DORMANT_GOLEM);
      assertThat(GolemItems.packedGolemOf(dormant)).contains(PACKED);
      verify(dormant.getItemMeta()).setMaxStackSize(1);
    }
    assertThat(modelPaths).containsExactly(
        List.of("golemancy", "dormant_golem", "snow_block"),
        List.of("golemancy", "dormant_golem", "iron_block"),
        List.of("golemancy", "dormant_golem", "copper_block"),
        List.of("golemancy", "dormant_golem", "waxed_oxidized_copper"));
  }

  @Test
  void dormantGolemLoreListsCoresRunesAndHealth() {
    ItemStack dormant = GolemItems.dormantGolem(dormant(Material.IRON_BLOCK));

    ArgumentCaptor<List<String>> lore = loreCaptor();
    verify(dormant.getItemMeta()).setLore(lore.capture());
    assertThat(String.join("\n", lore.getValue()))
        .contains("Core: Guardian")
        .contains("Core: Gatherer")
        .contains("Runes: Might ◆◆◇, Tide ◆◇◇")
        .contains("Health: 80/100");
  }

  @Test
  void dormantGolemsRejectBlocksOutsideTheBodyMaterials() {
    assertThatThrownBy(() -> new DormantGolem(PACKED, Material.GOLD_BLOCK, List.of(), List.of(), 1D, 1D))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void copperBlocksMatchOxidationStageAndWax() {
    assertThat(GolemItems.copperBlock(0, false)).isEqualTo(Material.COPPER_BLOCK);
    assertThat(GolemItems.copperBlock(1, false)).isEqualTo(Material.EXPOSED_COPPER);
    assertThat(GolemItems.copperBlock(2, false)).isEqualTo(Material.WEATHERED_COPPER);
    assertThat(GolemItems.copperBlock(3, false)).isEqualTo(Material.OXIDIZED_COPPER);
    assertThat(GolemItems.copperBlock(0, true)).isEqualTo(Material.WAXED_COPPER_BLOCK);
    assertThat(GolemItems.copperBlock(1, true)).isEqualTo(Material.WAXED_EXPOSED_COPPER);
    assertThat(GolemItems.copperBlock(2, true)).isEqualTo(Material.WAXED_WEATHERED_COPPER);
    assertThat(GolemItems.copperBlock(3, true)).isEqualTo(Material.WAXED_OXIDIZED_COPPER);
    assertThatThrownBy(() -> GolemItems.copperBlock(4, false)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void dormantBlocksFollowTheBodyAndTheCopperStage() {
    assertThat(GolemItems.dormantBlock(GolemBody.FROST, 2, true)).isEqualTo(Material.SNOW_BLOCK);
    assertThat(GolemItems.dormantBlock(GolemBody.IRON, 0, false)).isEqualTo(Material.IRON_BLOCK);
    assertThat(GolemItems.dormantBlock(GolemBody.COPPER, 0, false)).isEqualTo(Material.COPPER_BLOCK);
    assertThat(GolemItems.dormantBlock(GolemBody.COPPER, 2, true)).isEqualTo(Material.WAXED_WEATHERED_COPPER);
    assertThat(GolemItems.dormantBlock(GolemBody.COPPER, 9, false)).isEqualTo(Material.OXIDIZED_COPPER);
    assertThatThrownBy(() -> GolemItems.dormantBlock(GolemBody.BONE, 0, false)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shatteredCoresAndMasterworkHeartsRoundTrip() {
    ItemStack shattered = GolemItems.shatteredCore(PACKED);
    ItemStack heart = GolemItems.masterworkHeart();

    assertThat(shattered.getType()).isEqualTo(Material.FIREWORK_STAR);
    assertThat(GolemItems.kindOf(shattered)).contains(GolemItemKind.SHATTERED_CORE);
    assertThat(GolemItems.packedGolemOf(shattered)).contains(PACKED);
    assertThat(GolemItems.coreOf(shattered)).isEmpty();
    assertThat(heart.getType()).isEqualTo(Material.HEART_OF_THE_SEA);
    assertThat(GolemItems.kindOf(heart)).contains(GolemItemKind.MASTERWORK_HEART);
    assertThat(GolemItems.packedGolemOf(heart)).isEmpty();
  }

  @Test
  void everyItemUsesItsKindModelPathAndGlint() {
    List<ItemStack> items = everyItem();

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
    for (ItemStack item : items) {
      verify(item.getItemMeta()).setEnchantmentGlintOverride(true);
    }
  }

  @Test
  void everyItemHidesTheVanillaAdditionalTooltip() {
    for (ItemStack item : everyItem()) {
      assertThat(item.getItemMeta().getItemFlags())
          .describedAs("%s tooltip flags", item.getType())
          .contains(ItemFlags.HIDE_ADDITIONAL_TOOLTIP);
    }
  }

  @Test
  void everyRuneOnItsSherdHidesTheVanillaAdditionalTooltip() {
    List<ItemStack> runes = new ArrayList<>();
    runes.add(GolemItems.blankRune());
    for (GolemRuneType aspect : GolemRuneType.values()) {
      for (int tier = GolemRuneType.MIN_TIER; tier <= GolemRuneType.MAX_TIER; tier++) {
        runes.add(GolemItems.rune(new GolemRune(aspect, tier)));
      }
    }

    for (ItemStack rune : runes) {
      ItemMeta meta = rune.getItemMeta();

      assertThat(rune.getType()).isNotEqualTo(Material.DISC_FRAGMENT_5);
      assertThat(meta.getItemFlags()).describedAs("%s tooltip flags", rune.getType()).contains(ItemFlags.HIDE_ADDITIONAL_TOOLTIP);
      verify(meta, never()).setHideTooltip(true);
    }
  }

  @Test
  void hidingTheVanillaTooltipKeepsTheItemsOwnNameAndLore() {
    for (ItemStack item : everyItem()) {
      ItemMeta meta = item.getItemMeta();

      verify(meta, never()).setHideTooltip(true);
      verify(meta).setDisplayName(any(String.class));
      verify(meta).setLore(any());
    }
  }

  @Test
  void tagsUseTheExplicitAdaptKeysWithAGsonPayload() {
    ItemStack core = GolemItems.core(GolemCoreType.GUARDIAN);
    ItemStack crook = GolemItems.crook();

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
    assertIngredient("golemancy:crook", Material.IRON_HOE, GolemItems.crook(), GolemItems.blankCore());
    assertIngredient("golemancy:blank_core", Material.FIREWORK_STAR, GolemItems.blankCore(), GolemItems.core(GolemCoreType.GUARDIAN));
    assertIngredient("golemancy:core/guardian", Material.FIREWORK_STAR, GolemItems.core(GolemCoreType.GUARDIAN), GolemItems.core(GolemCoreType.SORTER));
    assertIngredient("golemancy:blank_node", Material.CLAY_BALL, GolemItems.blankNode(), GolemItems.node(NodeType.GUARD));
    assertIngredient("golemancy:node/deposit", Material.BRICK, GolemItems.node(NodeType.DEPOSIT), GolemItems.node(NodeType.INPUT));
    assertIngredient("golemancy:blank_rune", BLANK_RUNE_BASE, GolemItems.blankRune(), GolemItems.rune(new GolemRune(GolemRuneType.TIDE, 1)));
    assertIngredient("golemancy:rune/tide/2", Material.ANGLER_POTTERY_SHERD,
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
    List<String> warnings = new ArrayList<>();
    Map<String, InfusionRecipe> defaults = InfusionRecipe.defaults();
    InfusionRecipes recipes = InfusionRecipes.parse(defaults, InfusionRecipes.bukkitResolver(material -> true), warnings::add);

    assertThat(warnings).isEmpty();
    for (GolemRuneType aspect : GolemRuneType.values()) {
      GolemRuneType other = GolemRuneType.values()[(aspect.ordinal() + 1) % GolemRuneType.values().length];
      for (int tier = GolemRuneType.MIN_TIER; tier <= GolemRuneType.MAX_TIER; tier++) {
        String id = aspect.id() + "_" + tier;
        ItemStack center = tier == GolemRuneType.MIN_TIER ? GolemItems.blankRune() : GolemItems.rune(new GolemRune(aspect, tier - 1));
        ItemStack wrongCenter = tier == GolemRuneType.MIN_TIER ? GolemItems.rune(new GolemRune(aspect, tier)) : GolemItems.rune(new GolemRune(other, tier - 1));
        List<ItemStack> pedestals = new ArrayList<>();
        for (String pedestal : defaults.get(id).getPedestals()) {
          pedestals.add(GolemItemStacks.plain(Material.valueOf(pedestal)));
        }

        Optional<Infusion> match = recipes.match(center, pedestals);

        assertThat(match).describedAs("%s with its centre rune", id).map(Infusion::id).contains(id);
        assertThat(match.orElseThrow().resultItem().get().getType()).describedAs("%s result base", id).isEqualTo(RUNE_BASES.get(aspect));
        assertThat(recipes.match(wrongCenter, pedestals)).describedAs("%s with %s", id, wrongCenter.getType()).isEmpty();
        assertThat(recipes.match(GolemItemStacks.plain(center.getType()), pedestals)).describedAs("%s with a plain sherd", id).isEmpty();
      }
    }
  }

  @Test
  void runeModelOverridesApplyToOneAspectOnly() {
    materialOverrides.put(List.of("golemancy", "rune", "ember"), Material.STICK);
    materialOverrides.put(List.of("golemancy", "rune", "blank"), Material.BONE);

    assertThat(ingredient("golemancy:rune/ember/2").base()).isEqualTo(Material.STICK);
    assertThat(GolemItems.rune(new GolemRune(GolemRuneType.EMBER, 3)).getType()).isEqualTo(Material.STICK);
    assertThat(ingredient("golemancy:rune/tide/2").base()).isEqualTo(Material.ANGLER_POTTERY_SHERD);
    assertThat(GolemItems.rune(new GolemRune(GolemRuneType.TIDE, 1)).getType()).isEqualTo(Material.ANGLER_POTTERY_SHERD);
    assertThat(ingredient("golemancy:blank_rune").base()).isEqualTo(Material.BONE);
    assertThat(GolemItems.blankRune().getType()).isEqualTo(Material.BONE);
  }

  @Test
  void ingredientsRejectPlainBaseItems() {
    assertThat(ingredient("golemancy:blank_core").matches().test(GolemItemStacks.plain(Material.FIREWORK_STAR))).isFalse();
    assertThat(ingredient("golemancy:crook").matches().test(GolemItemStacks.plain(Material.IRON_HOE))).isFalse();
    assertThat(ingredient("golemancy:crook").matches().test(null)).isFalse();
  }

  @Test
  void ingredientsMatchByTagRegardlessOfNameLoreOrLocale() {
    ItemStack english = GolemItemStacks.tagged(Material.FIREWORK_STAR, "core", "{\"role\":\"guardian\"}");
    ItemStack german = GolemItemStacks.tagged(Material.FIREWORK_STAR, "core", "{\"role\":\"guardian\"}");
    when(english.getItemMeta().getDisplayName()).thenReturn("Guardian Core");
    when(german.getItemMeta().getDisplayName()).thenReturn("Wächter-Kern");
    when(german.getItemMeta().getLore()).thenReturn(List.of("Bindet einen Golem an die Rolle Wächter"));

    ExactIngredient guardianCore = ingredient("golemancy:core/guardian");

    assertThat(guardianCore.matches().test(english)).isTrue();
    assertThat(guardianCore.matches().test(german)).isTrue();
  }

  @Test
  void ingredientBasesFollowCustomModelMaterialOverrides() {
    materialOverrides.put(List.of("golemancy", "crook"), Material.STICK);

    assertThat(ingredient("golemancy:crook").base()).isEqualTo(Material.STICK);
    assertThat(GolemItems.crook().getType()).isEqualTo(Material.STICK);
  }

  @Test
  void ingredientsReturnNullForUnknownIds() {
    for (String id : List.of("minecraft:crook", "golemancy:core/blank", "golemancy:rune/might/4", "golemancy:dormant_golem", "golemancy:masterwork_heart")) {
      assertThat(ingredient(id)).describedAs(id).isNull();
    }
    assertThat(GolemItems.ingredient(null)).isNull();
    assertThat(GolemItems.resolver().ingredient(NamespacedKey.fromString("golemancy:blank_node")).base()).isEqualTo(Material.CLAY_BALL);
    assertThat(GolemItems.kindOf(GolemItems.resolver().result(NamespacedKey.fromString("golemancy:blank_node")))).contains(GolemItemKind.BLANK_NODE);
  }

  @Test
  void plainAndMalformedItemsReadAsNothing() {
    assertThat(GolemItems.kindOf(null)).isEmpty();
    assertThat(GolemItems.kindOf(GolemItemStacks.plain(Material.IRON_HOE))).isEmpty();
    assertThat(GolemItems.isGolemItem(GolemItemStacks.plain(Material.IRON_HOE))).isFalse();
    assertThat(GolemItems.kindOf(GolemItemStacks.tagged(Material.STONE, "unknown", null))).isEmpty();
    assertThat(GolemItems.isGolemItem(GolemItemStacks.tagged(Material.IRON_HOE, "crook", null))).isTrue();

    ItemStack malformedCore = GolemItemStacks.tagged(Material.FIREWORK_STAR, "core", "{not json");
    assertThat(GolemItems.kindOf(malformedCore)).contains(GolemItemKind.CORE);
    assertThat(GolemItems.coreOf(malformedCore)).isEmpty();
    assertThat(GolemItems.coreOf(GolemItemStacks.tagged(Material.FIREWORK_STAR, "core", null))).isEmpty();
    assertThat(GolemItems.coreOf(GolemItemStacks.tagged(Material.FIREWORK_STAR, "core", "{\"role\":\"blank\"}"))).isEmpty();
    assertThat(GolemItems.runeOf(GolemItemStacks.tagged(Material.ARMS_UP_POTTERY_SHERD, "rune", "{\"aspect\":\"might\",\"tier\":7}"))).isEmpty();
    assertThat(GolemItems.packedGolemOf(GolemItemStacks.tagged(Material.IRON_BLOCK, "dormant_golem", "{\"golem\":\"" + GOLEM + "\"}"))).isEmpty();
    assertThat(GolemItems.nodeTypeOf(GolemItemStacks.tagged(Material.BRICK, "rune", "{\"type\":\"guard\"}"))).isEmpty();
  }

  @Test
  void aCrookRemembersItsSignalChannelUntilCleared() {
    ItemStack crook = GolemItemStacks.tagged(Material.IRON_HOE, "crook", null);

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
    ItemStack core = GolemItemStacks.tagged(Material.FIREWORK_STAR, "core", "{\"role\":\"guardian\"}");
    ItemStack crook = GolemItemStacks.tagged(Material.IRON_HOE, "crook", null);
    crook.getItemMeta().getPersistentDataContainer().set(CROOK_CHANNEL_KEY, PersistentDataType.STRING, "PLAID");

    assertThat(GolemItems.setCrookChannel(core, DyeColor.RED)).isFalse();
    assertThat(GolemItemStacks.stored(core, CROOK_CHANNEL_KEY)).isNull();
    assertThat(GolemItems.setCrookChannel(GolemItemStacks.plain(Material.IRON_HOE), DyeColor.RED)).isFalse();
    assertThat(GolemItems.setCrookChannel(null, DyeColor.RED)).isFalse();
    assertThat(GolemItems.crookChannel(core)).isEmpty();
    assertThat(GolemItems.crookChannel(null)).isEmpty();
    assertThat(GolemItems.crookChannel(crook)).isEmpty();
  }

  private CustomModel model(Material fallback, String[] path) {
    List<String> modelPath = List.of(path);
    modelPaths.add(modelPath);
    Material material = materialOverrides.getOrDefault(modelPath, fallback);
    CustomModel model = mock(CustomModel.class);
    when(model.material()).thenReturn(material);
    when(model.toItemStack()).thenAnswer(call -> new ItemStack(material));
    when(model.toItemStack(any(ItemStack.class))).thenAnswer(call -> call.getArgument(0));
    return model;
  }

  private static List<ItemStack> everyItem() {
    return List.of(
        GolemItems.crook(),
        GolemItems.blankCore(),
        GolemItems.core(GolemCoreType.ANGLER),
        GolemItems.blankNode(),
        GolemItems.node(NodeType.FILTER),
        GolemItems.blankRune(),
        GolemItems.rune(new GolemRune(GolemRuneType.ORDER, 2)),
        GolemItems.dormantGolem(dormant(Material.SNOW_BLOCK)),
        GolemItems.shatteredCore(PACKED),
        GolemItems.masterworkHeart());
  }

  private static DormantGolem dormant(Material block) {
    return new DormantGolem(PACKED, block, List.of(GolemCoreType.GUARDIAN, GolemCoreType.GATHERER),
        List.of(new GolemRune(GolemRuneType.MIGHT, 2), new GolemRune(GolemRuneType.TIDE, 1)), 79.6D, 100D);
  }

  private static ItemStack resolve(String id) {
    return GolemItems.resolve(NamespacedKey.fromString(id));
  }

  private static ExactIngredient ingredient(String id) {
    return GolemItems.ingredient(NamespacedKey.fromString(id));
  }

  private static void assertIngredient(String id, Material base, ItemStack variant, ItemStack otherVariant) {
    ExactIngredient ingredient = ingredient(id);

    assertThat(ingredient.base()).describedAs(id).isEqualTo(base);
    assertThat(ingredient.matches().test(variant)).describedAs("%s matches its variant", id).isTrue();
    assertThat(ingredient.matches().test(otherVariant)).describedAs("%s rejects another variant", id).isFalse();
  }

  private static Color tint(ItemStack item) {
    ArgumentCaptor<FireworkEffect> effect = ArgumentCaptor.forClass(FireworkEffect.class);
    verify((FireworkEffectMeta) item.getItemMeta()).setEffect(effect.capture());
    return effect.getValue().getColors().getFirst();
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<List<String>> loreCaptor() {
    return ArgumentCaptor.forClass(List.class);
  }
}
