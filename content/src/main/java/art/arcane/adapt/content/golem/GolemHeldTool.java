package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;

import java.util.Objects;
import java.util.Optional;
import java.util.function.DoubleSupplier;

final class GolemHeldTool {
  private final GolemHandle handle;
  private String encoded;
  private ItemRef decoded;
  private boolean shown;

  GolemHeldTool(GolemHandle handle) {
    this.handle = Objects.requireNonNull(handle);
  }

  static void hide(MobRef golem) {
    golem.setEquipment(EquipmentSlot.MAIN_HAND, null);
  }

  Optional<ItemRef> item() {
    if (handle.body() == GolemBody.BONE) {
      return handle.equipped(GolemEquipmentSlot.MAIN_HAND);
    }
    String stored = handle.state().held();
    if (stored == null) {
      return Optional.empty();
    }
    if (!stored.equals(encoded)) {
      try {
        decoded = handle.platform().decodeItem(stored);
      } catch (RuntimeException corrupt) {
        AdaptApi.log().error("Skipping the unreadable held tool of golem " + handle.id() + "; the stored entry is kept.", corrupt);
        decoded = null;
      }
      encoded = stored;
    }
    return decoded == null ? Optional.empty() : Optional.of(decoded.copy());
  }

  boolean hold(ItemRef next) {
    if (handle.body() == GolemBody.BONE) {
      return false;
    }
    ItemRef before = item().orElse(null);
    String stored = next == null ? null : handle.platform().encodeItem(next);
    if (!handle.setHeldEncoded(stored)) {
      return false;
    }
    encoded = stored;
    decoded = next == null ? null : next.copy();
    show(before, next);
    return true;
  }

  void sync() {
    if (shown || handle.body() == GolemBody.BONE) {
      return;
    }
    shown = true;
    item().ifPresent(tool -> show(tool, tool));
  }

  boolean wear(DoubleSupplier random) {
    if (handle.body() == GolemBody.BONE) {
      handle.damageEquipment(GolemEquipmentSlot.MAIN_HAND, 1);
      return item().isEmpty();
    }
    ItemRef tool = item().orElse(null);
    if (tool == null || tool.isEmpty() || tool.isUnbreakable()) {
      return false;
    }
    int maxDamage = tool.maxDamage();
    if (maxDamage <= 0) {
      return false;
    }
    GolemToolRules.Wear wear = GolemToolRules.wear(tool.damage(), maxDamage, GolemToolEnchants.unbreaking(tool), random.getAsDouble());
    if (!wear.worn()) {
      return false;
    }
    if (wear.broken()) {
      handle.entity().playEquipmentBreak(EquipmentSlot.MAIN_HAND);
      hold(null);
      return true;
    }
    tool.setDamage(wear.damage());
    hold(tool);
    return false;
  }

  private void show(ItemRef before, ItemRef next) {
    MobRef golem = handle.entity();
    ItemRef current = golem.equipment(EquipmentSlot.MAIN_HAND);
    if (before == null && current != null && !current.isEmpty() && !current.type().isAir()) {
      Loc at = golem.location();
      golem.world().dropItemNaturally(at.position(), current);
    }
    golem.setEquipment(EquipmentSlot.MAIN_HAND, next == null ? null : next.copy());
    golem.setEquipmentDropChance(EquipmentSlot.MAIN_HAND, 0F);
  }
}
