package art.arcane.adapt.api.golem.item;

import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.recipe.ExactIngredient;
import art.arcane.adapt.api.recipe.ExactIngredientBook;
import art.arcane.adapt.api.recipe.ExactItemResolver;
import art.arcane.adapt.api.recipe.RecipeTithe;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public final class GolemItems {
  public static final String NAMESPACE = "golemancy";

  private static final NamespacedKey KIND_KEY = new NamespacedKey("adapt", "golem_item");
  private static final NamespacedKey DATA_KEY = new NamespacedKey("adapt", "golem_item_data");
  private static final NamespacedKey CROOK_CHANNEL_KEY = new NamespacedKey("adapt", "crook_channel");
  private static final String MODEL_ROOT = "golemancy";
  private static final String FILLED_PIP = "◆";
  private static final String EMPTY_PIP = "◇";
  private static final Gson GSON = new Gson();
  private static final ExactIngredientBook RECIPE_BOOK = new ExactIngredientBook();
  private static final RecipeTithe RECIPE_TITHE = new RecipeTithe();
  private static final ExactItemResolver RESOLVER = new Resolver();
  private static final List<Material> COPPER_BLOCKS = List.of(
      Material.COPPER_BLOCK, Material.EXPOSED_COPPER, Material.WEATHERED_COPPER, Material.OXIDIZED_COPPER);
  private static final List<Material> WAXED_COPPER_BLOCKS = List.of(
      Material.WAXED_COPPER_BLOCK, Material.WAXED_EXPOSED_COPPER, Material.WAXED_WEATHERED_COPPER, Material.WAXED_OXIDIZED_COPPER);

  private GolemItems() {
  }

  public static ItemStack crook() {
    return build(new Spec(GolemItemKind.CROOK, baseMaterial(GolemItemKind.CROOK), GolemItemData.EMPTY,
        AdaptLanguage.text(GolemancyMessages.CROOK_NAME), List.of(AdaptLanguage.text(GolemancyMessages.CROOK_LORE))));
  }

  public static ItemStack blankCore() {
    return build(new Spec(GolemItemKind.BLANK_CORE, baseMaterial(GolemItemKind.BLANK_CORE), GolemItemData.EMPTY,
        AdaptLanguage.text(GolemancyMessages.BLANK_CORE_NAME), List.of(AdaptLanguage.text(GolemancyMessages.BLANK_CORE_LORE))));
  }

  public static ItemStack core(GolemCoreType role) {
    if (Objects.requireNonNull(role) == GolemCoreType.BLANK) {
      return blankCore();
    }
    String roleName = AdaptLanguage.text(role.displayName());
    return build(new Spec(GolemItemKind.CORE, baseMaterial(GolemItemKind.CORE), GolemItemData.role(role),
        AdaptLanguage.text(GolemancyMessages.CORE_NAME, trusted("role", roleName)),
        List.of(AdaptLanguage.text(GolemancyMessages.CORE_LORE, trusted("role", roleName)))));
  }

  public static ItemStack blankNode() {
    return build(new Spec(GolemItemKind.BLANK_NODE, baseMaterial(GolemItemKind.BLANK_NODE), GolemItemData.EMPTY,
        AdaptLanguage.text(GolemancyMessages.BLANK_NODE_NAME), List.of(AdaptLanguage.text(GolemancyMessages.BLANK_NODE_LORE))));
  }

  public static ItemStack node(NodeType type) {
    String nodeName = AdaptLanguage.text(Objects.requireNonNull(type).displayName());
    return build(new Spec(GolemItemKind.NODE, baseMaterial(GolemItemKind.NODE), GolemItemData.node(type),
        AdaptLanguage.text(GolemancyMessages.NODE_NAME, trusted("node", nodeName)),
        List.of(AdaptLanguage.text(GolemancyMessages.NODE_LORE))));
  }

  public static ItemStack blankRune() {
    return build(new Spec(GolemItemKind.BLANK_RUNE, baseMaterial(GolemItemKind.BLANK_RUNE), GolemItemData.EMPTY,
        AdaptLanguage.text(GolemancyMessages.BLANK_RUNE_NAME), List.of(AdaptLanguage.text(GolemancyMessages.BLANK_RUNE_LORE))));
  }

  public static ItemStack rune(GolemRune rune) {
    String aspectName = AdaptLanguage.text(Objects.requireNonNull(rune).aspect().displayName());
    return build(new Spec(GolemItemKind.RUNE, baseMaterial(GolemItemKind.RUNE), GolemItemData.rune(rune),
        AdaptLanguage.text(GolemancyMessages.RUNE_NAME, trusted("aspect", aspectName)),
        List.of(AdaptLanguage.text(GolemancyMessages.RUNE_TIER, trusted("tier", pips(rune.tier()))),
            AdaptLanguage.text(GolemancyMessages.RUNE_LORE))));
  }

  public static ItemStack dormantGolem(DormantGolem golem) {
    List<String> lore = new ArrayList<>(golem.cores().size() + 3);
    for (GolemCoreType core : golem.cores()) {
      lore.add(AdaptLanguage.text(GolemancyMessages.DORMANT_GOLEM_CORE, trusted("core", AdaptLanguage.text(core.displayName()))));
    }
    if (!golem.runes().isEmpty()) {
      lore.add(AdaptLanguage.text(GolemancyMessages.DORMANT_GOLEM_RUNES, trusted("runes", runeList(golem.runes()))));
    }
    lore.add(AdaptLanguage.text(GolemancyMessages.DORMANT_GOLEM_HEALTH,
        trusted("health", String.valueOf((int) Math.ceil(Math.max(0D, golem.health())))),
        trusted("maximum", String.valueOf(Math.round(Math.max(0D, golem.maxHealth()))))));
    lore.add(AdaptLanguage.text(GolemancyMessages.DORMANT_GOLEM_LORE));
    return build(new Spec(GolemItemKind.DORMANT_GOLEM, golem.block(), GolemItemData.packed(golem.packed()),
        AdaptLanguage.text(GolemancyMessages.DORMANT_GOLEM_NAME), lore));
  }

  public static ItemStack shatteredCore(PackedGolem golem) {
    return build(new Spec(GolemItemKind.SHATTERED_CORE, baseMaterial(GolemItemKind.SHATTERED_CORE), GolemItemData.packed(Objects.requireNonNull(golem)),
        AdaptLanguage.text(GolemancyMessages.SHATTERED_CORE_NAME), List.of(AdaptLanguage.text(GolemancyMessages.SHATTERED_CORE_LORE))));
  }

  public static ItemStack masterworkHeart() {
    return build(new Spec(GolemItemKind.MASTERWORK_HEART, baseMaterial(GolemItemKind.MASTERWORK_HEART), GolemItemData.EMPTY,
        AdaptLanguage.text(GolemancyMessages.MASTERWORK_HEART_NAME), List.of(AdaptLanguage.text(GolemancyMessages.MASTERWORK_HEART_LORE))));
  }

  public static ItemStack resolve(NamespacedKey id) {
    return variant(id).map(GolemItems::create).orElse(null);
  }

  public static ExactIngredient ingredient(NamespacedKey id) {
    return variant(id).map(GolemItems::ingredientFor).orElse(null);
  }

  public static ExactItemResolver resolver() {
    return RESOLVER;
  }

  public static ExactIngredientBook recipeBook() {
    return RECIPE_BOOK;
  }

  public static RecipeTithe recipeTithe() {
    return RECIPE_TITHE;
  }

  public static Material copperBlock(int oxidationStage, boolean waxed) {
    if (oxidationStage < 0 || oxidationStage >= COPPER_BLOCKS.size()) {
      throw new IllegalArgumentException("Copper oxidation stage must be 0 to 3, found " + oxidationStage);
    }
    return waxed ? WAXED_COPPER_BLOCKS.get(oxidationStage) : COPPER_BLOCKS.get(oxidationStage);
  }

  public static boolean isDormantBlock(Material material) {
    return material == Material.SNOW_BLOCK
        || material == Material.IRON_BLOCK
        || COPPER_BLOCKS.contains(material)
        || WAXED_COPPER_BLOCKS.contains(material);
  }

  public static Optional<GolemItemKind> kindOf(ItemStack item) {
    ItemMeta meta = metaOf(item);
    return meta == null ? Optional.empty() : kindOf(meta.getPersistentDataContainer());
  }

  public static boolean isGolemItem(ItemStack item) {
    return kindOf(item).isPresent();
  }

  public static Optional<GolemCoreType> coreOf(ItemStack item) {
    return read(item).flatMap(tag -> switch (tag.kind()) {
      case BLANK_CORE -> Optional.of(GolemCoreType.BLANK);
      case CORE -> Optional.ofNullable(tag.data().role()).filter(role -> role != GolemCoreType.BLANK);
      default -> Optional.empty();
    });
  }

  public static Optional<NodeType> nodeTypeOf(ItemStack item) {
    return read(item)
        .filter(tag -> tag.kind() == GolemItemKind.NODE)
        .map(tag -> tag.data().type());
  }

  public static Optional<GolemRune> runeOf(ItemStack item) {
    return read(item)
        .filter(tag -> tag.kind() == GolemItemKind.RUNE && tag.data().aspect() != null && GolemRuneType.isTier(tag.data().tier()))
        .map(tag -> new GolemRune(tag.data().aspect(), tag.data().tier()));
  }

  public static Optional<PackedGolem> packedGolemOf(ItemStack item) {
    return read(item)
        .filter(tag -> tag.kind() == GolemItemKind.DORMANT_GOLEM || tag.kind() == GolemItemKind.SHATTERED_CORE)
        .filter(tag -> tag.data().golem() != null && tag.data().owner() != null && tag.data().state() != null)
        .map(tag -> new PackedGolem(tag.data().golem(), tag.data().owner(), tag.data().state()));
  }

  public static Optional<DyeColor> crookChannel(ItemStack item) {
    ItemMeta meta = metaOf(item);
    if (meta == null) {
      return Optional.empty();
    }
    PersistentDataContainer container = meta.getPersistentDataContainer();
    if (kindOf(container).orElse(null) != GolemItemKind.CROOK) {
      return Optional.empty();
    }
    String stored = container.get(CROOK_CHANNEL_KEY, PersistentDataType.STRING);
    if (stored == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(DyeColor.valueOf(stored));
    } catch (IllegalArgumentException unknown) {
      return Optional.empty();
    }
  }

  public static boolean setCrookChannel(ItemStack item, DyeColor channel) {
    ItemMeta meta = metaOf(item);
    if (meta == null) {
      return false;
    }
    PersistentDataContainer container = meta.getPersistentDataContainer();
    if (kindOf(container).orElse(null) != GolemItemKind.CROOK) {
      return false;
    }
    if (channel == null) {
      container.remove(CROOK_CHANNEL_KEY);
    } else {
      container.set(CROOK_CHANNEL_KEY, PersistentDataType.STRING, channel.name());
    }
    return item.setItemMeta(meta);
  }

  private static ItemStack build(Spec spec) {
    ItemStack item = model(spec.kind(), spec.base()).toItemStack();
    ItemMeta meta = Objects.requireNonNull(item.getItemMeta());
    List<String> lore = new ArrayList<>(spec.lore().size());
    for (String line : spec.lore()) {
      lore.add(C.GRAY + line);
    }
    meta.setDisplayName(C.AQUA + spec.name());
    meta.setLore(lore);
    meta.setEnchantmentGlintOverride(true);
    meta.setMaxStackSize(spec.kind().maxStackSize());
    decorate(meta, spec);
    PersistentDataContainer container = meta.getPersistentDataContainer();
    container.set(KIND_KEY, PersistentDataType.STRING, spec.kind().id());
    if (!spec.data().isEmpty()) {
      container.set(DATA_KEY, PersistentDataType.STRING, GSON.toJson(spec.data()));
    }
    item.setItemMeta(meta);
    return item;
  }

  private static void decorate(ItemMeta meta, Spec spec) {
    switch (spec.kind()) {
      case CROOK -> {
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
      }
      case BLANK_CORE, CORE, SHATTERED_CORE -> {
        if (meta instanceof FireworkEffectMeta firework) {
          firework.setEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL).withColor(tint(spec)).build());
        }
      }
      default -> {
      }
    }
  }

  private static Color tint(Spec spec) {
    return switch (spec.kind()) {
      case SHATTERED_CORE -> Color.BLACK;
      case CORE -> roleTint(spec.data().role());
      default -> Color.GRAY;
    };
  }

  private static Color roleTint(GolemCoreType role) {
    return switch (role) {
      case BLANK -> Color.GRAY;
      case GUARDIAN -> Color.fromRGB(0x3C6FD1);
      case GATHERER -> Color.fromRGB(0xE0A526);
      case HARVESTER -> Color.fromRGB(0x5DB33A);
      case LUMBER -> Color.fromRGB(0x8A5A2E);
      case BREAKER -> Color.fromRGB(0xB83A3A);
      case RANCHER -> Color.fromRGB(0xF0E6C8);
      case SORTER -> Color.fromRGB(0xC46A3C);
      case ANGLER -> Color.fromRGB(0x2FA3A0);
    };
  }

  private static CustomModel model(GolemItemKind kind, Material base) {
    if (kind == GolemItemKind.DORMANT_GOLEM) {
      return CustomModel.get(base, MODEL_ROOT, kind.id(), base.name().toLowerCase(Locale.ROOT));
    }
    return CustomModel.get(base, MODEL_ROOT, kind.id());
  }

  private static Material baseMaterial(GolemItemKind kind) {
    return switch (kind) {
      case CROOK -> Material.IRON_HOE;
      case BLANK_CORE, CORE, SHATTERED_CORE -> Material.FIREWORK_STAR;
      case BLANK_NODE -> Material.CLAY_BALL;
      case NODE -> Material.BRICK;
      case BLANK_RUNE, RUNE -> Material.DISC_FRAGMENT_5;
      case MASTERWORK_HEART -> Material.HEART_OF_THE_SEA;
      case DORMANT_GOLEM -> throw new IllegalArgumentException("Dormant golems use the block of the packed golem");
    };
  }

  private static Optional<Variant> variant(NamespacedKey id) {
    if (id == null || !NAMESPACE.equals(id.getNamespace())) {
      return Optional.empty();
    }

    String[] parts = id.getKey().split("/", -1);
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

  private static ItemStack create(Variant variant) {
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
    Material base = model(variant.kind(), baseMaterial(variant.kind())).material();
    return new ExactIngredient(base, item -> isVariant(item, variant));
  }

  private static boolean isVariant(ItemStack item, Variant variant) {
    return read(item)
        .filter(tag -> tag.kind() == variant.kind() && tag.data().sameVariant(variant.data()))
        .isPresent();
  }

  private static String runeList(List<GolemRune> runes) {
    List<String> labels = new ArrayList<>(runes.size());
    for (GolemRune rune : runes) {
      labels.add(AdaptLanguage.text(rune.aspect().displayName()) + " " + pips(rune.tier()));
    }
    return String.join(", ", labels);
  }

  private static String pips(int tier) {
    return FILLED_PIP.repeat(tier) + EMPTY_PIP.repeat(GolemRuneType.MAX_TIER - tier);
  }

  private static ItemMeta metaOf(ItemStack item) {
    if (item == null || !item.hasItemMeta()) {
      return null;
    }
    return item.getItemMeta();
  }

  private static Optional<GolemItemKind> kindOf(PersistentDataContainer container) {
    String id = container.get(KIND_KEY, PersistentDataType.STRING);
    return id == null ? Optional.empty() : GolemItemKind.fromId(id);
  }

  private static Optional<Tag> read(ItemStack item) {
    ItemMeta meta = metaOf(item);
    if (meta == null) {
      return Optional.empty();
    }
    PersistentDataContainer container = meta.getPersistentDataContainer();
    return kindOf(container).map(kind -> new Tag(kind, dataOf(container)));
  }

  private static GolemItemData dataOf(PersistentDataContainer container) {
    String json = container.get(DATA_KEY, PersistentDataType.STRING);
    if (json == null) {
      return GolemItemData.EMPTY;
    }
    try {
      GolemItemData data = GSON.fromJson(json, GolemItemData.class);
      return data == null ? GolemItemData.EMPTY : data;
    } catch (JsonParseException malformed) {
      return GolemItemData.EMPTY;
    }
  }

  private record Spec(GolemItemKind kind, Material base, GolemItemData data, String name, List<String> lore) {
  }

  private record Tag(GolemItemKind kind, GolemItemData data) {
  }

  private record Variant(GolemItemKind kind, GolemItemData data) {
  }

  private static final class Resolver implements ExactItemResolver {
    @Override
    public ExactIngredient ingredient(NamespacedKey id) {
      return GolemItems.ingredient(id);
    }

    @Override
    public ItemStack result(NamespacedKey id) {
      return GolemItems.resolve(id);
    }
  }
}
