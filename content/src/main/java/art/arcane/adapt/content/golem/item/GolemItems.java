package art.arcane.adapt.content.golem.item;

import art.arcane.adapt.api.content.ContentIds;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.item.ExactIngredient;
import art.arcane.adapt.api.item.ExactItemResolver;
import art.arcane.adapt.api.item.ItemArt;
import art.arcane.adapt.api.item.VanillaUse;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.content.golem.GolemBody;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemMastery;
import art.arcane.adapt.content.golem.GolemRune;
import art.arcane.adapt.content.golem.GolemRuneType;
import art.arcane.adapt.content.golem.GolemRunes;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.content.data.Json;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.TooltipPart;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.api.text.TextFormat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;


public final class GolemItems {
  public static final String NAMESPACE = "golemancy";
  private static final Set<VanillaUse> GOLEM_USES = Set.of(VanillaUse.TRADE);
  private static final Set<VanillaUse> DORMANT_USES = Set.of(VanillaUse.USE, VanillaUse.PLACE, VanillaUse.TRADE);
  /** Golemancy's items for the item guard: traded freely, and a dormant golem may also be used and placed. */
  public static final CustomItem.Definition<CustomItem.Plain> ITEM = CustomItem.builder(Id.adapt("golemancy"), Id.minecraft("paper"))
      .recognisedBy((ItemRef item) -> kindOf(item).isPresent())
      .allowPerStack((ItemRef item) -> kindOf(item).orElse(null) == GolemItemKind.DORMANT_GOLEM ? DORMANT_USES : GOLEM_USES)
      .consumableBy(GolemItemGuard::isGolemancyRecipe)
      .claimsGestures((ItemRef item) -> kindOf(item).orElse(null) == GolemItemKind.CROOK)
      .build();

  private static final DataKey<String> KIND_KEY = DataKey.of(Id.of("adapt", "golem_item"), DataCodecs.STRING);
  private static final DataKey<String> DATA_KEY = DataKey.of(Id.of("adapt", "golem_item_data"), DataCodecs.STRING);
  private static final DataKey<String> CROOK_CHANNEL_KEY = DataKey.of(Id.of("adapt", "crook_channel"), DataCodecs.STRING);
  private static final String MODEL_ROOT = "golemancy";
  private static final String BLANK_RUNE_MODEL = "blank";
  private static final String FILLED_PIP = "◆";
  private static final String EMPTY_PIP = "◇";
  private static final ExactIngredientBook RECIPE_BOOK = new ExactIngredientBook();
  private static final RecipeCraftCounter CRAFT_COUNTER = new RecipeCraftCounter();
  private static final ExactItemResolver RESOLVER = new Resolver();
  private static final Id SNOW_BLOCK = Id.minecraft("snow_block");
  private static final Id IRON_BLOCK = Id.minecraft("iron_block");
  private static final Id BONE_BLOCK = Id.minecraft("bone_block");
  private static final Id FIREWORK_STAR = Id.minecraft("firework_star");
  private static final Rgb GRAY = Rgb.of(0x808080);
  private static final Rgb BLACK = Rgb.of(0x000000);
  private static final List<Id> COPPER_BLOCKS = List.of(
      Id.minecraft("copper_block"), Id.minecraft("exposed_copper"), Id.minecraft("weathered_copper"), Id.minecraft("oxidized_copper"));
  private static final List<Id> WAXED_COPPER_BLOCKS = List.of(
      Id.minecraft("waxed_copper_block"), Id.minecraft("waxed_exposed_copper"), Id.minecraft("waxed_weathered_copper"),
      Id.minecraft("waxed_oxidized_copper"));
  private static volatile ModelMaterials ownerBoundMaterials;

  private GolemItems() {
  }

  public static ItemRef crook() {
    return build(new Spec(GolemItemKind.CROOK, baseMaterial(GolemItemKind.CROOK), GolemItemData.EMPTY,
        Text.key("adapt.golemancy.item.crook.name").resolve(), List.of(Text.key("adapt.golemancy.help.crook.manage").resolve(),
            Text.key("adapt.golemancy.help.crook.roster").resolve(), Text.key("adapt.golemancy.help.crook.areas").resolve(),
            Text.key("adapt.golemancy.help.crook.shift").resolve(), Text.key("adapt.golemancy.help.crook.scroll").resolve())));
  }

