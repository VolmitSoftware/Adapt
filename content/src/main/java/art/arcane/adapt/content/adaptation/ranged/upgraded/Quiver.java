package art.arcane.adapt.content.adaptation.ranged.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.ToolUse;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.ranged.RangedProjectiles;
import art.arcane.adapt.content.item.ToolItems;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stormshot's Quiver: holds plain, spectral, Snare and Ricochet arrows by count, takes back a share of the arrows that
 * hit, and restocks the inventory when a kind runs out. Inert, contents kept, for anyone without Stormshot.
 */
public class Quiver extends MutationTool<Quiver.Data> {
  public static final Id ID = Id.adapt("ranged/tool/quiver");
  private static final int STACK = 64;

  private final Stormshot owner;

  public Quiver(Stormshot owner) {
    super(ToolItems.builder(ID, Id.minecraft("rabbit_hide"), Data.class, new Data()).build(), owner, 1);
    this.owner = owner;
  }

  /** The arrow kinds a Quiver keeps; tipped arrows and other plugins' arrows are left alone. */
  public enum Kind {
    ARROW,
    SPECTRAL,
    SNARE,
    RICOCHET;

    /** One fresh arrow of this kind, or null when its tool is not registered. */
    public @Nullable ItemRef unit() {
      return switch (this) {
        case ARROW -> AdaptApi.platform().host().items().create(RangedProjectiles.ARROW, 1);
        case SPECTRAL -> AdaptApi.platform().host().items().create(RangedProjectiles.SPECTRAL_ARROW, 1);
        case SNARE -> toolUnit(SnareArrow.ID);
        case RICOCHET -> toolUnit(RicochetArrow.ID);
      };
    }

    /** The kind of {@code stack}, or null when a Quiver does not hold it. */
    public static @Nullable Kind of(@Nullable ItemRef stack) {
      if (stack == null || stack.isEmpty()) {
        return null;
      }
      MutationTool<?> tool = Mutations.tool(stack).orElse(null);
      if (tool != null) {
        Id id = tool.item().id();
        return SnareArrow.ID.equals(id) ? SNARE : RicochetArrow.ID.equals(id) ? RICOCHET : null;
      }
      Kind plain = stack.type().equals(RangedProjectiles.ARROW) ? ARROW
          : stack.type().equals(RangedProjectiles.SPECTRAL_ARROW) ? SPECTRAL : null;
      return plain != null && PlainUnits.current().of(plain).isSimilar(stack) ? plain : null;
    }

    private static @Nullable ItemRef toolUnit(Id id) {
      MutationTool<?> tool = Mutations.tool(id).orElse(null);
      return tool instanceof MunitionArrow arrow ? arrow.create(CustomItem.Plain.INSTANCE) : null;
    }
  }

  private record PlainUnits(ItemOps items, ItemRef arrow, ItemRef spectral) {
    private static volatile PlainUnits cached;

    private static PlainUnits current() {
      ItemOps items = AdaptApi.platform().host().items();
      PlainUnits units = cached;
      if (units == null || units.items != items) {
        units = new PlainUnits(items, items.create(RangedProjectiles.ARROW, 1),
            items.create(RangedProjectiles.SPECTRAL_ARROW, 1));
        cached = units;
      }
      return units;
    }

    private ItemRef of(Kind plain) {
      return plain == Kind.SPECTRAL ? spectral : arrow;
    }
  }

  public static final class Data {
    Map<String, Integer> arrows = new LinkedHashMap<>();

    int stored() {
      int total = 0;
      for (int count : arrows.values()) {
        total += count;
      }
      return total;
    }

    int count(Kind kind) {
      return arrows.getOrDefault(kind.name(), 0);
    }

    void add(Kind kind, int amount) {
      int next = count(kind) + amount;
      if (next <= 0) {
        arrows.remove(kind.name());
      } else {
        arrows.put(kind.name(), next);
      }
    }
  }

  @Override
  public List<RecipeSpec> recipes() {
    return List.of(RecipeSpec.shapeless(ID, List.of(Ingredient.of(Id.minecraft("leather")),
        Ingredient.of(Id.minecraft("string")), Ingredient.of(RangedProjectiles.ARROW)), create(new Data())).withRequiredLevel(1));
  }

