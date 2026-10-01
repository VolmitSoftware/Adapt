package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.gameplay.demo.DemoGeometry;
import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.gameplay.demo.SimpleSet;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.EntityType;

public final class PickaxeSets implements DemoSetProvider {
    private static final DemoSet.Pose ACTOR_NORTH = new DemoSet.Pose(0.5, 0, 0.5, 180f, 0f);
    private static final int SPAWNER_DELAY = 12000;
    private static final List<Cell> QUARRY_ORES = List.of(
            new Cell(-2, 1, -4, Material.IRON_ORE),
            new Cell(0, 2, -4, Material.DIAMOND_ORE),
            new Cell(2, 1, -3, Material.GOLD_ORE),
            new Cell(1, 1, -5, Material.EMERALD_ORE),
            new Cell(-1, 2, -5, Material.REDSTONE_ORE),
            new Cell(-1, 1, -3, Material.LAPIS_ORE),
            new Cell(2, 2, -5, Material.COPPER_ORE),
            new Cell(0, 1, -5, Material.COAL_ORE)
    );
    private static final List<Cell> DIAMOND_VEIN = List.of(
            new Cell(-2, 0, -3, Material.DIAMOND_ORE),
            new Cell(-2, 1, -3, Material.DIAMOND_ORE),
            new Cell(-1, 1, -3, Material.DIAMOND_ORE),
            new Cell(0, 1, -3, Material.DIAMOND_ORE),
            new Cell(0, 2, -3, Material.DIAMOND_ORE),
            new Cell(1, 2, -3, Material.DIAMOND_ORE),
            new Cell(1, 3, -3, Material.DIAMOND_ORE),
            new Cell(2, 3, -3, Material.DIAMOND_ORE),
            new Cell(2, 4, -3, Material.DIAMOND_ORE),
            new Cell(0, 2, -4, Material.DIAMOND_ORE),
            new Cell(1, 3, -4, Material.DIAMOND_ORE)
    );
    private static final List<DemoSet> SETS = List.of(
            new SimpleSet("pickaxe-orewall", ACTOR_NORTH, new DemoSet.Pose(5.5, 2.8, 2.5, 126.5f, 15.3f), false, List.of(),
                    PickaxeSets::buildOreWall),
            new SimpleSet("pickaxe-deepslate", ACTOR_NORTH, new DemoSet.Pose(5.5, 3.2, 2.5, 126.5f, 12.7f), false, List.of(),
                    (world, x, y, z) -> DemoGeometry.fill(world, Material.DEEPSLATE, x - 2, y, z - 3, x + 2, y + 4, z - 2)),
            new SimpleSet("pickaxe-obsidian", ACTOR_NORTH, new DemoSet.Pose(4.5, 2.6, 2.0, 128.7f, 17.3f), false, List.of(),
                    (world, x, y, z) -> DemoGeometry.fill(world, Material.OBSIDIAN, x - 1, y, z - 3, x + 1, y + 1, z - 2)),
            new SimpleSet("pickaxe-trophy", ACTOR_NORTH, new DemoSet.Pose(4.5, 2.4, 1.5, 124.0f, 16.2f), false, List.of(),
                    PickaxeSets::buildTrophy),
            new SimpleSet("pickaxe-spawner", ACTOR_NORTH, new DemoSet.Pose(4.5, 2.4, 1.5, 122.0f, 20.9f), false, List.of(),
                    PickaxeSets::buildSpawner),
            new SimpleSet("pickaxe-quarry", ACTOR_NORTH, new DemoSet.Pose(7.5, 4.5, 4.0, 135.0f, 16.9f), false, List.of(),
                    PickaxeSets::buildQuarry),
            new SimpleSet("pickaxe-floor", ACTOR_NORTH, new DemoSet.Pose(5.0, 3.4, 2.0, 125.4f, 31.6f), false, List.of(),
                    (world, x, y, z) -> DemoGeometry.fill(world, Material.STONE, x - 1, y - 1, z - 3, x + 1, y - 1, z - 1)),
            new SimpleSet("pickaxe-stonewall", ACTOR_NORTH, new DemoSet.Pose(6.5, 3.2, 3.0, 126.9f, 10.6f), false, List.of(),
                    (world, x, y, z) -> DemoGeometry.fill(world, Material.STONE, x - 3, y, z - 4, x + 3, y + 4, z - 3)),
            new SimpleSet("pickaxe-vein", ACTOR_NORTH, new DemoSet.Pose(6.5, 3.2, 3.0, 129.8f, 10.2f), false, List.of(),
                    PickaxeSets::buildVein),
            new SimpleSet("pickaxe-tunnel", ACTOR_NORTH, new DemoSet.Pose(2.5, 3.5, 5.5, 166.8f, 14.8f), false, List.of(),
                    (world, x, y, z) -> DemoGeometry.fill(world, Material.STONE, x - 2, y, z - 9, x + 2, y + 3, z - 2))
    );

    @Override
    public String skill() {
        return "pickaxe";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }

    private static void buildOreWall(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.STONE, x - 2, y, z - 3, x + 2, y + 2, z - 2);
        world.getBlockAt(x - 1, y + 1, z - 2).setType(Material.IRON_ORE, false);
        world.getBlockAt(x, y + 1, z - 2).setType(Material.GOLD_ORE, false);
        world.getBlockAt(x + 1, y + 1, z - 2).setType(Material.COPPER_ORE, false);
    }

    private static void buildTrophy(World world, int x, int y, int z) {
        world.getBlockAt(x, y, z - 2).setType(Material.CHISELED_STONE_BRICKS, false);
        Block head = world.getBlockAt(x, y + 1, z - 2);
        Rotatable data = (Rotatable) Material.DRAGON_HEAD.createBlockData();
        data.setRotation(BlockFace.SOUTH);
        head.setBlockData(data, false);
        XpProvenance.clearPlacementRecord(head);
    }

    private static void buildSpawner(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z - 2);
        block.setType(Material.SPAWNER, false);
        CreatureSpawner spawner = (CreatureSpawner) block.getState();
        spawner.setSpawnedType(EntityType.ZOMBIE);
        spawner.setDelay(SPAWNER_DELAY);
        spawner.update(true, false);
    }

    private static void buildQuarry(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.STONE, x - 3, y, z - 6, x + 3, y + 3, z - 2);
        place(world, x, y, z, QUARRY_ORES);
    }

    private static void buildVein(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.STONE, x - 3, y, z - 4, x + 3, y + 4, z - 3);
        place(world, x, y, z, DIAMOND_VEIN);
    }

    private static void place(World world, int x, int y, int z, List<Cell> cells) {
        for (Cell cell : cells) {
            world.getBlockAt(x + cell.dx(), y + cell.dy(), z + cell.dz()).setType(cell.material(), false);
        }
    }

    private record Cell(int dx, int dy, int dz, Material material) {
    }
}