  public static ItemRef blankCore() {
    return build(new Spec(GolemItemKind.BLANK_CORE, baseMaterial(GolemItemKind.BLANK_CORE), GolemItemData.EMPTY,
        Text.key("adapt.golemancy.item.blank_core.name").resolve(), List.of(Text.key("adapt.golemancy.help.core.companion").resolve(),
            Text.key("adapt.golemancy.help.core.craft").resolve(), Text.key("adapt.golemancy.help.core.bind").resolve())));
  }

  public static ItemRef core(GolemCoreType role) {
    if (Objects.requireNonNull(role) == GolemCoreType.BLANK) {
      return blankCore();
    }
    String roleName = role.displayName().resolve();
    return build(new Spec(GolemItemKind.CORE, baseMaterial(GolemItemKind.CORE), GolemItemData.role(role),
        Text.key("adapt.golemancy.item.core.name").withMarkup("role", String.valueOf(roleName)).resolve(),
        List.of(Text.key("adapt.golemancy.item.core.lore").withMarkup("role", String.valueOf(roleName)).resolve(),
            Text.key("adapt.golemancy.help.core.bind").resolve())));
  }

  public static ItemRef blankNode() {
    return build(new Spec(GolemItemKind.BLANK_NODE, baseMaterial(GolemItemKind.BLANK_NODE), GolemItemData.EMPTY,
        Text.key("adapt.golemancy.item.blank_node.name").resolve(), List.of(Text.key("adapt.golemancy.item.blank_node.lore").resolve())));
  }

  public static ItemRef node(NodeType type) {
    String nodeName = Objects.requireNonNull(type).displayName().resolve();
    return build(new Spec(GolemItemKind.NODE, baseMaterial(GolemItemKind.NODE), GolemItemData.node(type),
        Text.key("adapt.golemancy.item.node.name").withMarkup("node", String.valueOf(nodeName)).resolve(),
        List.of(Text.key("adapt.golemancy.item.node.lore").resolve())));
  }

  public static ItemRef blankRune() {
    return build(new Spec(GolemItemKind.BLANK_RUNE, baseMaterial(GolemItemKind.BLANK_RUNE), GolemItemData.EMPTY,
        Text.key("adapt.golemancy.item.blank_rune.name").resolve(), List.of(Text.key("adapt.golemancy.item.blank_rune.lore").resolve())));
  }

  public static ItemRef rune(GolemRune rune) {
    String aspectName = Objects.requireNonNull(rune).aspect().displayName().resolve();
    List<String> lore = new ArrayList<>();
    lore.add(Text.key("adapt.golemancy.item.rune.tier").withMarkup("tier", String.valueOf(pips(rune.tier()))).resolve());
    lore.addAll(GolemRunes.describe(rune, GolemMastery.Perks.NONE));
    lore.add(Text.key("adapt.golemancy.item.rune.lore").resolve());
    return build(new Spec(GolemItemKind.RUNE, runeBase(rune.aspect()), GolemItemData.rune(rune),
        Text.key("adapt.golemancy.item.rune.name").withMarkup("aspect", String.valueOf(aspectName)).resolve(), lore));
  }

  public static ItemRef dormantGolem(DormantGolem golem) {
    List<String> lore = new ArrayList<>(golem.cores().size() + 3);
    for (GolemCoreType core : golem.cores()) {
      lore.add(Text.key("adapt.golemancy.item.dormant_golem.core").with("core", core.displayName()).resolve());
    }
    if (!golem.runes().isEmpty()) {
      lore.add(Text.key("adapt.golemancy.item.dormant_golem.runes").withMarkup("runes", String.valueOf(runeList(golem.runes()))).resolve());
    }
    lore.add(Text.key("adapt.golemancy.item.dormant_golem.health").withMarkup("health", String.valueOf((int) Math.ceil(Math.max(0D, golem.health())))).withMarkup("maximum", String.valueOf(Math.round(Math.max(0D, golem.maxHealth())))).resolve());
    lore.add(Text.key("adapt.golemancy.item.dormant_golem.lore").resolve());
    return build(new Spec(GolemItemKind.DORMANT_GOLEM, golem.block(), GolemItemData.packed(golem.packed()),
        Text.key("adapt.golemancy.item.dormant_golem.name").resolve(), lore));
  }

