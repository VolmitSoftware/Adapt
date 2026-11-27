package art.arcane.adapt.client.view;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.item.BrewingRecipe;
import art.arcane.adapt.api.item.MixingRecipe;
import art.arcane.adapt.engine.mutation.tool.MutationTools;

import art.arcane.adapt.api.mutation.MutationTool;

import art.arcane.adapt.engine.skill.LegacyContentNames;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.entity.EffectCategory;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.misc.ItemLooks;
import art.arcane.volmlib.util.localization.LanguageAudience;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Builds the {@link RecipeView}s every enabled adaptation unlocks, in the viewer's language: its crafting, cooking,
 * stonecutting and smithing recipes, its brews, and for an adaptation that mixes potions one mix per level at which
 * the number of bottles it takes changes.
 */
public final class RecipeViews {
  private static final String POTION_TEXTURES = "minecraft:textures/item/";
  private static final int WATER = 0x385DC6;
  private static final Map<String, Integer> VANILLA_POTIONS = Map.of(
      "awkward", WATER,
      "healing", 0xF82423,
      "night_vision", 0xC2FF66,
      "regeneration", 0xCD5CAB,
      "swiftness", 0x33EBFF,
      "weakness", 0x484D48);

  private RecipeViews() {
  }

  /** Every enabled adaptation's recipes, adaptation by adaptation in registry order. */
  public static List<RecipeView> build(SkillRegistry registry, UUID viewer) {
    return LanguageAudience.call(viewer, () -> {
      RegistryLookup registries = AdaptCore.platform().host().registries();
      List<RecipeView> views = new ArrayList<>();
      for (Skill<?> skill : registry.getSkills()) {
        if (!skill.isEnabled()) {
          continue;
        }
        for (Adaptation<?> adaptation : SkillHost.ownedAdaptations(skill)) {
          if (adaptation.isEnabled()) {
            views.addAll(of(adaptation, registries));
          }
        }
      }
      return List.copyOf(views);
    });
  }

  /**
   * The recipes {@code adaptation} unlocks, grouped and ordered as its page shows them: groups by the lowest level any
   * of their recipes needs, then by first appearance; within a group by level, then registration.
   */
  public static List<RecipeView> of(Adaptation<?> adaptation, RegistryLookup registries) {
    String page = ToolViews.page(adaptation);
    List<RecipeView> views = new ArrayList<>();
    for (RecipeSpec recipe : adaptation.getRecipes()) {
      views.add(crafted(page, recipe, registries));
    }
    for (BrewingRecipe brew : adaptation.getBrewingRecipes()) {
      views.add(new RecipeView(page, RecipeView.Kind.BREWING, 1, brewGroup(brew), 0,
          List.of(item(brew.basePotion(), registries), plain(brew.ingredient(), 1)), item(brew.result(), registries)));
    }
    for (MixingRecipe mix : adaptation.getMixingRecipes()) {
      views.add(mix(page, mix));
    }
    return ordered(views);
  }

  private static List<RecipeView> ordered(List<RecipeView> views) {
    Map<String, Integer> lowest = new LinkedHashMap<>();
    for (RecipeView view : views) {
      lowest.merge(view.group(), view.requiredLevel(), Math::min);
    }
    List<String> groups = new ArrayList<>(lowest.keySet());
    List<RecipeView> sorted = new ArrayList<>(views);
    sorted.sort(Comparator.comparingInt((RecipeView view) -> lowest.get(view.group()))
        .thenComparingInt((RecipeView view) -> groups.indexOf(view.group()))
        .thenComparingInt(RecipeView::requiredLevel));
    return List.copyOf(sorted);
  }

  private static RecipeView mix(String page, MixingRecipe mix) {
    List<RecipeView.Item> inputs = new ArrayList<>();
    RecipeView.Item bottle = potion(mix.bottle(), WATER, "", nameKey(mix.bottle(), null), List.of());
    for (int index = 0; index < mix.bottles(); index++) {
      inputs.add(bottle);
    }
    inputs.add(plain(mix.ingredient(), 1));
    return new RecipeView(page, RecipeView.Kind.MIXING, mix.level(), "mix", 0, inputs,
        potion(mix.bottle(), mix.color().packed(), CatalogViews.plain(mix.name()), "", List.of()));
  }

