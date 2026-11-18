package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Test bundles in the shape React marks as super stacks, plus a {@link ReactSuperStackPort} that reads them the way the
 * Bukkit port reads real ones: a bundle whose only lore line is the marker, while React is enabled.
 */
final class ReactBundles {
  static final String MARKER = "REACT SUPER STACK";
  private static final Id BUNDLE = Id.minecraft("bundle");
  private static final DataKey<String> BUNDLE_ID = DataKey.of(Id.of("adapt-test", "react_bundle"), DataCodecs.STRING);
  private static final Map<String, Bundle> BUNDLES = new ConcurrentHashMap<>();

  private ReactBundles() {
  }

  static ReactSuperStackPort port(BooleanSupplier reactEnabled) {
    return new ReactSuperStackPort() {
      @Override
      public boolean isSuperStack(ItemRef stack) {
        return marked(stack, reactEnabled) != null;
      }

      @Override
      public List<ItemRef> items(ItemRef stack) {
        Bundle bundle = marked(stack, reactEnabled);
        if (bundle == null) {
          return List.of();
        }
        List<ItemRef> kept = new ArrayList<>();
        for (ItemRef item : bundle.copyItems()) {
          if (!item.isEmpty() && item.amount() > 0) {
            kept.add(item);
          }
        }
        return kept;
      }

      @Override
      public Optional<ItemRef> withItems(ItemRef stack, List<ItemRef> items) {
        Bundle bundle = of(stack);
        if (bundle == null || bundle.rejecting) {
          return Optional.empty();
        }
        ItemRef rebuilt = new Bundle(copies(items), stack.lore(), false, stack.amount()).stack();
        return marked(rebuilt, reactEnabled) != null ? Optional.of(rebuilt) : Optional.empty();
      }
    };
  }

  static ItemRef superStack(ItemRef... items) {
    return bundle(List.of(MARKER), false, 1, items);
  }

  static ItemRef bundle(List<String> lore, int amount, ItemRef... items) {
    return bundle(lore, false, amount, items);
  }

  static ItemRef rejecting(ItemRef... items) {
    return bundle(List.of(MARKER), true, 1, items);
  }

  static List<ItemRef> contents(ItemRef bundle) {
    return of(bundle).copyItems();
  }

  static List<String> lore(ItemRef bundle) {
    return bundle.lore();
  }

  static List<String> summary(List<ItemRef> items) {
    List<String> lines = new ArrayList<>(items.size());
    for (ItemRef item : items) {
      lines.add(item.type().legacyName() + "x" + item.amount());
    }
    return lines;
  }

  private static ItemRef bundle(List<String> lore, boolean rejecting, int amount, ItemRef... items) {
    return new Bundle(copies(List.of(items)), lore, rejecting, amount).stack();
  }

  private static Bundle of(ItemRef stack) {
    if (stack == null || !BUNDLE.equals(stack.type())) {
      return null;
    }
    String id = stack.data().get(BUNDLE_ID);
    return id == null ? null : BUNDLES.get(id);
  }

  private static Bundle marked(ItemRef stack, BooleanSupplier reactEnabled) {
    if (!reactEnabled.getAsBoolean()) {
      return null;
    }
    Bundle bundle = of(stack);
    if (bundle == null) {
      return null;
    }
    List<String> lore = stack.lore();
    return lore.size() == 1 && MARKER.equals(lore.getFirst()) ? bundle : null;
  }

  private static List<ItemRef> copies(List<ItemRef> items) {
    List<ItemRef> copies = new ArrayList<>(items.size());
    for (ItemRef item : items) {
      copies.add(item.copy());
    }
    return copies;
  }

  private record Bundle(List<ItemRef> items, List<String> lore, boolean rejecting, int amount) {
    private List<ItemRef> copyItems() {
      return copies(items);
    }

    // The contents summary goes into the JSON lore so two bundles are similar only when they hold the same stacks.
    private ItemRef stack() {
      String id = UUID.randomUUID().toString();
      BUNDLES.put(id, this);
      FakeItem stack = new FakeItem(BUNDLE, amount);
      stack.setLore(lore);
      stack.setLoreJson(List.of(String.join(",", summary(items))));
      stack.data().set(BUNDLE_ID, id);
      return stack;
    }
  }
}
