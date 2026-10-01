package art.arcane.adapt.gameplay.demo;

import java.util.List;
import org.bukkit.World;

public interface DemoSet {
    double OPPONENT_SOUTH = 3.0;
    float FACING_NORTH = 180f;

    String name();

    void build(World world, int originX, int originY, int originZ);

    default void clear(World world, int originX, int originY, int originZ) {
    }

    Pose actorStart();

    default Pose opponentStart() {
        Pose actor = actorStart();
        return new Pose(actor.x(), actor.y(), actor.z() + OPPONENT_SOUTH, FACING_NORTH, 0f);
    }

    Pose camera();

    default boolean followCamera() {
        return false;
    }

    List<Sparring> sparring();

    record Pose(double x, double y, double z, float yaw, float pitch) {
    }

    record Sparring(String entityType, double x, double y, double z) {
    }
}
