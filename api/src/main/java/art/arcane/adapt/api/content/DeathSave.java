package art.arcane.adapt.api.content;

import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * An adaptation that keeps a player alive through a lethal hit. Implemented by the adaptation class itself.
 *
 * <p>Adapt runs the {@code EntityDamageEvent} handlers its content registers at {@code HIGHEST} priority in one fixed
 * order: {@link #protectsFromDamage} of every death save, plain handlers, {@link DamageDeferral} handlers,
 * {@link #protectsFromDamage} again, and then, only while the hit would still kill a player, the handlers of the
 * death saves. Death saves run by the player's {@linkplain #deathSavePriority priority}, {@code FIRST} to
 * {@code LAST}, and within a priority by {@link #defaultOrder()}; the first one that leaves the hit no longer lethal
 * ends the round. A save's handler decides for itself whether it saves, normally by
 * {@link DeathSaveSlot#activate activating} its slot.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface DeathSave {
  /**
   * Called before and after the other damage handlers for each hit on a player; true cancels the hit. Use it for an
   * invulnerability window a save opened earlier. False by default.
   */
  default boolean protectsFromDamage(EntityDamageEvent event) {
    return false;
  }

  /**
   * When this save runs relative to the others for {@code player}. By default the player's choice of
   * {@link CommonPreferences#DEATH_SAVE_PRIORITY} on this adaptation, which it then lists in its player preferences;
   * {@code NORMAL} for an implementation that is not an {@link Adaptation}.
   */
  default CommonPreferences.DeathSavePriority deathSavePriority(PlayerRef player) {
    return this instanceof Adaptation<?> adaptation
        ? adaptation.preference(player, CommonPreferences.DEATH_SAVE_PRIORITY)
        : CommonPreferences.DeathSavePriority.NORMAL;
  }

  /** Order among saves of the same priority, lowest first; ties run in class name order. */
  int defaultOrder();
}
