package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.gameplay.demo.SimpleSet;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Grindstone;
import org.bukkit.block.data.type.Leaves;

public final class SwordsSets implements DemoSetProvider {
    private static final DemoSet.Pose ACTOR_NORTH = new DemoSet.Pose(0.5, 0, 0.5, 180f, 0f);
    private static final List<Material> MEADOW = List.of(Material.SHORT_GRASS, Material.FERN, Material.DANDELION, Material.SHORT_GRASS,
            Material.CORNFLOWER, Material.SHORT_GRASS, Material.ALLIUM, Material.FERN, Material.AZURE_BLUET, Material.SHORT_GRASS,
            Material.ORANGE_TULIP);
    private static final int MEADOW_HALF_WIDTH = 3;
    private static final int MEADOW_SOUTH_Z = -1;
    private static final int MEADOW_NORTH_Z = -4;
    private static final int HEDGE_Z = -5;
    private static final int HEDGE_HEIGHT = 2;
    private static final int ANVIL_Z = 2;
    private static final int GRINDSTONE_Z = -1;
    private static final List<DemoSet> SETS = List.of(
            new SimpleSet("swords-cluster", ACTOR_NORTH, new DemoSet.Pose(6.5, 4.5, 3.5, 135f, 25f), false,
                    List.of(new DemoSet.Sparring("ZOMBIE", 0.5, 0, -2.0), new DemoSet.Sparring("ZOMBIE", -2.0, 0, -3.5),
                            new DemoSet.Sparring("ZOMBIE", 3.0, 0, -3.5), new DemoSet.Sparring("ZOMBIE", 0.5, 0, -5.0)),
                    SimpleSet.OPEN_PLATE),
            new SimpleSet("swords-lunge", ACTOR_NORTH, new DemoSet.Pose(7.5, 2.6, -3.0, 90f, 8f), false,
                    List.of(new DemoSet.Sparring("ZOMBIE", 0.5, 0, -4.5)), SimpleSet.OPEN_PLATE),
            new SimpleSet("swords-cow", ACTOR_NORTH, new DemoSet.Pose(5.0, 2.4, 1.0, 111f, 17f), false,
                    List.of(new DemoSet.Sparring("COW", 0.5, 0, -2.0)), SimpleSet.OPEN_PLATE),
            new SimpleSet("swords-coop", new DemoSet.Pose(0.5, 0, 0.5, -45f, 0f), new DemoSet.Pose(5.5, 2.6, -3.0, 45f, 17f), false,
                    List.of(new DemoSet.Sparring("CHICKEN", 2.0, 0, 2.0)), SimpleSet.OPEN_PLATE),
            new SimpleSet("swords-heirloom", ACTOR_NORTH, new DemoSet.Pose(6.5, 3.0, 0.0, 90f, 14f), false,
                    List.of(new DemoSet.Sparring("CHICKEN", -1.96, 0, 0.07), new DemoSet.Sparring("CHICKEN", -1.11, 0, -1.42),
                            new DemoSet.Sparring("CHICKEN", 0.5, 0, -2.0), new DemoSet.Sparring("CHICKEN", 2.11, 0, -1.42),
                            new DemoSet.Sparring("CHICKEN", 2.96, 0, 0.07)),
                    SwordsSets::buildAnvil),
            new SimpleSet("swords-meadow", ACTOR_NORTH, new DemoSet.Pose(5.5, 3.5, 3.5, 138f, 24f), false, List.of(),
                    SwordsSets::buildMeadow),
            new SimpleSet("swords-grindstone", ACTOR_NORTH, new DemoSet.Pose(4.5, 2.6, 2.5, 122f, 21f), false, List.of(),
                    SwordsSets::buildGrindstone),
            new Pursuit()
    );

    @Override
    public String skill() {
        return "swords";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }

    private static void buildAnvil(World world, int x, int y, int z) {
        Directional anvil = (Directional) Material.ANVIL.createBlockData();
        anvil.setFacing(BlockFace.EAST);
        world.getBlockAt(x, y, z + ANVIL_Z).setBlockData(anvil, false);
    }

    private static void buildMeadow(World world, int x, int y, int z) {
        for (int dx = -MEADOW_HALF_WIDTH; dx <= MEADOW_HALF_WIDTH; dx++) {
            for (int dz = MEADOW_NORTH_Z; dz <= MEADOW_SOUTH_Z; dz++) {
                Material plant = MEADOW.get(Math.floorMod(dx * 3 + dz * 5, MEADOW.size()));
                world.getBlockAt(x + dx, y, z + dz).setType(plant, false);
            }
        }
        Leaves leaves = (Leaves) Material.OAK_LEAVES.createBlockData();
        leaves.setPersistent(true);
        for (int dx = -MEADOW_HALF_WIDTH; dx <= MEADOW_HALF_WIDTH; dx++) {
            for (int dy = 0; dy < HEDGE_HEIGHT; dy++) {
                world.getBlockAt(x + dx, y + dy, z + HEDGE_Z).setBlockData(leaves, false);
            }
        }
    }

    private static void buildGrindstone(World world, int x, int y, int z) {
        Grindstone grindstone = (Grindstone) Material.GRINDSTONE.createBlockData();
        grindstone.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
        grindstone.setFacing(BlockFace.NORTH);
        world.getBlockAt(x, y, z + GRINDSTONE_Z).setBlockData(grindstone, false);
    }

    private static final class Pursuit implements DemoSet {
        private static final DemoSet.Pose RUNNER = new DemoSet.Pose(0.5, 0, -1.0, 180f, 0f);
        private static final DemoSet.Pose CAMERA_SIDE = new DemoSet.Pose(7.5, 2.8, -4.0, 90f, 9f);

        @Override
        public String name() {
            return "swords-pursuit";
        }

        @Override
        public void build(World world, int originX, int originY, int originZ) {
        }

        @Override
        public DemoSet.Pose actorStart() {
            return ACTOR_NORTH;
        }

        @Override
        public DemoSet.Pose opponentStart() {
            return RUNNER;
        }

        @Override
        public DemoSet.Pose camera() {
            return CAMERA_SIDE;
        }

        @Override
        public List<DemoSet.Sparring> sparring() {
            return List.of();
        }
    }
}
