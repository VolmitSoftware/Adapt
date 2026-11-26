package art.arcane.adapt.api.mutation.tool;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.item.VanillaUse;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.ItemLooks;
import art.arcane.volmlib.util.localization.TextKey;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A craftable item handed out by an upgrade or a signature (its owner). The item is a plain vanilla material marked
 * with data on the stack, so no registry entry exists and vanilla clients can join. It works only for a player whose
 * owner level reaches {@link #requiredLevel()}; for everyone else, and for its own vanilla action, it is inert
 * ({@link MutationToolGuard}). Its recipes are registered on the owner by {@link ToolOwner} at the required level, so
 * nobody can craft a tool they cannot use.
 *
 * <p>Subclasses pass a literal id {@code tool-<skill>-<slug>}, the owner, the material, the required level, the data
 * class (a Gson-serialisable class, or {@link NoData}) and the name and lore keys from the skill's message class, then
 * implement {@link #recipes()} and {@link #use(ToolUse)}.
 */
public abstract class MutationTool<D> {
  public static final String ID_PREFIX = "tool-";
  static final DataKey<String> TOOL_KEY = DataKey.adapt("mutation_tool", DataCodecs.STRING);
  private static final DataKey<String> DATA_KEY = DataKey.adapt("mutation_tool_data", DataCodecs.STRING);
  private static final Gson GSON = new Gson();
  private static final Set<Id> MATERIALS = ConcurrentHashMap.newKeySet();

  private final String id;
  private final Adaptation<?> owner;
  private final Id material;
  private final int requiredLevel;
  private final Class<D> dataType;
  private final TextKey name;
  private final List<TextKey> lore;

  protected MutationTool(String id, Adaptation<?> owner, Id material, int requiredLevel, Class<D> dataType,
                         TextKey name, TextKey... lore) {
    if (id == null || !id.startsWith(ID_PREFIX)) {
      throw new IllegalArgumentException("A mutation tool id starts with " + ID_PREFIX + ": " + id);
    }
    this.id = id;
    this.owner = Objects.requireNonNull(owner);
    this.material = Objects.requireNonNull(material);
    MATERIALS.add(material);
    this.requiredLevel = Math.max(1, requiredLevel);
    this.dataType = Objects.requireNonNull(dataType);
    this.name = Objects.requireNonNull(name);
    this.lore = List.of(lore);
  }

  /** Data of a tool that stores nothing. */
  public static final class NoData {
  }

  /** The recipes that make this tool; their results are {@link #create} stacks and their keys start with the id. */
  public abstract List<RecipeSpec> recipes();

  /** A holder at or above the required level used the tool in the main hand; its vanilla action is already denied. */
  protected abstract void use(ToolUse use);

  /** Whether a holder's use also runs the material's vanilla action (a fishing rod's cast, a horn's sound). */
  protected boolean keepsVanillaUse() {
    return false;
  }

  /** The vanilla interactions this tool is built for: its material's own use when {@link #keepsVanillaUse()}. */
  public final Set<VanillaUse> vanillaUses() {
    return keepsVanillaUse() ? Set.of(VanillaUse.USE) : Set.of();
  }

  /** Extra lore lines from the stored data, such as a captured pattern's size. */
  protected void applyLore(D data, List<String> lines) {
  }

  protected int maxStackSize() {
    return 1;
  }

  public final String id() {
    return id;
  }

  public final Adaptation<?> owner() {
    return owner;
  }

  public final Id material() {
    return material;
  }

  public final int requiredLevel() {
    return requiredLevel;
  }

  public final TextKey nameKey() {
    return name;
  }

  public final List<TextKey> loreKeys() {
    return lore;
  }

  /** The owner's active level for the player when it reaches the required level, else 0. */
  public final int level(PlayerRef player) {
    int level = owner.getActiveLevel(player);
    return level >= requiredLevel ? level : 0;
  }

  /** A fresh stack of this tool holding {@code data}. */
  public final ItemRef create(D data) {
    ItemRef item = AdaptCore.platform().host().items().create(material, 1);
    item.setMaxStackSize(maxStackSize());
    item.setGlintOverride(true);
    ItemLooks.apply(item, id);
    item.data().set(TOOL_KEY, id);
    write(item, data);
    return item;
  }

  public final boolean is(@Nullable ItemRef item) {
    return id.equals(idOf(item));
  }

  /** The stored data, or null when the stack is not this tool or its data cannot be read. */
  public final @Nullable D read(@Nullable ItemRef item) {
    if (!is(item)) {
      return null;
    }
    String raw = item.data().get(DATA_KEY);
    if (raw == null) {
      return null;
    }
    try {
      return GSON.fromJson(raw, dataType);
    } catch (JsonParseException invalid) {
      return null;
    }
  }

  /** Replaces the stored data and lore on {@code item} in place; false when it is not this tool. Write the stack back to its slot. */
  public final boolean write(ItemRef item, D data) {
    if (!id.equals(item.data().get(TOOL_KEY))) {
      return false;
    }
    item.setDisplayName(C.AQUA + AdaptLanguage.text(name));
    List<String> lines = new ArrayList<>(lore.size() + 2);
    for (TextKey line : lore) {
      lines.add(C.GRAY + AdaptLanguage.text(line));
    }
    applyLore(data, lines);
    item.setLore(lines);
    item.data().set(DATA_KEY, GSON.toJson(data));
    return true;
  }

  /** The id of the mutation tool a stack is, or null. */
  public static @Nullable String idOf(@Nullable ItemRef item) {
    if (item == null || item.isEmpty()) {
      return null;
    }
    Id type = item.type();
    return type == null || !MATERIALS.contains(type) ? null : item.data().get(TOOL_KEY);
  }

  void dispatch(ToolUse use) {
    use(use);
  }

  boolean vanillaUseFor(int level) {
    return level > 0 && keepsVanillaUse();
  }
}
