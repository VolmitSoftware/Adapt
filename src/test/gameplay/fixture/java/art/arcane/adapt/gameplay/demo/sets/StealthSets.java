package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.gameplay.demo.SimpleSet;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Tripwire;
import org.bukkit.block.data.type.TripwireHook;

public final class StealthSets implements DemoSetProvider {
    private static final DemoSet.Pose ACTOR_NORTH = new DemoSet.Pose(0.5, 0, 0.5, 180f, 0f);
    private static final List<DemoSet> SETS = List.of(
            new SimpleSet("stealth-backstab", ACTOR_NORTH, new DemoSet.Pose(7.5, 2.6, -1.5, 90f, 8f), false,
                    List.of(new DemoSet.Sparring("COW", 0.5, 0, -3.5)), SimpleSet.OPEN_PLATE),
            new SimpleSet("stealth-pickpocket", ACTOR_NORTH, new DemoSet.Pose(7.0, 2.6, -0.75, 90f, 8f), false,
                    List.of(new DemoSet.Sparring("VINDICATOR", 0.5, 0, -2.0)), SimpleSet.OPEN_PLATE),
            new SimpleSet("stealth-lure", ACTOR_NORTH, new DemoSet.Pose(11.5, 4.0, -4.0, 90f, 12f), false,
                    List.of(new DemoSet.Sparring("ZOMBIE", 0.5, 0, -10.5)), SimpleSet.OPEN_PLATE),
            new SimpleSet("stealth-veil", ACTOR_NORTH, new DemoSet.Pose(9.5, 3.0, -3.0, 90f, 6f), false,
                    List.of(new DemoSet.Sparring("ENDERMAN", 0.5, 0, -6.5)), SimpleSet.OPEN_PLATE),
            new SimpleSet("stealth-traps", ACTOR_NORTH, new DemoSet.Pose(6.5, 6.0, 3.5, 133f, 30f), false, List.of(),
                    StealthSets::buildTraps)
    );

    @Override
    public String skill() {
        return "stealth";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }

    private static void buildTraps(World world, int x, int y, int z) {
        world.getBlockAt(x + 2, y, z - 3).setType(Material.SCULK_SENSOR, false);
        world.getBlockAt(x + 2, y, z - 6).setType(Material.STONE_PRESSURE_PLATE, false);
        Chest chest = (Chest) Material.TRAPPED_CHEST.createBlockData();
        chest.setFacing(BlockFace.EAST);
        world.getBlockAt(x - 2, y, z - 2).setBlockData(chest, false);
        world.getBlockAt(x - 5, y, z - 5).setType(Material.MOSSY_COBBLESTONE, false);
        world.getBlockAt(x - 1, y, z - 5).setType(Material.MOSSY_COBBLESTONE, false);
        tripwireHook(world, x - 4, y, z - 5, BlockFace.EAST);
        tripwireHook(world, x - 2, y, z - 5, BlockFace.WEST);
        Tripwire wire = (Tripwire) Material.TRIPWIRE.createBlockData();
        wire.setAttached(true);
        wire.setFace(BlockFace.EAST, true);
        wire.setFace(BlockFace.WEST, true);
        world.getBlockAt(x - 3, y, z - 5).setBlockData(wire, false);
    }

    private static void tripwireHook(World world, int x, int y, int z, BlockFace facing) {
        TripwireHook hook = (TripwireHook) Material.TRIPWIRE_HOOK.createBlockData();
        hook.setFacing(facing);
        hook.setAttached(true);
        world.getBlockAt(x, y, z).setBlockData(hook, false);
    }
}
