package art.arcane.adapt.api.internal;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.item.MaterialValues;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** What the engine does for custom items: creating, recognising, reading and writing stacks, and parsing recipes. */
@ApiStatus.Internal
public interface ItemRuntime {
  <D> ItemRef create(CustomItem.Definition<D> definition, D data, int amount);

  boolean is(CustomItem.Definition<?> definition, ItemRef item);

  /** The data of a stack of the definition, or null for any other stack. */
  <D> @Nullable D read(CustomItem.Definition<D> definition, ItemRef item);

  <D> boolean write(CustomItem.Definition<D> definition, ItemRef item, D data);

  <D> void replace(CustomItem.Definition<D> definition, ItemRef item, D data);

  /** A per-player cooldown gate; marking it shows {@code owner}'s cooldown glyph on the HUD when owner is set. */
  Cooldowns cooldowns(@Nullable Adaptation<?> owner);

  /** Stamps the use-cooldown group onto the stack unless it carries it already; true when the stack changed. */
  boolean stampCooldownGroup(ItemRef stack, Id group, float markerSeconds);

  void recordAppliedEffect(UUID player, Id effect);

  boolean maxDurabilityPerStack();

  int maxDurability(ItemRef item);

  void setMaxDurability(ItemRef item, int maxDamage);

  List<MaterialValues.Recipe> materialRecipes(Id material);

  ItemRef modelled(Id fallback, List<String> path);

  long modelsRevision();

  void applyLook(ItemRef item, String key);
}
