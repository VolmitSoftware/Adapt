package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodePlacementVerdict;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.golem.node.NodeWeavingPolicy;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.iris.api.terrain.IrisTerrainService;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.logging.Logger;

public final class LumberProbeFixtures {
    private static final String DETECTOR = "art.arcane.adapt.content.adaptation.golemancy.";
    private static final int CANOPY_DEPTH = 48;
    private static final int MAX_COMPONENT = 600;
    private static final int PROBE_CAP = 256;
    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private LumberProbeFixtures() {
    }

    public static String scan(String worldName, int centerX, int centerZ, int radius, Logger logger) {
        World world = world(worldName);
        for (int cx = (centerX - radius) >> 4; cx <= (centerX + radius) >> 4; cx++) {
            for (int cz = (centerZ - radius) >> 4; cz <= (centerZ + radius) >> 4; cz++) {
                world.getChunkAt(cx, cz);
            }
        }
        Detector detector = Detector.load(world);
        IrisTerrainService terrain = Bukkit.getServicesManager().load(IrisTerrainService.class);
        Set<Long> visited = new HashSet<>(4096);
        int trees = 0;
        Map<String, Integer> verdicts = new TreeMap<>();
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                int top = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
                for (int y = top; y >= Math.max(world.getMinHeight(), top - CANOPY_DEPTH); y--) {
                    if (!Tag.LOGS.isTagged(world.getBlockAt(x, y, z).getType()) || visited.contains(key(x, y, z))) {
                        continue;
                    }
                    Anatomy anatomy = anatomy(world, x, y, z, visited);
                    String verdict = detector.judge(anatomy);
                    verdicts.merge(verdict.split("[ @(]")[0], 1, Integer::sum);
                    String biome = terrain == null ? "?" : terrain.surfaceBiomeKey(world, anatomy.lowX(), anatomy.lowZ()).orElse("?");
                    logger.info("ADAPT_QA LUMBER TREE " + anatomy.describe() + " biome=" + biome + " verdict=" + verdict);
                    trees++;
                }
            }
        }
        return "ADAPT_QA LUMBER SCAN trees=" + trees + " " + verdicts;
    }

    public static String dump(String worldName, int x, int y, int z, Logger logger) {
        World world = world(worldName);
        Anatomy anatomy = anatomy(world, x, y, z, new HashSet<>(1024));
        Map<Integer, StringBuilder> layers = new TreeMap<>();
        for (Block block : anatomy.blocks) {
            Material below = block.getRelative(0, -1, 0).getType();
            layers.computeIfAbsent(block.getY(), ignored -> new StringBuilder()).append(' ').append(block.getX() - anatomy.low.getX()).append(',')
                .append(block.getZ() - anatomy.low.getZ()).append(':').append(block.getType().name())
                .append(Tag.LOGS.isTagged(below) ? "" : "/" + below.name());
        }
        logger.info("ADAPT_QA LUMBER DUMP " + anatomy.describe());
        for (Map.Entry<Integer, StringBuilder> layer : layers.entrySet()) {
            logger.info("ADAPT_QA LUMBER LAYER y=" + layer.getKey() + layer.getValue());
        }
        return "ADAPT_QA LUMBER DUMPED " + anatomy.blocks.size();
    }

    public static String node(Player owner, int x, int y, int z, int radius) {
        NodeService service = GolemancyRuntime.nodes().orElseThrow(() -> new IllegalStateException("Golemancy nodes are not running"));
        NodeWeavingPolicy policy = GolemancyRuntime.nodeWeaving().orElseThrow(() -> new IllegalStateException("Node Weaving is not running"));
        Block anchor = owner.getWorld().getBlockAt(x, y, z);
        NodePlacementVerdict verdict = service.place(owner, anchor, BlockFace.UP, NodeType.LUMBER, policy);
        if (verdict != NodePlacementVerdict.READY) {
            return "ADAPT_QA LUMBER NODE " + verdict;
        }
        NodeKey key = NodeKey.of(anchor, BlockFace.UP);
        Optional<NodeRecord> widened = service.update(owner.getUniqueId(), key, record -> record.toBuilder().radii(radius, radius, radius).build());
        return "ADAPT_QA LUMBER NODE " + verdict + " radius=" + widened.map(NodeRecord::radiusX).orElse(-1);
    }

    private static Anatomy anatomy(World world, int x, int y, int z, Set<Long> visited) {
        Anatomy anatomy = new Anatomy();
        Deque<int[]> pending = new ArrayDeque<>();
        pending.add(new int[]{x, y, z});
        visited.add(key(x, y, z));
        while (!pending.isEmpty() && anatomy.blocks.size() < MAX_COMPONENT) {
            int[] at = pending.poll();
            Block block = world.getBlockAt(at[0], at[1], at[2]);
            anatomy.add(block, XpProvenance.wasPlaced(block));
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int nx = at[0] + dx;
                        int ny = at[1] + dy;
                        int nz = at[2] + dz;
                        if ((dx == 0 && dy == 0 && dz == 0) || !Tag.LOGS.isTagged(world.getBlockAt(nx, ny, nz).getType())) {
                            continue;
                        }
                        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 1) {
                            anatomy.diagonal = true;
                        }
                        if (visited.add(key(nx, ny, nz))) {
                            pending.add(new int[]{nx, ny, nz});
                        }
                    }
                }
            }
        }
        for (Block block : anatomy.blocks) {
            for (int[] face : FACES) {
                Block near = block.getRelative(face[0], face[1], face[2]);
                Material type = near.getType();
                if (Tag.LOGS.isTagged(type) || type.isAir()) {
                    continue;
                }
                if (Tag.LEAVES.isTagged(type)) {
                    boolean persistent = near.getBlockData() instanceof Leaves leaves && leaves.isPersistent();
                    anatomy.leaf(type, persistent);
                    if (XpProvenance.wasPlaced(near)) {
                        anatomy.contacts.merge("STAMPED_" + type.name(), 1, Integer::sum);
                    }
                    continue;
                }
                if (Detector.building(type)) {
                    anatomy.contacts.merge(type.name(), 1, Integer::sum);
                }
                anatomy.touches.merge(type.name(), 1, Integer::sum);
            }
            Block below = block.getRelative(0, -1, 0);
            Material ground = below.getType();
            if (!Tag.LOGS.isTagged(ground)) {
                anatomy.footings.merge(block.getType().name() + "/" + ground.name(), 1, Integer::sum);
            }
            if (Tag.LOGS.isTagged(ground) || ground.isAir() || Tag.LEAVES.isTagged(ground) || !ground.isSolid()) {
                continue;
            }
            int run = 1;
            while (run < CANOPY_DEPTH && Tag.LOGS.isTagged(block.getRelative(0, run, 0).getType())) {
                run++;
            }
            anatomy.runs.add(new int[]{block.getX(), block.getY(), block.getZ(), run});
            if (anatomy.main == null || run > anatomy.mainRun) {
                anatomy.main = block;
                anatomy.mainRun = run;
                anatomy.mainGround = ground;
            }
        }
        return anatomy;
    }

    private static World world(String name) {
        return Objects.requireNonNull(Bukkit.getWorld(name), "No world " + name);
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    private static final class Anatomy {
        private final List<Block> blocks = new ArrayList<>(64);
        private final Map<String, Integer> kinds = new TreeMap<>();
        private final Map<String, Integer> touches = new TreeMap<>();
        private final Map<String, Integer> footings = new TreeMap<>();
        private final Map<String, Integer> leaves = new TreeMap<>();
        private final Map<String, Integer> contacts = new TreeMap<>();
        private final List<int[]> runs = new ArrayList<>(8);
        private int stamped;
        private boolean diagonal;
        private Block low;
        private int top = Integer.MIN_VALUE;
        private Block main;
        private int mainRun;
        private Material mainGround;

        void add(Block block, boolean placed) {
            blocks.add(block);
            kinds.merge(block.getType().name(), 1, Integer::sum);
            if (placed) {
                stamped++;
            }
            if (low == null || block.getY() < low.getY()) {
                low = block;
            }
            top = Math.max(top, block.getY());
        }

        void leaf(Material type, boolean persistent) {
            leaves.merge(type.name() + (persistent ? "/p" : "/np"), 1, Integer::sum);
        }

        int lowX() {
            return low.getX();
        }

        int lowZ() {
            return low.getZ();
        }

        String describe() {
            return "at=" + low.getX() + "," + low.getY() + "," + low.getZ() + " size=" + blocks.size() + " height=" + (top - low.getY() + 1)
                + " diagonal=" + diagonal + " stamped=" + stamped + " kinds=" + kinds + " footings=" + footings + " leaves=" + leaves
                + " touches=" + touches + " building=" + contacts + " trunk=" + trunk();
        }

        private String trunk() {
            if (main == null) {
                return "none";
            }
            StringBuilder out = new StringBuilder(main.getType().name() + "/" + mainGround.name() + "@" + main.getX() + "," + main.getY() + "," + main.getZ()
                + " run=" + mainRun + " grounded=" + runs.size() + " trunks=");
            int trunks = 0;
            StringBuilder others = new StringBuilder();
            for (int[] run : runs) {
                if (run[3] >= Math.max(3, (mainRun + 1) / 2)) {
                    trunks++;
                    others.append(' ').append(run[0] - main.getX()).append(',').append(run[1] - main.getY()).append(',').append(run[2] - main.getZ())
                        .append('x').append(run[3]);
                }
            }
            return out.append(trunks).append(" [").append(others.toString().trim()).append(']').toString();
        }
    }

    private record Detector(Object view, Object reach, Method candidate, Method survey, Method verdict, Method tree, Method logs) {
        static Detector load(World world) {
            try {
                ClassLoader loader = Adapt.class.getClassLoader();
                Class<?> trees = Class.forName(DETECTOR + "LumberTrees", true, loader);
                Class<?> viewType = Class.forName(DETECTOR + "TreeView", true, loader);
                Class<?> reachType = Class.forName(DETECTOR + "LumberTrees$Reach", true, loader);
                Class<?> surveyType = Class.forName(DETECTOR + "LumberTrees$Survey", true, loader);
                Class<?> treeType = Class.forName(DETECTOR + "LumberTrees$Tree", true, loader);
                Constructor<?> viewConstructor = Class.forName(DETECTOR + "WorldTreeView", true, loader)
                    .getDeclaredConstructor(World.class, Predicate.class, Predicate.class);
                viewConstructor.setAccessible(true);
                Constructor<?> reachConstructor = reachType.getDeclaredConstructor(boolean.class, boolean.class, int.class);
                reachConstructor.setAccessible(true);
                Predicate<Block> owns = block -> true;
                Predicate<Block> placed = XpProvenance::wasPlaced;
                Object view = viewConstructor.newInstance(world, owns, placed);
                Object reach = reachConstructor.newInstance(true, true, PROBE_CAP);
                return new Detector(view, reach, accessible(trees.getDeclaredMethod("candidate", viewType, int.class, int.class, int.class, boolean.class)),
                    accessible(trees.getDeclaredMethod("survey", viewType, int.class, int.class, int.class, reachType)),
                    accessible(surveyType.getDeclaredMethod("verdict")), accessible(surveyType.getDeclaredMethod("tree")),
                    accessible(treeType.getDeclaredMethod("logs")));
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Lumber detector is unavailable", failure);
            }
        }

        String judge(Anatomy anatomy) {
            try {
                List<String> results = new ArrayList<>(2);
                for (Block block : anatomy.blocks) {
                    if (!(boolean) candidate.invoke(null, view, block.getX(), block.getY(), block.getZ(), true)) {
                        continue;
                    }
                    Object outcome = survey.invoke(null, view, block.getX(), block.getY(), block.getZ(), reach);
                    Object found = tree.invoke(outcome);
                    results.add(verdict.invoke(outcome) + (found == null ? "" : "(" + logs.invoke(found) + ")") + "@" + block.getX() + "," + block.getY() + ","
                        + block.getZ());
                }
                return results.isEmpty() ? "NO_CANDIDATE" : String.join(" ", results);
            } catch (IllegalAccessException | InvocationTargetException failure) {
                throw new IllegalStateException("Lumber detector failed", failure);
            }
        }

        static boolean building(Material type) {
            try {
                return (boolean) Building.METHOD.invoke(null, type);
            } catch (IllegalAccessException | InvocationTargetException failure) {
                throw new IllegalStateException("Lumber building check failed for " + type, failure);
            }
        }

        private static Method accessible(Method method) {
            method.setAccessible(true);
            return method;
        }
    }

    private static final class Building {
        private static final Method METHOD = resolve();

        private Building() {
        }

        private static Method resolve() {
            try {
                Method method = Class.forName(DETECTOR + "TreeBlocks", true, Adapt.class.getClassLoader()).getDeclaredMethod("building", Material.class);
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Lumber building check is unavailable", failure);
            }
        }
    }
}
