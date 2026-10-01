package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.gameplay.demo.DemoGeometry;
import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.gameplay.demo.SimpleSet;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.List;

public final class KineticsComparisonSets implements DemoSetProvider {
    private static final List<DemoSet> SETS = List.of(
            new SimpleSet("kinetics-inspection-smash", new DemoSet.Pose(0.5, 5, 0.5, 180f, 50f),
                    new DemoSet.Pose(6.5, 3.2, -2.0, 90f, 8f), false,
                    List.of(new DemoSet.Sparring("COW", 0.5, 0, -1.8)), KineticsComparisonSets::buildSmash),
            new SimpleSet("kinetics-charge-comparison", new DemoSet.Pose(0.5, 0, 0.5, 180f, 5f),
                    new DemoSet.Pose(8.5, 2.6, -3.0, 90f, 8f), false,
                    List.of(new DemoSet.Sparring("COW", 0.5, 0, -5.7)), KineticsComparisonSets::buildMeleeLane),
            new SimpleSet("kinetics-reach-comparison", new DemoSet.Pose(0.5, 0, 0.5, 180f, 10f),
                    new DemoSet.Pose(7.5, 2.8, -1.0, 90f, 10f), false,
                    List.of(new DemoSet.Sparring("VILLAGER", -1.3, 0, -3.8),
                            new DemoSet.Sparring("VILLAGER", 2.3, 0, -3.8)), KineticsComparisonSets::buildMeleeLane),
            new BraceSet(),
            new SimpleSet("kinetics-skate-comparison", new DemoSet.Pose(0.5, 0, 0.5, 180f, 20f),
                    new DemoSet.Pose(12.5, 9, -13.0, 90f, 30f), false, List.of(), KineticsComparisonSets::buildRunway)
    );

    @Override
    public String skill() {
        return "kinetics";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }

    private static void buildSmash(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.STONE_BRICKS, x - 1, y, z, x + 1, y + 4, z + 2);
        DemoGeometry.fill(world, Material.POLISHED_ANDESITE, x - 2, y - 1, z - 4, x + 2, y - 1, z - 1);
    }

    private static void buildMeleeLane(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.STONE_BRICKS, x - 2, y - 1, z - 8, x + 2, y - 1, z + 2);
        for (int offset = -8; offset <= 2; offset += 2) {
            DemoGeometry.fill(world, Material.POLISHED_ANDESITE, x - 2, y - 1, z + offset, x + 2, y - 1, z + offset);
        }
    }

    private static void buildRunway(World world, int x, int y, int z) {
        DemoGeometry.fill(world, Material.STONE_BRICKS, x - 2, y - 1, z - 42, x + 2, y - 1, z + 3);
        for (int offset = -42; offset <= 2; offset += 2) {
            DemoGeometry.fill(world, Material.POLISHED_ANDESITE, x - 2, y - 1, z + offset, x + 2, y - 1, z + offset);
        }
    }

    private static final class BraceSet implements DemoSet {
        @Override
        public String name() {
            return "kinetics-brace-inspection";
        }

        @Override
        public void build(World world, int x, int y, int z) {
            buildSmash(world, x, y, z);
        }

        @Override
        public Pose actorStart() {
            return new Pose(-4.5, 0, -1.8, -65f, -45f);
        }

        @Override
        public Pose opponentStart() {
            return new Pose(0.5, 5, 0.5, 180f, 50f);
        }

        @Override
        public Pose camera() {
            return new Pose(-8.5, 3.0, -5.5, -65f, 6f);
        }

        @Override
        public List<Sparring> sparring() {
            return List.of(new Sparring("COW", 0.5, 0, -1.8));
        }
    }
}
