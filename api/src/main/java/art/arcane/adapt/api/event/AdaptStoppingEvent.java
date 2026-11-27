package art.arcane.adapt.api.event;

import art.arcane.adapt.platform.event.AdaptEvent;
import org.jetbrains.annotations.ApiStatus;

/** The running core is about to stop. Player data is still loaded while this is handled. */
@ApiStatus.AvailableSince("1.0.0")
public final class AdaptStoppingEvent extends AdaptEvent {
}
