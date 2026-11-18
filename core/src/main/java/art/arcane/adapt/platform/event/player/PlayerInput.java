package art.arcane.adapt.platform.event.player;

/** Movement keys a player holds, as sent by the client. */
public record PlayerInput(boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean sneak,
                          boolean sprint) {
}
