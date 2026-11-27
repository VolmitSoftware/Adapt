package art.arcane.adapt.content.adaptation.enchanting;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A grindstone result taken by a player, carried to the next tick so the book is only handed out once the grind went
 * through. Used by Grindstone Recovery and Arcane Hand's library. Library books mark the enchantment they carry, and an
 * item that took it by hand keeps that mark, so the same enchantment cannot be pulled again.
 */
public final class EnchantingGrindstones {
  public static final Id GRINDSTONE = Id.minecraft("grindstone");
  public static final int RESULT_SLOT = 2;
  private static final DataKey<String> LIBRARY_KEY = DataKey.adapt("grindstone-library", DataCodecs.STRING);

  private final Adaptation<?> owner;
  private final Map<UUID, Pull> pending;

  public EnchantingGrindstones(Adaptation<?> owner, Map<UUID, Pull> pending) {
    this.owner = owner;
    this.pending = pending;
  }

  /** The grind a click takes, with the enchanted input it strips. */
  public record Pull(InventoryClickEvent event, InventoryRef inventory, @Nullable Loc location, ItemRef source, int level) {
  }

  /** The pull for a player's click on the grindstone result while the grindstone is off cooldown, or null. */
  public static @Nullable Pull pull(InventoryClickEvent e, int level) {
    PlayerRef p = e.player();
    if (level <= 0 || e.rawSlot() != RESULT_SLOT || e.viewKind() != InventoryKind.GRINDSTONE || isEmpty(e.currentItem())
        || e.action() == InventoryAction.NOTHING || e.action() == InventoryAction.CLONE_STACK || p.hasCooldown(GRINDSTONE)) {
      return null;
    }
    InventoryRef top = p.openMenuInventory();
    if (top == null) {
      return null;
    }
    ItemRef source = enchantedSource(top.item(0), top.item(1));
    return source == null ? null : new Pull(e, top, p.openMenuLocation(), source, level);
  }

  /** Runs {@code commit} next tick when the grind went through and the player still qualifies. */
  public void commitNextTick(PlayerRef p, Pull pull, Consumer<Pull> commit) {
    pending.put(p.uuid(), pull);
    AdaptApi.scheduler().runEntity(p, () -> {
      if (settled(p, pull)) {
        commit.accept(pull);
      }
    }, 1);
  }

  private boolean settled(PlayerRef p, Pull pull) {
    return pending.remove(p.uuid(), pull) && p.isOnline()
        && owner.getActiveLevel(p) > 0 && !p.hasCooldown(GRINDSTONE)
        && pull.inventory().equals(p.openMenuInventory())
        && (!AdaptApi.scheduler().isRegionized() || pull.location() == null || AdaptApi.scheduler().owns(pull.location()))
        && committedRemoval(pull.event(), pull.inventory());
  }

  public static boolean committedRemoval(InventoryClickEvent event, InventoryRef inventory) {
    return !event.isCancelled() && isEmpty(inventory.item(0))
        && isEmpty(inventory.item(1)) && isEmpty(inventory.item(RESULT_SLOT));
  }

  /** A book holding one enchantment at no more than its vanilla maximum. */
  public static @Nullable ItemRef book(Id enchant, int level) {
    ItemRef book = AdaptApi.platform().host().items().create(EnchantingBooks.ENCHANTED_BOOK);
    if (book.isEmpty()) {
      return null;
    }
    book.setStoredEnchantment(enchant, Math.max(1, Math.min(level,
        AdaptApi.platform().host().registries().enchantmentMaxLevel(enchant))));
    return book;
  }

  public static void deliver(PlayerRef p, ItemRef book) {
    List<ItemRef> overflow = p.inventory().addItem(book);
    overflow.forEach((ItemRef item) -> p.world().dropItemNaturally(p.location().position(), item));
  }

  /** Marks a book as pulled by the library. */
  public static void markLibraryBook(ItemRef book, Id enchant) {
    book.data().set(LIBRARY_KEY, enchant.toString());
  }

  /** The enchantments on {@code item} that came from a library book by hand. */
  public static Set<Id> libraryEnchants(@Nullable ItemRef item) {
    String raw = item == null || item.isEmpty() ? null : item.data().get(LIBRARY_KEY);
    if (raw == null || raw.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(raw.split(",")).map(Id::parse).collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /** Records on {@code item} that it took {@code enchants} from library books, alongside what it already carried. */
  public static void carryLibraryMarks(ItemRef item, Set<Id> enchants) {
    if (enchants.isEmpty()) {
      return;
    }
    Set<Id> marks = new LinkedHashSet<>(libraryEnchants(item));
    marks.addAll(enchants);
    item.data().set(LIBRARY_KEY, marks.stream().map(Id::toString).collect(Collectors.joining(",")));
  }

  /** Drops the library marks from {@code item}. Returns whether it had any. */
  public static boolean clearLibraryMarks(ItemRef item) {
    if (!item.data().has(LIBRARY_KEY)) {
      return false;
    }
    item.data().remove(LIBRARY_KEY);
    return true;
  }

  private static @Nullable ItemRef enchantedSource(ItemRef a, ItemRef b) {
    if (isEnchanted(a)) {
      return a;
    }
    return isEnchanted(b) ? b : null;
  }

  private static boolean isEnchanted(ItemRef item) {
    return !isEmpty(item) && !item.enchantments().isEmpty();
  }

  private static boolean isEmpty(@Nullable ItemRef item) {
    return item == null || item.amount() <= 0 || item.type().isAir();
  }
}