  private static RecipeView crafted(String page, RecipeSpec recipe, RegistryLookup registries) {
    RecipeView.Item result = result(recipe, registries);
    return switch (recipe) {
      case RecipeSpec.Shaped shaped -> {
        int width = 0;
        for (String row : shaped.shape()) {
          width = Math.max(width, row.length());
        }
        List<RecipeView.Item> cells = new ArrayList<>();
        for (String row : shaped.shape()) {
          for (int column = 0; column < width; column++) {
            Ingredient ingredient = column < row.length() ? shaped.ingredients().get(row.charAt(column)) : null;
            cells.add(ingredient == null ? RecipeView.Item.NONE : ingredient(ingredient, registries));
          }
        }
        yield new RecipeView(page, RecipeView.Kind.SHAPED, recipe.requiredLevel(), madeOf(result), width, cells, result);
      }
      case RecipeSpec.Shapeless shapeless -> {
        List<RecipeView.Item> inputs = ingredients(shapeless.ingredients(), registries);
        yield new RecipeView(page, RecipeView.Kind.SHAPELESS, recipe.requiredLevel(), madeOf(result), 0, inputs, result);
      }
      case RecipeSpec.Cooking cooking -> single(page, RecipeView.Kind.COOKING, recipe, cooking.input(), result, registries);
      case RecipeSpec.Stonecutting cutting -> single(page, RecipeView.Kind.STONECUTTING, recipe, cutting.input(), result, registries);
      case RecipeSpec.Smithing smithing -> {
        List<RecipeView.Item> inputs = List.of(
            smithing.template() == null ? RecipeView.Item.NONE : ingredient(smithing.template(), registries),
            ingredient(smithing.base(), registries), ingredient(smithing.addition(), registries));
        yield new RecipeView(page, RecipeView.Kind.SMITHING, recipe.requiredLevel(), madeOf(result), 0, inputs, result);
      }
    };
  }

  private static RecipeView single(String page, RecipeView.Kind kind, RecipeSpec recipe, Ingredient input, RecipeView.Item result,
                                   RegistryLookup registries) {
    List<RecipeView.Item> inputs = List.of(ingredient(input, registries));
    return new RecipeView(page, kind, recipe.requiredLevel(), madeOf(result), 0, inputs, result);
  }

  private static List<RecipeView.Item> ingredients(List<Ingredient> ingredients, RegistryLookup registries) {
    List<RecipeView.Item> items = new ArrayList<>(ingredients.size());
    for (Ingredient ingredient : ingredients) {
      items.add(ingredient(ingredient, registries));
    }
    return items;
  }

  /** What a recipe makes, so the many ways a skill makes one item sit together. */
  private static String madeOf(RecipeView.Item result) {
    return result.icon().itemId() + "#" + result.icon().customModelData();
  }

  /** The effects a brew makes, so a brew and its stronger brew sit together and an elixir of several stands apart. */
  private static String brewGroup(BrewingRecipe brew) {
    TreeSet<String> types = new TreeSet<>();
    for (EffectInstance effect : brew.result().potionContents().map(PotionContents::customEffects).orElse(List.of())) {
      types.add(effect.type().toString());
    }
    return types.isEmpty() ? brew.id().toString() : String.join(",", types);
  }

  private static RecipeView.Item ingredient(Ingredient ingredient, RegistryLookup registries) {
    return switch (ingredient) {
      case Ingredient.AnyOf any -> plain(new TreeSet<>(any.types()).first(), 1);
      case Ingredient.Tag tag -> plain(tagMember(tag.tag(), registries), 1);
      case Ingredient.Exact exact -> item(exact.item().copyWithAmount(1), registries);
      case Ingredient.Matching matching -> item(matching.example().copyWithAmount(1), registries);
    };
  }

  /** A white member of the tag when it has one, as wool shows as white wool; otherwise its first member. */
  private static Id tagMember(Id tag, RegistryLookup registries) {
    TreeSet<Id> members = new TreeSet<>(registries.tagValues(RegistryKind.ITEM, tag));
    if (members.isEmpty()) {
      return tag;
    }
    for (Id member : members) {
      if (member.path().startsWith("white_")) {
        return member;
      }
    }
    return members.first();
  }