  public static ItemRef shatteredCore(PackedGolem golem) {
    return build(new Spec(GolemItemKind.SHATTERED_CORE, baseMaterial(GolemItemKind.SHATTERED_CORE), GolemItemData.packed(Objects.requireNonNull(golem)),
        Text.key("adapt.golemancy.item.shattered_core.name").resolve(), List.of(Text.key("adapt.golemancy.item.shattered_core.lore").resolve())));
  }

  public static ItemRef masterworkHeart() {
    return build(new Spec(GolemItemKind.MASTERWORK_HEART, baseMaterial(GolemItemKind.MASTERWORK_HEART), GolemItemData.EMPTY,
        Text.key("adapt.golemancy.item.masterwork_heart.name").resolve(), List.of(Text.key("adapt.golemancy.item.masterwork_heart.lore").resolve())));
  }

  public static ItemRef resolve(Id id) {
    return variant(id).map(GolemItems::create).orElse(null);
  }

  public static ExactIngredient ingredient(Id id) {
    return variant(id).map(GolemItems::ingredientFor).orElse(null);
  }

  public static ExactItemResolver resolver() {
    return RESOLVER;
  }

  public static ExactIngredientBook recipeBook() {
    return RECIPE_BOOK;
  }

  public static RecipeCraftCounter craftCounter() {
    return CRAFT_COUNTER;
  }

  public static Id copperBlock(int oxidationStage, boolean waxed) {
    if (oxidationStage < 0 || oxidationStage >= COPPER_BLOCKS.size()) {
      throw new IllegalArgumentException("Copper oxidation stage must be 0 to 3, found " + oxidationStage);
    }
    return waxed ? WAXED_COPPER_BLOCKS.get(oxidationStage) : COPPER_BLOCKS.get(oxidationStage);
  }

  public static Id dormantBlock(GolemBody body, int copperStage, boolean waxed) {
    return switch (body) {
      case FROST -> SNOW_BLOCK;
      case COPPER -> copperBlock(Math.max(0, Math.min(COPPER_BLOCKS.size() - 1, copperStage)), waxed);
      case IRON -> IRON_BLOCK;
      case BONE -> BONE_BLOCK;
    };
  }

  public static boolean isDormantBlock(Id material) {
    return SNOW_BLOCK.equals(material)
        || BONE_BLOCK.equals(material)
        || IRON_BLOCK.equals(material)
        || COPPER_BLOCKS.contains(material)
        || WAXED_COPPER_BLOCKS.contains(material);
  }

  public static boolean mayBeOwnerBound(Id material) {
    return FIREWORK_STAR.equals(material) || isDormantBlock(material) || ownerBoundMaterials().contains(material);
  }

  public static Optional<GolemItemKind> kindOf(ItemRef item) {
    DataHolder data = dataOf(item);
    return data == null ? Optional.empty() : kindOf(data);
  }

  public static boolean isGolemItem(ItemRef item) {
    return kindOf(item).isPresent();
  }

  public static Optional<GolemCoreType> coreOf(ItemRef item) {
    return read(item).flatMap(tag -> switch (tag.kind()) {
      case BLANK_CORE -> Optional.of(GolemCoreType.BLANK);
      case CORE -> Optional.ofNullable(tag.data().role()).filter(role -> role != GolemCoreType.BLANK);
      default -> Optional.empty();
    });
  }

  public static Optional<NodeType> nodeTypeOf(ItemRef item) {
    return read(item)
        .filter(tag -> tag.kind() == GolemItemKind.NODE)
        .map(tag -> tag.data().type());
  }

  public static Optional<GolemRune> runeOf(ItemRef item) {
    return read(item)
        .filter(tag -> tag.kind() == GolemItemKind.RUNE && tag.data().aspect() != null && GolemRuneType.isTier(tag.data().tier()))
        .map(tag -> new GolemRune(tag.data().aspect(), tag.data().tier()));
  }

