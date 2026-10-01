package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.StructureType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.StructureSearchResult;

public final class StructureDiscoverySets implements DemoSetProvider {
    private final List<DemoSet> sets = List.of(new StructureApproach());

    @Override
    public String skill() {
        return "discovery";
    }

    @Override
    public List<DemoSet> sets() {
        return sets;
    }

    public static String preference(Player player, String adaptationId, String controlId, String value) {
        AdaptPlayer learner = Adapt.instance.getAdaptServer().getPlayer(player);
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (!adaptation.getName().equals(adaptationId)) {
                    continue;
                }
                for (PlayerPreference<?> control : adaptation.getPlayerPreferences()) {
                    if (control.id().equals(controlId)) {
                        if (!setPreference(adaptation, learner, control, value)) {
                            throw new IllegalArgumentException("Preference value is unavailable: " + controlId + "=" + value);
                        }
                        return "ADAPT_QA DEMO PREFERENCE " + adaptationId + " " + controlId + " " + value;
                    }
                }
                throw new IllegalArgumentException("Unknown preference " + controlId + " for " + adaptationId);
            }
        }
        throw new IllegalArgumentException("Unknown adaptation " + adaptationId);
    }

    private static <E extends Enum<E>> boolean setPreference(Adaptation<?> adaptation, AdaptPlayer learner, PlayerPreference<E> control, String value) {
        E selected = control.parse(value);
        return selected != null && PlayerPreferences.set(adaptation, learner, control, selected);
    }

    private static final class StructureApproach implements DemoSet {
        private static final int SEARCH_CHUNKS = 128;
        private static final int STANDOFF = 128;
        private static final int SURFACE_SEARCH = 24;
        private Pose actor;
        private Pose camera;

        @Override
        public String name() {
            return "discovery-structure-approach";
        }

        @Override
        public void build(World world, int originX, int originY, int originZ) {
            Location origin = new Location(world, originX, originY, originZ);
            StructureSearchResult target = world.locateNearestStructure(origin, StructureType.JIGSAW, SEARCH_CHUNKS, false);
            if (target == null || target.getLocation() == null || target.getLocation().getWorld() != world) {
                throw new IllegalStateException("No native JIGSAW structure can be located in the approved Iris world");
            }
            GeneratedStructure structure = generatedStructure(world, target);
            if (structure == null) {
                throw new IllegalStateException("Located JIGSAW structure has no generated Bukkit structure metadata");
            }
            BoundingBox bounds = structure.getBoundingBox();
            int[][] approaches = {
                    {(int) Math.floor(bounds.getCenterX()), (int) Math.ceil(bounds.getMaxZ()) + STANDOFF},
                    {(int) Math.ceil(bounds.getMaxX()) + STANDOFF, (int) Math.floor(bounds.getCenterZ())},
                    {(int) Math.floor(bounds.getCenterX()), (int) Math.floor(bounds.getMinZ()) - STANDOFF},
                    {(int) Math.floor(bounds.getMinX()) - STANDOFF, (int) Math.floor(bounds.getCenterZ())}
            };
            for (int[] approach : approaches) {
                for (int distance = 0; distance <= SURFACE_SEARCH; distance += 4) {
                    for (int dx = -distance; dx <= distance; dx += 4) {
                        if (select(world, target.getLocation(), bounds, originX, originY, originZ, approach[0] + dx, approach[1] - distance)
                                || select(world, target.getLocation(), bounds, originX, originY, originZ, approach[0] + dx, approach[1] + distance)) {
                            Bukkit.getLogger().info("Demo structure approach: " + target.getStructure() + " at "
                                    + target.getLocation().getBlockX() + " " + target.getLocation().getBlockZ()
                                    + " with generated bounds " + bounds);
                            return;
                        }
                    }
                }
            }
            throw new IllegalStateException("No safe surface approach outside generated structure bounds within Sixth Sense range");
        }

        private static GeneratedStructure generatedStructure(World world, StructureSearchResult target) {
            int chunkX = target.getLocation().getBlockX() >> 4;
            int chunkZ = target.getLocation().getBlockZ() >> 4;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (GeneratedStructure structure : world.getChunkAt(chunkX + dx, chunkZ + dz).getStructures()) {
                        if (structure.getStructure().equals(target.getStructure())) {
                            return structure;
                        }
                    }
                }
            }
            return null;
        }

        private boolean select(World world, Location target, BoundingBox bounds, int originX, int originY, int originZ, int x, int z) {
            if (Math.abs(x - originX) <= 24 && z - originZ >= -72 && z - originZ <= 24) {
                return false;
            }
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
            Block ground = world.getBlockAt(x, y - 1, z);
            if (!ground.getType().isSolid() || Tag.LOGS.isTagged(ground.getType()) || Tag.LEAVES.isTagged(ground.getType())
                    || !world.getBlockAt(x, y, z).isPassable() || world.getBlockAt(x, y, z).isLiquid()
                    || !world.getBlockAt(x, y + 1, z).isPassable() || bounds.contains(x + 0.5D, y, z + 0.5D)) {
                return false;
            }
            double dx = target.getX() - x - 0.5D;
            double dz = target.getZ() - z - 0.5D;
            double horizontal = Math.hypot(dx, dz);
            if (horizontal < 80D || horizontal > 450D) {
                return false;
            }
            for (int step = 1; step <= 12; step++) {
                int aheadX = (int) Math.floor(x + 0.5D + dx / horizontal * step);
                int aheadZ = (int) Math.floor(z + 0.5D + dz / horizontal * step);
                int aheadY = world.getHighestBlockYAt(aheadX, aheadZ, HeightMap.MOTION_BLOCKING);
                if (aheadY > y || world.getBlockAt(aheadX, aheadY, aheadZ).isLiquid()) {
                    return false;
                }
            }
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            clearPlants(world, x, y, z, dx / horizontal, dz / horizontal);
            actor = new Pose(x - originX + 0.5D, y - originY, z - originZ + 0.5D, yaw, 2F);
            double cameraX = x + 0.5D + dz / horizontal * 7D - dx / horizontal * 5D;
            double cameraZ = z + 0.5D - dx / horizontal * 7D - dz / horizontal * 5D;
            float cameraYaw = (float) Math.toDegrees(Math.atan2(cameraX - x - 0.5D, z + 0.5D - cameraZ));
            camera = new Pose(cameraX - originX, y - originY + 4D, cameraZ - originZ, cameraYaw, 18F);
            return true;
        }

        private static void clearPlants(World world, int x, int y, int z, double directionX, double directionZ) {
            for (int step = 0; step <= 12; step++) {
                for (int side = -1; side <= 1; side++) {
                    int plantX = (int) Math.floor(x + 0.5D + directionX * step + directionZ * side);
                    int plantZ = (int) Math.floor(z + 0.5D + directionZ * step - directionX * side);
                    for (int height = 0; height < 2; height++) {
                        Block plant = world.getBlockAt(plantX, y + height, plantZ);
                        Material material = plant.getType();
                        if (!material.isAir() && !material.isSolid() && !plant.isLiquid() && Tag.REPLACEABLE.isTagged(material)) {
                            plant.setType(Material.AIR, false);
                        }
                    }
                }
            }
        }

        @Override
        public Pose actorStart() {
            if (actor == null) {
                throw new IllegalStateException("Select a generated structure approach before positioning actors");
            }
            return actor;
        }

        @Override
        public Pose camera() {
            if (camera == null) {
                throw new IllegalStateException("Select a generated structure approach before positioning the camera");
            }
            return camera;
        }

        @Override
        public List<Sparring> sparring() {
            return List.of();
        }
    }
}
