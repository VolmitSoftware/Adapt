package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.integration.Integrations;
import art.arcane.adapt.content.golem.GolemContext;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

final class ReactSuperStacks {
  private ReactSuperStacks() {
  }

  static boolean isSuperStack(ItemRef stack) {
    return stack != null && !stack.isEmpty() && Integrations.isSuperStack(stack);
  }

  static boolean hollow(ItemRef stack) {
    return isSuperStack(stack) && (stack.amount() != 1 || Integrations.superStackItems(stack).isEmpty());
  }

  static List<ItemRef> contents(ItemRef stack) {
    return !isSuperStack(stack) || stack.amount() != 1 ? List.of() : Integrations.superStackItems(stack);
  }

  static boolean accepts(ItemRef stack, Predicate<Id> types) {
    if (!isSuperStack(stack)) {
      return types.test(stack.type());
    }
    if (stack.amount() != 1) {
      return false;
    }
    for (ItemRef content : Integrations.superStackItems(stack)) {
      if (types.test(content.type())) {
        return true;
      }
    }
    return false;
  }

  static Unpacked unpack(GolemContext golem, ItemRef bundle, Predicate<Id> accepts) {
    List<ItemRef> contents = contents(bundle);
    List<ItemRef> leftovers = new ArrayList<>(contents.size());
    int carried = 0;
    for (ItemRef content : contents) {
      if (golem.room() <= 0 || !accepts.test(content.type())) {
        leftovers.add(content);
        continue;
      }
      int amount = content.amount();
      Optional<ItemRef> refused = golem.carry(content);
      carried += amount - refused.map(ItemRef::amount).orElse(0);
      refused.ifPresent(leftovers::add);
    }
    return new Unpacked(carried, leftovers);
  }

  static Optional<ItemRef> rebuild(ItemRef bundle, List<ItemRef> leftovers) {
    if (leftovers.isEmpty()) {
      return Optional.empty();
    }
    return Integrations.withSuperStackItems(bundle, leftovers);
  }

  record Unpacked(int carried, List<ItemRef> leftovers) {
  }
}
