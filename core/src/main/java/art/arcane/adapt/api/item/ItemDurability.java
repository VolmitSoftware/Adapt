package art.arcane.adapt.api.item;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerItemDamageEvent;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Per-stack max durability. Where the server keeps it on the stack ({@link EngineFeature#ITEM_STACK_LIMITS}) it is set
 * there. Older versions keep the wanted maximum in the item's Adapt data, and this listener scales every durability
 * loss by item type maximum / wanted maximum, so on average the item lasts as many uses as the wanted maximum gives.
 * The client still draws the durability bar against the type's maximum.
 */
public final class ItemDurability {
  static final DataKey<Integer> MAX_DURABILITY = DataKey.adapt("max_durability", DataCodecs.INT);

  private final DoubleSupplier random;

  public ItemDurability() {
    this(() -> ThreadLocalRandom.current().nextDouble());
  }

  ItemDurability(DoubleSupplier random) {
    this.random = random;
  }

  /** True when the running server keeps max durability per stack, so no listener is needed. */
  public static boolean perStack() {
    return AdaptCore.platform().host().supports(EngineFeature.ITEM_STACK_LIMITS);
  }

  /** Sets the stack's max durability, natively or as the stored stand-in. */
  public static void setMaxDamage(ItemRef item, int maxDamage) {
    if (perStack()) {
      item.setMaxDamage(maxDamage);
    } else if (maxDamage == item.maxDamage()) {
      item.data().remove(MAX_DURABILITY);
    } else {
      item.data().set(MAX_DURABILITY, maxDamage);
    }
  }

  /** The stack's max durability as Adapt sees it: the native value, or the stored stand-in on older versions. */
  public static int maxDamage(ItemRef item) {
    if (perStack()) {
      return item.maxDamage();
    }
    Integer wanted = item.data().get(MAX_DURABILITY);
    return wanted == null || wanted <= 0 ? item.maxDamage() : wanted;
  }

  /** Durability loss after scaling, rounded up with probability equal to the fractional part. */
  static int scaledDamage(int damage, int typeMax, int wantedMax, double roll) {
    double exact = (double) damage * typeMax / wantedMax;
    int whole = (int) Math.floor(exact);
    return roll < exact - whole ? whole + 1 : whole;
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerItemDamageEvent event) {
    if (event.damage() <= 0) {
      return;
    }
    Integer wanted = event.item().data().get(MAX_DURABILITY);
    int typeMax = event.item().maxDamage();
    if (wanted == null || wanted <= 0 || typeMax <= 0 || wanted == typeMax) {
      return;
    }
    event.setDamage(scaledDamage(event.damage(), typeMax, wanted, random.getAsDouble()));
  }
}