  public static Optional<PackedGolem> packedGolemOf(ItemRef item) {
    return read(item)
        .filter(tag -> tag.kind() == GolemItemKind.DORMANT_GOLEM || tag.kind() == GolemItemKind.SHATTERED_CORE)
        .filter(tag -> tag.data().golem() != null && tag.data().owner() != null && tag.data().state() != null)
        .map(tag -> new PackedGolem(tag.data().golem(), tag.data().owner(), tag.data().state()));
  }

  public static Optional<DyeColor> crookChannel(ItemRef item) {
    DataHolder container = dataOf(item);
    if (container == null) {
      return Optional.empty();
    }
    if (kindOf(container).orElse(null) != GolemItemKind.CROOK) {
      return Optional.empty();
    }
    String stored = container.get(CROOK_CHANNEL_KEY);
    if (stored == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(DyeColor.valueOf(stored));
    } catch (IllegalArgumentException unknown) {
      return Optional.empty();
    }
  }

  public static boolean setCrookChannel(ItemRef item, DyeColor channel) {
    DataHolder container = dataOf(item);
    if (container == null) {
      return false;
    }
    if (kindOf(container).orElse(null) != GolemItemKind.CROOK) {
      return false;
    }
    if (channel == null) {
      container.remove(CROOK_CHANNEL_KEY);
    } else {
      container.set(CROOK_CHANNEL_KEY, channel.name());
    }
    return true;
  }

  private static ItemRef build(Spec spec) {
    ItemRef item = model(spec.kind(), spec.base(), spec.data());
    List<String> lore = new ArrayList<>(spec.lore().size());
    for (String line : spec.lore()) {
      lore.add(TextFormat.GRAY + line);
    }
    item.setDisplayName(TextFormat.AQUA + spec.name());
    item.setLore(lore);
    item.setGlintOverride(true);
    item.setMaxStackSize(spec.kind().maxStackSize());
    item.hideTooltip(TooltipPart.ADDITIONAL);
    decorate(item, spec);
    DataHolder container = item.data();
    container.set(KIND_KEY, spec.kind().id());
    if (!spec.data().isEmpty()) {
      container.set(DATA_KEY, Json.write(spec.data()));
    }
    return item;
  }

  private static void decorate(ItemRef item, Spec spec) {
    switch (spec.kind()) {
      case CROOK -> {
        item.setUnbreakable(true);
        item.hideTooltip(TooltipPart.ATTRIBUTES);
        item.hideTooltip(TooltipPart.UNBREAKABLE);
      }
      case BLANK_CORE, CORE, SHATTERED_CORE -> item.setFireworkStarColor(tint(spec));
      default -> {
      }
    }
  }

  private static Rgb tint(Spec spec) {
    return switch (spec.kind()) {
      case SHATTERED_CORE -> BLACK;
      case CORE -> roleTint(spec.data().role());
      default -> GRAY;
    };
  }

  private static Rgb roleTint(GolemCoreType role) {
    return switch (role) {
      case BLANK -> GRAY;
      case GUARDIAN -> Rgb.of(0x3C6FD1);
      case GATHERER -> Rgb.of(0xE0A526);
      case HARVESTER -> Rgb.of(0x5DB33A);
      case LUMBER -> Rgb.of(0x8A5A2E);
      case BREAKER -> Rgb.of(0xB83A3A);
      case RANCHER -> Rgb.of(0xF0E6C8);
      case SORTER -> Rgb.of(0xC46A3C);
      case ANGLER -> Rgb.of(0x2FA3A0);
    };
  }

  private static ItemRef model(GolemItemKind kind, Id base, GolemItemData data) {
    Id fallback = base;
    return switch (kind) {
      case DORMANT_GOLEM -> ItemArt.modelled(fallback, MODEL_ROOT, kind.id(), base.path().toLowerCase(Locale.ROOT));
      case BLANK_RUNE -> ItemArt.modelled(fallback, MODEL_ROOT, GolemItemKind.RUNE.id(), BLANK_RUNE_MODEL);
      case RUNE -> ItemArt.modelled(fallback, MODEL_ROOT, kind.id(), data.aspect().id());
      default -> ItemArt.modelled(fallback, MODEL_ROOT, kind.id());
    };
  }

