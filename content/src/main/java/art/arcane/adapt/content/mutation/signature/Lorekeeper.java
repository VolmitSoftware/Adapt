package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerLootGenerateEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discovery and Enchanting, either leading, replacing Convergence: the first time you open a structure chest and its
 * loot rolls, one enchanted book the roll put there rises, one level per enchantment, never past the vanilla maximum.
 */
public final class Lorekeeper extends Signature<Lorekeeper.Config> {
  private static final Id ENCHANTED_BOOK = Id.minecraft("enchanted_book");
  private static final Map<Id, Integer> VANILLA_MAX = vanillaMaxima();

  private final Set<String> opened = ConcurrentHashMap.newKeySet();
  private final Map<UUID, Roll> rolling = new ConcurrentHashMap<>();

  public Lorekeeper(MutationSlot slot) {
    super(slot);
    setLocalizationKey(PairMessages.signatureKey(slot));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("lectern"));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, raises(level), 1);
  }

  @Override
  public long cooldownMillis(int level) {
    return Math.max(0L, getConfig().cooldownMillis);
  }

  @Override
  public boolean paysOut() {
    return true;
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerLootGenerateEvent e) {
    PlayerRef player = e.player();
    Loc at = e.location();
    if (getActiveLevel(player) <= 0 || at.world() == null) {
      return;
    }
    BlockRef chest = at.world().blockAt(at);
    InventoryRef loot = chest.container();
    if (loot == null || !opened.add(player.uuid() + ":" + at.world().uid() + ":" + chest.x() + ":" + chest.y() + ":" + chest.z())) {
      return;
    }
    int[] half = half(loot.size(), chest.containerHalves().indexOf(chest), chest.containerHalves().size());
    Map<Integer, ItemRef> before = books(loot, half);
    AdaptApi.scheduler().runAt(at, () -> {
      BlockRef rolled = at.world().blockAt(at);
      if (!player.isOnline() || rolled.container() == null) {
        return;
      }
      rolling.put(player.uuid(), new Roll(before, half));
      try {
        fire(player, null, rolled.center(), Mutations.farmed(rolled));
      } finally {
        rolling.remove(player.uuid());
      }
    }, 1);
  }

  @Override
  public Outcome run(BlockUse use) {
    Loc at = use.at();
    Roll roll = rolling.get(use.player().uuid());
    InventoryRef loot = at == null || roll == null ? null : at.world().blockAt(at).container();
    if (loot == null) {
      return Outcome.MISSED;
    }
    Map<Integer, ItemRef> after = books(loot, roll.half());
    for (Map.Entry<Integer, ItemRef> slot : after.entrySet()) {
      ItemRef old = roll.before().get(slot.getKey());
      if (old != null && old.storedEnchantments().equals(slot.getValue().storedEnchantments())) {
        continue;
      }
      ItemRef book = slot.getValue();
      if (raise(book, raises(use.level())) > 0) {
        loot.setItem(slot.getKey(), book);
        fx(at, FxPriority.TRANSITION)
            .burst(Particles.ENCHANT, 16, 0.5D)
            .burst(Particles.CRIT, 6, 0.3D);
        return Outcome.INSTANT;
      }
    }
    return Outcome.MISSED;
  }

  /** Raises up to {@code left} of the book's enchantments one level each; how many rose. */
  private static int raise(ItemRef book, int left) {
    int raised = 0;
    for (Map.Entry<Id, Integer> enchantment : new HashMap<>(book.storedEnchantments()).entrySet()) {
      if (raised >= left) {
        break;
      }
      if (enchantment.getValue() < maximum(enchantment.getKey())) {
        book.setStoredEnchantment(enchantment.getKey(), enchantment.getValue() + 1);
        raised++;
      }
    }
    return raised;
  }

  /** The vanilla maximum for a vanilla enchantment, which modpacks may raise in the registry; the registry's otherwise. */
  static int maximum(Id enchantment) {
    int registry = AdaptApi.platform().host().registries().enchantmentMaxLevel(enchantment);
    Integer vanilla = VANILLA_MAX.get(enchantment);
    return vanilla == null ? registry : Math.min(registry, vanilla);
  }

  /** The slots {@code [from, to)} of the half that rolled: the whole container unless it is one of two halves. */
  static int[] half(int size, int index, int halves) {
    if (halves != 2 || index < 0) {
      return new int[]{0, size};
    }
    int split = size / 2;
    return index == 0 ? new int[]{0, split} : new int[]{split, size};
  }

  private static Map<Integer, ItemRef> books(InventoryRef loot, int[] half) {
    Map<Integer, ItemRef> books = new HashMap<>();
    for (int slot = half[0]; slot < Math.min(half[1], loot.size()); slot++) {
      ItemRef item = loot.item(slot);
      if (item != null && ENCHANTED_BOOK.equals(item.type())) {
        books.put(slot, item.copy());
      }
    }
    return books;
  }

  int raises(int level) {
    return Math.max(1, getConfig().raisesBase << Math.max(0, level - 1));
  }

  private static Map<Id, Integer> vanillaMaxima() {
    Map<Id, Integer> max = new HashMap<>();
    for (String one : List.of("aqua_affinity", "binding_curse", "channeling", "flame", "infinity", "mending",
        "multishot", "silk_touch", "vanishing_curse")) {
      max.put(Id.minecraft(one), 1);
    }
    for (String two : List.of("fire_aspect", "frost_walker", "knockback", "punch")) {
      max.put(Id.minecraft(two), 2);
    }
    for (String three : List.of("depth_strider", "fortune", "looting", "loyalty", "luck_of_the_sea", "lure",
        "quick_charge", "respiration", "riptide", "soul_speed", "sweeping", "sweeping_edge", "swift_sneak", "thorns",
        "unbreaking", "wind_burst", "lunge")) {
      max.put(Id.minecraft(three), 3);
    }
    for (String four : List.of("blast_protection", "breach", "feather_falling", "fire_protection", "piercing",
        "projectile_protection", "protection")) {
      max.put(Id.minecraft(four), 4);
    }
    for (String five : List.of("bane_of_arthropods", "density", "efficiency", "impaling", "power", "sharpness",
        "smite")) {
      max.put(Id.minecraft(five), 5);
    }
    return Map.copyOf(max);
  }

  private record Roll(Map<Integer, ItemRef> before, int[] half) {
  }

  @ConfigDescription("One enchanted book in a structure chest you open first rolls one level higher.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Enchantments on the book raised at level 1; each level doubles it.", impact = "Higher values raise more of the book's enchantments.")
    int raisesBase = 1;
    @ConfigDoc(value = "Cooldown after a book rises, in milliseconds.", impact = "Higher values skip chests opened soon after.")
    long cooldownMillis = 0L;
  }
}
