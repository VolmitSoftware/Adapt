package art.arcane.adapt.platform.advancement;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;
import java.util.List;

/**
 * Advancement tabs shown to players. Bukkit renders through UltimateAdvancementAPI; modded sends
 * vanilla advancement update packets. Adapt's player data stays the source of truth for grants:
 * on join Adapt calls {@link #restore} with the stored keys, and the host only renders them.
 * Methods may be called from any thread; adapters hop to the viewer's thread for packets. Only the engine holds it:
 * addons declare advancements as {@code AdaptAdvancement} values on their content.
 */
@ApiStatus.Internal
public interface AdvancementHost {
  /** Replaces the whole catalogue with these root trees and resends it to online players. */
  void setCatalog(List<AdvancementNode> roots);

  /** True when the current catalogue has an advancement with this key. */
  boolean isRegistered(Id key);

  /** Marks the advancement complete for the player, optionally with a toast. Returns false when unknown. */
  boolean grant(PlayerRef player, Id key, boolean toast);

  /** Clears the grant and progress for the player. */
  void revoke(PlayerRef player, Id key);

  /** Sets counter progress, clamped to {@code 0..maxProgress}; reaching {@code maxProgress} grants. Unknown keys are ignored. */
  void setProgress(PlayerRef player, Id key, int progress);

  /** The player's counter progress, from 0 up to the node's {@code maxProgress}; 0 when nothing is recorded. */
  int progress(PlayerRef player, Id key);

  /** Sends every stored grant to a joining player without toasts. */
  void restore(PlayerRef player, Collection<Id> granted);

  /** One-off toast that is not tied to a registered advancement. */
  void toast(PlayerRef player, ItemRef icon, String titleMarkup, AdvancementFrame frame);

  /** Forgets per-player render state on quit. */
  void forget(PlayerRef player);
}
