package art.arcane.adapt.api.event;

import art.arcane.adapt.platform.event.AdaptEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * A core finished starting and every installed addon has been applied. From here until
 * {@link AdaptStoppingEvent} the catalog, players and platform are available through {@code AdaptApi}.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class AdaptStartedEvent extends AdaptEvent {
}