  private static Id modelType(GolemItemKind kind, Id base, GolemItemData data) {
    ItemRef modelled = model(kind, base, data);
    return modelled == null || modelled.isEmpty() ? base : modelled.type();
  }

  private static Id baseMaterial(GolemItemKind kind) {
    return switch (kind) {
      case CROOK -> Id.minecraft("iron_hoe");
      case BLANK_CORE, CORE, SHATTERED_CORE -> FIREWORK_STAR;
      case BLANK_NODE -> Id.minecraft("clay_ball");
      case NODE -> Id.minecraft("brick");
      case BLANK_RUNE -> ContentIds.itemOr(Id.minecraft("scrape_pottery_sherd"), Id.minecraft("plenty_pottery_sherd"));
      case MASTERWORK_HEART -> Id.minecraft("heart_of_the_sea");
      case RUNE -> throw new IllegalArgumentException("Runes use the pottery sherd of their aspect");
      case DORMANT_GOLEM -> throw new IllegalArgumentException("Dormant golems use the block of the packed golem");
    };
  }

  private static Id runeBase(GolemRuneType aspect) {
    return switch (Objects.requireNonNull(aspect)) {
      case SWIFTNESS -> ContentIds.itemOr(Id.minecraft("guster_pottery_sherd"), Id.minecraft("explorer_pottery_sherd"));
      case MIGHT -> Id.minecraft("arms_up_pottery_sherd");
      case EMBER -> Id.minecraft("burn_pottery_sherd");
      case TIDE -> Id.minecraft("angler_pottery_sherd");
      case ORDER -> Id.minecraft("sheaf_pottery_sherd");
      case ENTROPY -> Id.minecraft("danger_pottery_sherd");
      case BULWARK -> Id.minecraft("shelter_pottery_sherd");
    };
  }

  private static Set<Id> ownerBoundMaterials() {
    long generation = ItemArt.revision();
    ModelMaterials cached = ownerBoundMaterials;
    if (cached != null && cached.generation() == generation) {
      return cached.materials();
    }
    Set<Id> materials = new HashSet<>();
    materials.add(modelType(GolemItemKind.SHATTERED_CORE, baseMaterial(GolemItemKind.SHATTERED_CORE), GolemItemData.EMPTY));
    List<Id> blocks = new ArrayList<>(COPPER_BLOCKS.size() + WAXED_COPPER_BLOCKS.size() + 2);
    blocks.add(SNOW_BLOCK);
    blocks.add(IRON_BLOCK);
    blocks.addAll(COPPER_BLOCKS);
    blocks.addAll(WAXED_COPPER_BLOCKS);
    for (Id block : blocks) {
      materials.add(modelType(GolemItemKind.DORMANT_GOLEM, block, GolemItemData.EMPTY));
    }
    ModelMaterials next = new ModelMaterials(generation, Set.copyOf(materials));
    ownerBoundMaterials = next;
    return next.materials();
  }

  private static Optional<Variant> variant(Id id) {
    if (id == null || !NAMESPACE.equals(id.namespace())) {
      return Optional.empty();
    }
    String[] parts = id.path().split("/", -1);
    Optional<GolemItemKind> kind = GolemItemKind.fromId(parts[0]);
    if (kind.isEmpty()) {
      return Optional.empty();
    }
    return switch (kind.get()) {
      case CROOK, BLANK_CORE, BLANK_NODE, BLANK_RUNE -> parts.length == 1 ? Optional.of(new Variant(kind.get(), GolemItemData.EMPTY)) : Optional.empty();
      case CORE -> parts.length == 2
          ? GolemCoreType.fromId(parts[1]).filter(role -> role != GolemCoreType.BLANK).map(role -> new Variant(GolemItemKind.CORE, GolemItemData.role(role)))
          : Optional.empty();
      case NODE -> parts.length == 2
          ? NodeType.fromId(parts[1]).map(type -> new Variant(GolemItemKind.NODE, GolemItemData.node(type)))
          : Optional.empty();
      case RUNE -> parts.length == 3
          ? runeVariant(parts[1], parts[2]).map(rune -> new Variant(GolemItemKind.RUNE, GolemItemData.rune(rune)))
          : Optional.empty();
      case DORMANT_GOLEM, SHATTERED_CORE, MASTERWORK_HEART -> Optional.empty();
    };
  }

