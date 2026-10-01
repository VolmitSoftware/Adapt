package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.iris.api.tree.IrisTreeFellerService;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class IrisFellerSets implements DemoSetProvider {
    private final List<DemoSet> sets = List.of(new NaturalTree());

    @Override
    public String skill() {
        return "axes";
    }

    @Override
    public List<DemoSet> sets() {
        return sets;
    }

    private static final class NaturalTree implements DemoSet {
        private static final int SEARCH_START = 32;
        private static final int SEARCH_LIMIT = 256;
        private static final int SEARCH_STEP = 1;
        private static final int CANOPY_DEPTH = 32;
        private Pose actor;
        private Pose camera;
        private TreeCandidate selected;

        @Override
        public String name() {
            return "axes-iris-tree";
        }

        @Override
        public void build(World world, int originX, int originY, int originZ) {
            IrisTreeFellerService service = Bukkit.getServicesManager().load(IrisTreeFellerService.class);
            if (service == null) {
                throw new IllegalStateException("Iris tree-feller service is unavailable");
            }
            selected = null;
            for (int radius = SEARCH_START; radius <= SEARCH_LIMIT; radius += SEARCH_STEP) {
                for (int offset = -radius; offset <= radius; offset++) {
                    if (select(world, service, originX, originY, originZ, originX + offset, originZ - radius)
                            || select(world, service, originX, originY, originZ, originX + offset, originZ + radius)
                            || select(world, service, originX, originY, originZ, originX - radius, originZ + offset)
                            || select(world, service, originX, originY, originZ, originX + radius, originZ + offset)) {
                        return;
                    }
                }
            }
            if (selected != null) {
                position(originX, originY, originZ);
                return;
            }
            throw new IllegalStateException("No accessible Iris-provenance tree in generated chunks within " + SEARCH_LIMIT + " blocks of the demo plate");
        }

        private boolean select(World world, IrisTreeFellerService service, int originX, int originY, int originZ, int x, int z) {
            if ((Math.abs(x - originX) <= 24 && z - originZ >= -72 && z - originZ <= 24)
                    || !world.isChunkGenerated(x >> 4, z >> 4)
                    || !world.isChunkGenerated(x >> 4, (z + 3) >> 4)) {
                return false;
            }
            int top = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
            int floor = Math.max(world.getMinHeight() + 1, top - CANOPY_DEPTH);
            for (int y = floor; y <= top - 3; y++) {
                Block base = world.getBlockAt(x, y, z);
                if (!Tag.LOGS.isTagged(base.getType())
                        || Tag.LOGS.isTagged(world.getBlockAt(x, y - 1, z).getType())
                        || !standingRoom(world, x, y, z + 3)
                        || !service.isTreeBlock(base)) {
                    continue;
                }
                int logs = 1;
                while (logs < CANOPY_DEPTH && y + logs < world.getMaxHeight()
                        && Tag.LOGS.isTagged(world.getBlockAt(x, y + logs, z).getType())) {
                    logs++;
                }
                if (logs < 3) {
                    continue;
                }
                int basalLogs = countBasalLogs(world, x, y, z);
                int score = basalLogs * CANOPY_DEPTH + logs;
                TreeCandidate candidate = new TreeCandidate(x, y, z, logs, score);
                if (selected == null || score < selected.score()) {
                    selected = candidate;
                }
                if (basalLogs <= 10 && logs <= 12) {
                    selected = candidate;
                    position(originX, originY, originZ);
                    return true;
                }
            }
            return false;
        }

        private void position(int originX, int originY, int originZ) {
            actor = new Pose(selected.x() - originX + 0.5D, selected.y() - originY, selected.z() - originZ + 3.5D, 180F, 20.5F);
            camera = new Pose(selected.x() - originX - 3.5D, selected.y() - originY + 3.2D, selected.z() - originZ + 11.5D, 200F, 9F);
            Bukkit.getLogger().info("Demo Iris Feller: provenance-valid natural trunk at " + selected.x() + " " + selected.y() + " " + selected.z() + " with " + selected.logs() + " vertical logs");
        }

        private static boolean standingRoom(World world, int x, int y, int z) {
            Block ground = world.getBlockAt(x, y - 1, z);
            return ground.getType().isSolid() && !Tag.LOGS.isTagged(ground.getType()) && !Tag.LEAVES.isTagged(ground.getType())
                    && world.getBlockAt(x, y, z).isPassable() && !world.getBlockAt(x, y, z).isLiquid()
                    && world.getBlockAt(x, y + 1, z).isPassable();
        }

        private static int countBasalLogs(World world, int x, int y, int z) {
            int logs = 0;
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    if (!world.isChunkGenerated((x + offsetX) >> 4, (z + offsetZ) >> 4)) {
                        return 36;
                    }
                    for (int offsetY = 0; offsetY < 4; offsetY++) {
                        if (Tag.LOGS.isTagged(world.getBlockAt(x + offsetX, y + offsetY, z + offsetZ).getType())) {
                            logs++;
                        }
                    }
                }
            }
            return logs;
        }

        @Override
        public Pose actorStart() {
            if (actor == null) {
                throw new IllegalStateException("Select the natural Iris tree before positioning actors");
            }
            return actor;
        }

        @Override
        public Pose camera() {
            if (camera == null) {
                throw new IllegalStateException("Select the natural Iris tree before positioning the camera");
            }
            return camera;
        }

        @Override
        public List<Sparring> sparring() {
            return List.of();
        }

        private record TreeCandidate(int x, int y, int z, int logs, int score) {
        }
    }
}
