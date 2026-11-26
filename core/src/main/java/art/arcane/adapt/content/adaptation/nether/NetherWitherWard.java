package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

/** Wither Resistance's armor count and the soul dome it shows when a wither tick is shrugged off. */
public final class NetherWitherWard {
  private static final long FEEDBACK_INTERVAL_MILLIS = 1000L;
  private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
      EquipmentSlot.FEET);
  private static final Set<Id> NETHERITE_ARMOR = Set.of(Id.minecraft("netherite_helmet"), Id.minecraft("netherite_chestplate"),
      Id.minecraft("netherite_leggings"), Id.minecraft("netherite_boots"));
  private final BiFunction<Loc, FxPriority, FxEmitter> fx;
  private final Cooldowns cooldowns;

  /** {@code fx} is the owning adaptation's own effect factory, so its effects stay routed through it. */
  public NetherWitherWard(BiFunction<Loc, FxPriority, FxEmitter> fx, Cooldowns cooldowns) {
    this.fx = fx;
    this.cooldowns = cooldowns;
  }

  public static int netheritePieces(PlayerRef p) {
    int pieces = 0;
    for (EquipmentSlot slot : ARMOR_SLOTS) {
      ItemRef item = p.equipment(slot);
      if (item != null && NETHERITE_ARMOR.contains(item.type())) {
        pieces++;
      }
    }
    return pieces;
  }

  /** Shows the shrug at most once a second; returns whether it played. */
  public boolean feedback(PlayerRef p, boolean mastery) {
    if (!cooldowns.isReady(p.uuid(), FEEDBACK_INTERVAL_MILLIS)) {
      return false;
    }
    cooldowns.mark(p.uuid());
    Loc center = p.location().add(0D, 1.0D, 0D);
    NetherWitherResist.wardFx(fx.apply(center, FxPriority.COMBAT), mastery);
    return true;
  }
}
