package art.arcane.adapt.content.adaptation.brewing.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.ToolUse;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.brewing.BrewingDoses;
import art.arcane.adapt.content.item.ToolItems;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Master Distiller's potion belt. Right-click loads a potion from the off hand or the inventory, sneak and right-click
 * takes the active one out, and sneak and scroll with it in hand picks the active one. While a holder carries it, the
 * quarter-dose blocks draw from its active potion before the off hand, so a shield or clock can stay there.
 */
public final class AlchemistsBandolier extends MutationTool<AlchemistsBandolier.Data> {
  public static final Id ID = Id.adapt("brewing/tool/bandolier");
  private static final int INVENTORY_SLOTS = 36;
  private static final int OFF_HAND = -1;
  private static final Set<String> UNREADABLE = ConcurrentHashMap.newKeySet();
  private final MasterDistiller distiller;

  public AlchemistsBandolier(MasterDistiller owner) {
    super(ToolItems.builder(ID, Id.minecraft("leather"), Data.class, new Data()).build(), owner, 1);
    distiller = owner;
  }

  /** The loaded potions as serialized stacks, and which one doses come from. */
  public static final class Data {
    String potions = "";
    int active;
  }

  /** A potion in a carried Bandolier: the Bandolier's inventory slot and the potion's place in it. */
  public record Loaded(AlchemistsBandolier bandolier, int slot, int index, ItemRef potion) {
  }

  @Override
  public List<RecipeSpec> recipes() {
    return List.of(RecipeSpec.shapeless(ID, List.of(Ingredient.of(Id.minecraft("leather")),
        Ingredient.of(Id.minecraft("string")), Ingredient.of(Id.minecraft("glass_bottle")),
        Ingredient.of(Id.minecraft("glass_bottle")), Ingredient.of(Id.minecraft("glass_bottle"))), create(new Data())).withRequiredLevel(1));
  }

  @Override
  public void applyLore(Data data, List<String> lines) {
    List<ItemRef> potions = potions(data);
    lines.add(TextFormat.GRAY + Text.key("adapt.tool.brewing.bandolier.stored").withMarkup("count", potions == null ? "?" : Integer.toString(potions.size())).resolve());
  }

  @Override
  public void use(ToolUse use) {
    if (!use.rightClick()) {
      return;
    }
    PlayerRef player = use.player();
    int slot = player.inventory().heldSlot();
    if (use.sneaking()) {
      unload(player, slot);
    } else {
      load(player, slot, use.level());
    }
  }

  /** The active potion of the first Bandolier the player carries and can use, or null. */
  public static @Nullable Loaded find(PlayerRef player) {
    PlayerInventoryRef inventory = player.inventory();
    for (int slot = 0; slot < INVENTORY_SLOTS; slot++) {
      ItemRef item = inventory.item(slot);
      if (!(Mutations.tool(item).orElse(null) instanceof AlchemistsBandolier bandolier) || bandolier.level(player) <= 0) {
        continue;
      }
      Data data = bandolier.read(item).orElse(null);
      List<ItemRef> potions = data == null ? null : potions(data);
      if (potions != null && !potions.isEmpty()) {
        int index = Math.floorMod(data.active, potions.size());
        return new Loaded(bandolier, slot, index, potions.get(index));
      }
    }
    return null;
  }

  /**
   * Puts {@code after} back where a loaded potion was. A bottle that is no longer a potion leaves the Bandolier and,
   * unless nothing is left of it, goes to the inventory.
   */
  public void replace(PlayerRef player, Loaded loaded, ItemRef after) {
    ItemRef stack = player.inventory().item(loaded.slot());
    Data data = read(stack).orElse(null);
    if (data == null) {
      return;
    }
    List<ItemRef> potions = potions(data);
    if (potions == null || loaded.index() >= potions.size()) {
      return;
    }
    if (BrewingDoses.dosable(after)) {
      potions.set(loaded.index(), after);
    } else {
      potions.remove(loaded.index());
      if (!after.isEmpty()) {
        giveBack(player, after);
      }
    }
    save(player, loaded.slot(), stack, data, encode(potions));
  }

  /** Whether {@code item} is a Bandolier with potions loaded, or with stored potions it cannot read. */
  public static boolean loaded(@Nullable ItemRef item) {
    if (!(Mutations.tool(item).orElse(null) instanceof AlchemistsBandolier bandolier)) {
      return false;
    }
    Data data = bandolier.read(item).orElse(null);
    if (data == null) {
      return false;
    }
    List<ItemRef> potions = potions(data);
    return potions == null || !potions.isEmpty();
  }

