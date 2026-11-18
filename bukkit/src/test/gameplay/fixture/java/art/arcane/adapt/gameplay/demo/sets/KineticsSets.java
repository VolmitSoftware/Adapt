package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.gameplay.demo.DemoGeometry;
import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.gameplay.demo.SimpleSet;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;

public final class KineticsSets implements DemoSetProvider {
    private static final int SPIRE_HEIGHT = 50;
    private static final DemoSet.Pose ACTOR_NORTH = new DemoSet.Pose(0.5, 0, 0.5, 180f, 0f);
    private static final DemoSet.Pose CAMERA_FALL = new DemoSet.Pose(6.0, 1.5, -1.0, -99.5f, 13.9f);
    private static final List<DemoSet> SETS = List.of(
            new SimpleSet("kinetics-smash", new DemoSet.Pose(0.5, 5, 0.5, 180f, 0f), new DemoSet.Pose(10.5, 4.0, -1.5, 90f, 12f), false,
                    List.of(new DemoSet.Sparring("COW", 0.5, 0, -1.8), new DemoSet.Sparring("COW", -3.2, 0, -1.8),
                            new DemoSet.Sparring("COW", 0.5, 0, -5.5), new DemoSet.Sparring("COW", 3.1, 0, -4.4)),
                    (world, x, y, z) -> DemoGeometry.fill(world, Material.STONE_BRICKS, x - 1, y, z, x + 1, y + 4, z + 2)),
            new SimpleSet("kinetics-rebound", new DemoSet.Pose(0.5, 10, 0.5, 180f, 0f), new DemoSet.Pose(10.5, 5.0, -2.0, 90f, 14f), false,
                    List.of(new DemoSet.Sparring("COW", 2.5, 4, -1.8)), KineticsSets::buildRebound),
            new SimpleSet("kinetics-tower", new DemoSet.Pose(0.5, 20, 0.5, 180f, 0f), CAMERA_FALL, true,
                    List.of(), (world, x, y, z) -> DemoGeometry.fill(world, Material.STONE_BRICKS, x - 1, y, z, x + 1, y + 19, z + 2)),
            new Spire(),
            new SimpleSet("kinetics-spear", ACTOR_NORTH, new DemoSet.Pose(8.5, 2.8, -1.5, 90f, 8f), false,
                    List.of(new DemoSet.Sparring("COW", 0.5, 0, -3.5)), SimpleSet.OPEN_PLATE),
            new SimpleSet("kinetics-deadzone", ACTOR_NORTH, new DemoSet.Pose(9.5, 3.0, -1.0, 90f, 8f), false,
                    List.of(new DemoSet.Sparring("ZOMBIE", 0.5, 0, -1.5)), KineticsSets::buildDeadZone),
            new SimpleSet("kinetics-rails", new DemoSet.Pose(1.5, 0, 0.5, 180f, 0f), new DemoSet.Pose(10.5, 3.5, -8.0, 90f, 10f), false,
                    List.of(new DemoSet.Sparring("MINECART", 0.5, 0, 0.5), new DemoSet.Sparring("COW", -0.9, 0, -12.0)),
                    (world, x, y, z) -> DemoGeometry.fill(world, Material.RAIL, x, y, z - 40, x, y, z + 2)),
            new SimpleSet("kinetics-slime", new DemoSet.Pose(0.5, 3, 0.5, 180f, 0f), new DemoSet.Pose(9.5, 3.5, -3.5, 90f, 10f), false,
                    List.of(), KineticsSets::buildSlime),
            new SimpleSet("kinetics-sponge", new DemoSet.Pose(0.5, 7, 0.5, 180f, 0f), new DemoSet.Pose(12.5, 5.0, -2.0, 90f, 14f), false,
                    List.of(), KineticsSets::buildSponge)
    );

    @Override
    public String skill() {
        return "kinetics";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }

    private static void buildRebound(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.STONE_BRICKS, x - 1, y, z, x + 1, y + 9, z + 2);
        DemoGeometry.fill(world, Material.STONE_BRICKS, x + 2, y, z - 2, x + 2, y + 3, z - 2);
    }

    private static void buildDeadZone(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.STONE_BRICKS, x - 2, y, z - 4, x + 2, y + 2, z - 4);
        DemoGeometry.fill(world, Material.STONE_BRICKS, x - 2, y, z + 1, x + 2, y + 2, z + 1);
    }

    private static void buildSlime(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.SLIME_BLOCK, x - 2, y - 1, z - 8, x + 2, y - 1, z - 1);
        DemoGeometry.fill(world, Material.STONE_BRICKS, x - 1, y, z, x + 1, y + 2, z + 2);
    }

    private static void buildSponge(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.SPONGE, x - 2, y - 1, z - 8, x + 2, y - 1, z - 1);
        DemoGeometry.fill(world, Material.STONE_BRICKS, x - 2, y, z, x + 2, y + 6, z + 2);
    }

    private static final class Spire implements DemoSet {
        private static final DemoSet.Pose ACTOR_TOP = new DemoSet.Pose(0.5, SPIRE_HEIGHT, 0.5, 180f, 0f);

        @Override
        public String name() {
            return "kinetics-spire";
        }

        @Override
        public void build(World world, int x, int y, int z) {
            DemoGeometry.fill(world, Material.STONE_BRICKS, x - 1, y, z, x + 1, y + SPIRE_HEIGHT - 1, z + 2);
        }

        @Override
        public void clear(World world, int x, int y, int z) {
            DemoGeometry.fill(world, Material.AIR, x - 1, y, z, x + 1, y + SPIRE_HEIGHT - 1, z + 2);
        }

        @Override
        public DemoSet.Pose actorStart() {
            return ACTOR_TOP;
        }

        @Override
        public DemoSet.Pose camera() {
            return CAMERA_FALL;
        }

        @Override
        public boolean followCamera() {
            return true;
        }

        @Override
        public List<DemoSet.Sparring> sparring() {
            return List.of();
        }
    }
}
