package art.arcane.adapt.mod.event;

/** Per-connection position of the last posted move event, as Paper keeps it on the connection. */
public final class MoveState {
    double previousX;
    double previousY;
    double previousZ;
    float previousYaw;
    float previousPitch;
    double x;
    double y;
    double z;
    float yaw;
    float pitch;
    boolean known;
    boolean jumped;
    double jumpX;
    double jumpY;
    double jumpZ;
    float jumpYaw;
    float jumpPitch;

    /** Implemented by the packet listener mixin. */
    public interface Holder {
        MoveState adapt$moveState();
    }
}