  /** Moves the active potion of the Bandolier held in {@code slot} by {@code step}. */
  public void cycle(PlayerRef player, int slot, int step) {
    ItemRef stack = player.inventory().item(slot);
    Data data = read(stack).orElse(null);
    if (data == null) {
      return;
    }
    List<ItemRef> potions = potions(data);
    if (potions == null) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.tool.brewing.bandolier.unreadable").resolve());
      return;
    }
    if (!potions.isEmpty()) {
      data.active = Math.floorMod(data.active + step, potions.size());
    }
    save(player, slot, stack, data, encode(potions));
    show(player, data, potions);
  }

  private void load(PlayerRef player, int slot, int level) {
    ItemRef stack = player.inventory().item(slot);
    Data data = read(stack).orElse(null);
    if (data == null) {
      return;
    }
    List<ItemRef> potions = potions(data);
    if (potions == null) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.tool.brewing.bandolier.unreadable").resolve());
      return;
    }
    if (potions.size() >= distiller.getBandolierSlots(level)) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.tool.brewing.bandolier.full").resolve());
      return;
    }
    int source = source(player, slot);
    if (source == Integer.MIN_VALUE) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.tool.brewing.bandolier.nothing").resolve());
      return;
    }
    ItemRef potion = source == OFF_HAND ? player.inventory().offHand() : player.inventory().item(source);
    ItemRef loading = potion.copyWithAmount(1);
    potions.add(loading);
    String encoded = encode(potions);
    if (!storesFaithfully(encoded, loading)) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.tool.brewing.bandolier.unstorable").resolve());
      return;
    }
    ItemRef rest = potion.amount() > 1 ? potion.copyWithAmount(potion.amount() - 1)
        : AdaptApi.platform().host().items().empty();
    if (source == OFF_HAND) {
      player.inventory().setOffHand(rest);
    } else {
      player.inventory().setItem(source, rest);
    }
    data.active = potions.size() - 1;
    save(player, slot, stack, data, encoded);
    show(player, data, potions);
  }

  private void unload(PlayerRef player, int slot) {
    ItemRef stack = player.inventory().item(slot);
    Data data = read(stack).orElse(null);
    if (data == null) {
      return;
    }
    List<ItemRef> potions = potions(data);
    if (potions == null) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.tool.brewing.bandolier.unreadable").resolve());
      return;
    }
    if (potions.isEmpty()) {
      show(player, data, potions);
      return;
    }
    int index = Math.floorMod(data.active, potions.size());
    ItemRef taken = potions.get(index);
    if (!BrewingDoses.dosable(taken)) {
      warnOnce(data.potions, "An Alchemist's Bandolier holds a potion with no contents, keeping it stored: ");
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.tool.brewing.bandolier.unreadable").resolve());
      return;
    }
    potions.remove(index);
    data.active = potions.isEmpty() ? 0 : Math.floorMod(data.active, potions.size());
    save(player, slot, stack, data, encode(potions));
    giveBack(player, taken);
    show(player, data, potions);
  }

  private static int source(PlayerRef player, int held) {
    PlayerInventoryRef inventory = player.inventory();
    if (BrewingDoses.dosable(inventory.offHand())) {
      return OFF_HAND;
    }
    for (int slot = 0; slot < INVENTORY_SLOTS; slot++) {
      if (slot != held && BrewingDoses.dosable(inventory.item(slot))) {
        return slot;
      }
    }
    return Integer.MIN_VALUE;
  }

  private void save(PlayerRef player, int slot, ItemRef stack, Data data, String potions) {
    data.potions = potions;
    write(stack, data);
    player.inventory().setItem(slot, stack);
  }

  private static String encode(List<ItemRef> potions) {
    return potions.isEmpty() ? ""
        : Base64.getEncoder().encodeToString(AdaptApi.platform().host().items().serializeItems(potions));
  }

  private static boolean storesFaithfully(String encoded, ItemRef potion) {
    Data stored = new Data();
    stored.potions = encoded;
    List<ItemRef> read = potions(stored);
    return read != null && !read.isEmpty() && read.getLast().isSimilar(potion);
  }

  /** The stored potions, or null when they cannot be read; the stored form is then kept untouched. */
  private static @Nullable List<ItemRef> potions(Data data) {
    List<ItemRef> potions = new ArrayList<>();
    if (data.potions == null || data.potions.isEmpty()) {
      return potions;
    }
    try {
      for (ItemRef potion : AdaptApi.platform().host().items().deserializeItems(Base64.getDecoder().decode(data.potions))) {
        if (!potion.isEmpty()) {
          potions.add(potion);
        }
      }
    } catch (IllegalArgumentException | IllegalStateException unreadable) {
      warnOnce(data.potions, "An Alchemist's Bandolier's potions could not be read, keeping them stored: ");
      return null;
    }
    return potions;
  }

  private static void warnOnce(String stored, String message) {
    if (UNREADABLE.add(stored)) {
      AdaptApi.log().warn(message + stored);
    }
  }

  private static void show(PlayerRef player, Data data, List<ItemRef> potions) {
    if (potions.isEmpty()) {
      HudStatus.show(player, TextFormat.GRAY + Text.key("adapt.tool.brewing.bandolier.empty").resolve());
      return;
    }
    int index = Math.floorMod(data.active, potions.size());
    HudStatus.show(player, TextFormat.AQUA + Text.key("adapt.tool.brewing.bandolier.active").withMarkup("potion", name(potions.get(index))).withMarkup("slot", Integer.toString(index + 1)).withMarkup("count", Integer.toString(potions.size())).resolve());
  }

  private static String name(ItemRef potion) {
    String name = BrewingDoses.name(potion);
    return name == null ? Format.capitalizeWords(potion.type().path().replace('_', ' ')) : name;
  }

  private static void giveBack(PlayerRef player, ItemRef item) {
    List<ItemRef> overflow = player.inventory().addItem(item);
    if (overflow == null) {
      return;
    }
    for (ItemRef rest : overflow) {
      player.world().dropItem(player.location().position(), rest);
    }
  }
}
