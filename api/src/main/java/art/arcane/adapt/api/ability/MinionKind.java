package art.arcane.adapt.api.ability;

import art.arcane.adapt.platform.entity.EntityRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Recognises the entities one kind of minion is made of, such as the skeletons a summoning adaptation raises, and
 * names the player each belongs to. Called on the entity's thread, often; keep it a cheap data lookup.
 */
@ApiStatus.Experimental
public interface MinionKind {
  /** Whether {@code entity} is one of this kind's minions. */
  boolean matches(EntityRef entity);

  /** The owning player, or null when the minion has lost its owner. Only asked for entities {@link #matches}. */
  @Nullable UUID ownerOf(EntityRef entity);
}
