package art.arcane.adapt.gameplay.demo;

import java.util.List;
import org.bukkit.World;

public record SimpleSet(String name, DemoSet.Pose actorStart, DemoSet.Pose camera, boolean followCamera, List<DemoSet.Sparring> sparring,
        Builder builder) implements DemoSet {
    public static final Builder OPEN_PLATE = (world, x, y, z) -> { };

    @Override
    public void build(World world, int originX, int originY, int originZ) {
        builder.build(world, originX, originY, originZ);
    }

    @FunctionalInterface
    public interface Builder {
        void build(World world, int x, int y, int z);
    }
}