  private static Optional<GolemRune> runeVariant(String aspect, String tier) {
    int parsedTier = tier.length() == 1 ? tier.charAt(0) - '0' : -1;
    if (!GolemRuneType.isTier(parsedTier)) {
      return Optional.empty();
    }
    return GolemRuneType.fromId(aspect).map(type -> new GolemRune(type, parsedTier));
  }

  private static ItemRef create(Variant variant) {
    GolemItemData data = variant.data();
    return switch (variant.kind()) {
      case CROOK -> crook();
      case BLANK_CORE -> blankCore();
      case CORE -> core(data.role());
      case BLANK_NODE -> blankNode();
      case NODE -> node(data.type());
      case BLANK_RUNE -> blankRune();
      case RUNE -> rune(new GolemRune(data.aspect(), data.tier()));
      case DORMANT_GOLEM, SHATTERED_CORE, MASTERWORK_HEART -> throw new IllegalArgumentException("No shared recipe item for " + variant.kind().id());
    };
  }

  private static ExactIngredient ingredientFor(Variant variant) {
    Id fallback = variant.kind() == GolemItemKind.RUNE ? runeBase(variant.data().aspect()) : baseMaterial(variant.kind());
    Id base = modelType(variant.kind(), fallback, variant.data());
    return new ExactIngredient(base, (ItemRef item) -> isVariant(item, variant));
  }

  private static boolean isVariant(ItemRef item, Variant variant) {
    return read(item)
        .filter(tag -> tag.kind() == variant.kind() && tag.data().sameVariant(variant.data()))
        .isPresent();
  }

  private static String runeList(List<GolemRune> runes) {
    List<String> labels = new ArrayList<>(runes.size());
    for (GolemRune rune : runes) {
      labels.add(rune.aspect().displayName().resolve() + " " + pips(rune.tier()));
    }
    return String.join(", ", labels);
  }

  private static String pips(int tier) {
    return FILLED_PIP.repeat(tier) + EMPTY_PIP.repeat(GolemRuneType.MAX_TIER - tier);
  }

  private static DataHolder dataOf(ItemRef item) {
    if (item == null || item.isEmpty()) {
      return null;
    }
    return item.data();
  }

  private static Optional<GolemItemKind> kindOf(DataHolder container) {
    String id = container.get(KIND_KEY);
    return id == null ? Optional.empty() : GolemItemKind.fromId(id);
  }

  private static Optional<Tag> read(ItemRef item) {
    DataHolder container = dataOf(item);
    if (container == null) {
      return Optional.empty();
    }
    return kindOf(container).map((GolemItemKind kind) -> new Tag(kind, golemData(container)));
  }

  private static GolemItemData golemData(DataHolder container) {
    String json = container.get(DATA_KEY);
    if (json == null) {
      return GolemItemData.EMPTY;
    }
    try {
      GolemItemData data = Json.read(json, GolemItemData.class);
      return data == null ? GolemItemData.EMPTY : data;
    } catch (IllegalArgumentException malformed) {
      return GolemItemData.EMPTY;
    }
  }

  private record Spec(GolemItemKind kind, Id base, GolemItemData data, String name, List<String> lore) {
  }

  private record Tag(GolemItemKind kind, GolemItemData data) {
  }

  private record ModelMaterials(long generation, Set<Id> materials) {
  }

  private record Variant(GolemItemKind kind, GolemItemData data) {
  }

  private static final class Resolver implements ExactItemResolver {
    @Override
    public ExactIngredient ingredient(Id id) {
      return GolemItems.ingredient(id);
    }

    @Override
    public ItemRef result(Id id) {
      return GolemItems.resolve(id);
    }
  }
}
