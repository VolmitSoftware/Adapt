package art.arcane.adapt.platform.event.player;

import org.jetbrains.annotations.ApiStatus;

/**
 * Movement keys a player holds, as sent by the client.
 *
 * @param forward forward key held
 * @param backward backward key held
 * @param left strafe-left key held
 * @param right strafe-right key held
 * @param jump jump key held
 * @param sneak sneak key held
 * @param sprint sprint key held
 */
@ApiStatus.AvailableSince("1.0.0")
public record PlayerInput(boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean sneak,
                          boolean sprint) {
}