  private static RecipeView.Item plain(Id type, int count) {
    return new RecipeView.Item(IconRef.item(type.toString()), "", "", count, List.of());
  }

  /** A recipe's result, drawn with the client mod's own look when the recipe makes an Adapt item that has one. */
  private static RecipeView.Item result(RecipeSpec recipe, RegistryLookup registries) {
    RecipeView.Item item = item(recipe.result(), registries);
    MutationTool<?> tool = MutationTools.ofRecipe(recipe.key());
    String look = tool != null ? LegacyContentNames.of(tool.item().id()) : look(recipe.key().path());
    if (look.isEmpty() || !item.effects().isEmpty()) {
      return item;
    }
    IconRef icon = item.icon();
    String sprite = tool != null ? IconSprites.tool(tool.item().id()) : icon.spriteId();
    return new RecipeView.Item(new IconRef(icon.itemId(), ItemLooks.number(look), icon.itemModelId(), sprite), item.name(),
        item.nameKey(), item.count(), item.effects());
  }

  /** The longest {@link ItemLooks} key the recipe key names, as {@code tool-swords-whetstone-keen} names a whetstone. */
  static String look(String recipePath) {
    String found = "";
    for (ItemLooks.Look look : ItemLooks.LOOKS) {
      String key = look.key();
      boolean named = recipePath.equals(key) || recipePath.startsWith(key + "-") || recipePath.endsWith("-" + key);
      if (named && key.length() > found.length()) {
        found = key;
      }
    }
    return found;
  }

  private static RecipeView.Item item(ItemRef stack, RegistryLookup registries) {
    Optional<PotionContents> potion = stack.potionContents();
    if (potion.isPresent() && isPotion(stack.type())) {
      PotionContents contents = potion.get();
      if (contents.customEffects().isEmpty() && contents.potion() != null) {
        return potion(stack.type(), VANILLA_POTIONS.getOrDefault(contents.potion().path().replaceFirst("^(strong|long)_", ""), WATER),
            "", nameKey(stack.type(), contents.potion()), effects(registries.potionEffects(contents.potion()), registries));
      }
      String name = contents.name() != null ? contents.name() : stack.hasDisplayName() ? stack.displayName() : "";
      return potion(stack.type(), contents.color() == null ? WATER : contents.color().packed(), CatalogViews.plain(name),
          nameKey(stack.type(), contents.potion()), effects(contents.customEffects(), registries));
    }
    Id model = stack.itemModel();
    return new RecipeView.Item(new IconRef(stack.type().toString(), 0, model == null ? null : model.toString(), null),
        stack.hasDisplayName() ? CatalogViews.plain(stack.displayName()) : "", "", Math.max(1, stack.amount()), List.of());
  }

  private static boolean isPotion(Id type) {
    return type.namespace().equals(Id.MINECRAFT) && (type.path().equals("potion") || type.path().equals("splash_potion")
        || type.path().equals("lingering_potion"));
  }

  /** A bottle of {@code type} drawn from the vanilla bottle and its liquid tinted {@code color}. */
  private static RecipeView.Item potion(Id type, int color, String name, String nameKey, List<RecipeView.Effect> effects) {
    String sprite = IconSprites.stack(IconSprites.tinted(POTION_TEXTURES + "potion_overlay.png", color),
        POTION_TEXTURES + type.path() + ".png");
    return new RecipeView.Item(new IconRef(type.toString(), 0, null, sprite), name, nameKey, 1, effects);
  }

  /** The vanilla language key of a bottle of {@code type} holding {@code potion}, or of the bare bottle for none. */
  private static String nameKey(Id type, Id potion) {
    String bottle = "item." + type.namespace() + "." + type.path();
    return potion == null ? bottle : bottle + ".effect." + potion.path().replaceFirst("^(strong|long)_", "");
  }

  private static List<RecipeView.Effect> effects(List<EffectInstance> effects, RegistryLookup registries) {
    List<RecipeView.Effect> views = new ArrayList<>(effects.size());
    for (EffectInstance effect : effects) {
      views.add(new RecipeView.Effect(effect.type().toString(), effect.amplifier(),
          registries.isInstantEffect(effect.type()) ? 0 : effect.durationTicks(),
          registries.effectCategory(effect.type()) == EffectCategory.HARMFUL));
    }
    return views;
  }
}