  @Override
  public void use(ToolUse use) {
    if (!use.rightClick()) {
      return;
    }
    PlayerRef player = use.player();
    Data data = read(use.item()).orElse(null);
    if (data == null) {
      return;
    }
    if (use.sneaking()) {
      withdrawAll(player, data);
    } else {
      depositAll(player, data, capacity(use.level()));
    }
    write(use.item(), data);
    player.inventory().setMainHand(use.item());
    HudStatus.show(player, TextFormat.AQUA + Text.key("adapt.tool.ranged.quiver.name").resolve() + TextFormat.GRAY + ": " + TextFormat.WHITE
        + data.stored() + TextFormat.GRAY + " / " + capacity(use.level()));
  }

  @Override
  public void applyLore(Data data, List<String> lines) {
    for (Kind kind : Kind.values()) {
      int count = data.count(kind);
      if (count > 0) {
        lines.add(TextFormat.GRAY + label(kind).resolve() + ": " + TextFormat.WHITE + count);
      }
    }
  }

  /** Arrows a Quiver holds for a holder at {@code level}. */
  public int capacity(int level) {
    return owner.getQuiverStacks(level) * STACK;
  }

  /**
   * Puts {@code amount} arrows of {@code kind} into the first Quiver in the inventory with room, all or none. False when
   * the player holds no Quiver they can use or none has room.
   */
  public boolean store(PlayerRef player, Kind kind, int amount) {
    int level = level(player);
    if (level <= 0) {
      return false;
    }
    InventoryRef inventory = player.inventory();
    int capacity = capacity(level);
    for (int slot = 0; slot < inventory.size(); slot++) {
      ItemRef stack = inventory.item(slot);
      Data data = read(stack).orElse(null);
      if (data != null && data.stored() + amount <= capacity) {
        data.add(kind, amount);
        write(stack, data);
        inventory.setItem(slot, stack);
        return true;
      }
    }
    return false;
  }

  /** Moves up to a stack of {@code kind} from the player's Quivers into the inventory when none is left loose. */
  public void restock(PlayerRef player, Kind kind) {
    ItemRef unit = kind.unit();
    InventoryRef inventory = player.inventory();
    if (unit == null || level(player) <= 0 || inventory.containsAtLeast(unit, 1)) {
      return;
    }
    for (int slot = 0; slot < inventory.size(); slot++) {
      ItemRef stack = inventory.item(slot);
      Data data = read(stack).orElse(null);
      int take = data == null ? 0 : Math.min(STACK, data.count(kind));
      if (take > 0) {
        data.add(kind, -take);
        int left = give(player, unit, take);
        data.add(kind, left);
        write(stack, data);
        inventory.setItem(slot, stack);
        return;
      }
    }
  }

  private void depositAll(PlayerRef player, Data data, int capacity) {
    InventoryRef inventory = player.inventory();
    for (int slot = 0; slot < inventory.size(); slot++) {
      ItemRef stack = inventory.item(slot);
      Kind kind = Kind.of(stack);
      int room = capacity - data.stored();
      if (kind == null || room <= 0) {
        continue;
      }
      int moved = Math.min(room, stack.amount());
      data.add(kind, moved);
      if (moved >= stack.amount()) {
        inventory.clear(slot);
      } else {
        stack.setAmount(stack.amount() - moved);
        inventory.setItem(slot, stack);
      }
    }
  }

  private void withdrawAll(PlayerRef player, Data data) {
    for (Kind kind : Kind.values()) {
      ItemRef unit = kind.unit();
      int take = unit == null ? 0 : Math.min(STACK, data.count(kind));
      if (take > 0) {
        data.add(kind, give(player, unit, take) - take);
      }
    }
  }

  /** Adds {@code amount} copies of {@code unit} to the inventory and returns how many did not fit. */
  private static int give(PlayerRef player, ItemRef unit, int amount) {
    int left = 0;
    for (ItemRef leftover : player.inventory().addItem(unit.copyWithAmount(amount))) {
      left += leftover.amount();
    }
    return left;
  }

  private static Text label(Kind kind) {
    return switch (kind) {
      case ARROW -> Text.key("adapt.tool.ranged.quiver.arrows");
      case SPECTRAL -> Text.key("adapt.tool.ranged.quiver.spectral_arrows");
      case SNARE -> Text.key("adapt.tool.ranged.snare_arrow.name");
      case RICOCHET -> Text.key("adapt.tool.ranged.ricochet_arrow.name");
    };
  }
}
