package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

/** Cutpurse's theft: rolls a mob's loot and hands it to the thief, once per mob; {@code onStolen} sees each theft. */
public final class StealthPockets {
  private static final DataKey<Byte> PICKED = DataKey.adapt("cutpurse_picked", DataCodecs.BYTE);

  private final BiConsumer<PlayerRef, Loc> onStolen;

  public StealthPockets(BiConsumer<PlayerRef, Loc> onStolen) {
    this.onStolen = onStolen;
  }

  public static boolean picked(MobRef mob) {
    return mob.data().has(PICKED);
  }

  /** Steals up to {@code maxStacks} rolled stacks; false when the mob was already picked or rolled nothing. */
  public boolean steal(PlayerRef thief, MobRef mob, float lootQuality, int maxStacks) {
    DataHolder pdc = mob.data();
    if (pdc.has(PICKED)) {
      return false;
    }

    Loc targetLocation = mob.location();
    List<ItemRef> rolled = AdaptApi.platform().host().animals()
        .rollLoot(mob, thief, lootQuality, ThreadLocalRandom.current());
    List<ItemRef> stolen = new ArrayList<>();
    for (ItemRef stack : rolled) {
      if (stack == null || stack.isEmpty() || stack.type().isAir() || stack.amount() <= 0) {
        continue;
      }
      stolen.add(stack.copy());
      if (stolen.size() >= maxStacks) {
        break;
      }
    }

    if (stolen.isEmpty()) {
      return false;
    }

    pdc.set(PICKED, (byte) 1);
    AdaptApi.scheduler().runEntity(thief, () -> deliver(thief, targetLocation, stolen));
    return true;
  }

  private void deliver(PlayerRef p, Loc targetLocation, List<ItemRef> stolen) {
    if (!p.isOnline()) {
      dropAll(targetLocation, stolen);
      return;
    }

    List<ItemRef> leftovers = new ArrayList<>();
    for (ItemRef stack : stolen) {
      List<ItemRef> remaining = p.inventory().addItem(stack);
      if (remaining != null) {
        leftovers.addAll(remaining);
      }
    }
    if (!leftovers.isEmpty()) {
      dropAll(targetLocation, leftovers);
    }

    onStolen.accept(p, targetLocation);
  }

  private void dropAll(Loc location, List<ItemRef> stacks) {
    if (stacks.isEmpty()) {
      return;
    }
    AdaptApi.scheduler().runAt(location, () -> {
      for (ItemRef stack : stacks) {
        if (stack != null && !stack.isEmpty() && !stack.type().isAir() && stack.amount() > 0) {
          location.world().dropItem(location.position(), stack);
        }
      }
    });
  }
}
