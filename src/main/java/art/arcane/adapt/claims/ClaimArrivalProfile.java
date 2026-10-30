package art.arcane.adapt.claims;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public enum ClaimArrivalProfile {
    NONE(Landing.STANDING, List.of()),
    BRACKEN_STANDING(Landing.STANDING, List.of()),
    BRACKEN_FALLING(Landing.FALLING, List.of()),
    BRACKEN_SWIMMING(Landing.SWIMMING, List.of()),
    ASTRAL_RETURN(Landing.SWIMMING, List.of()),
    BODY_AIR(Landing.FALLING, List.of(new Volume(0, 0, 0, 1, 2, 1))),
    UNDERDARK_TO_OVERWORLD(Landing.STANDING, List.of(new Volume(0, -1, 0, 1, 2, 1))),
    NETHERRACK_COLUMN(Landing.STANDING, List.of(new Volume(0, -1, 0, 1, 2, 1))),
    FAEWILD_PLATFORM(Landing.STANDING, List.of(new Volume(-3, -1, -3, 4, 0, 4), new Volume(0, 0, 0, 1, 2, 1))),
    PAX_ARRIVAL(Landing.FALLING, List.of(new Volume(0, -11, 0, 1, -10, 1), new Volume(0, 0, 0, 1, 2, 1))),
    FAEWILD_LEAF(Landing.FALLING, List.of(new Volume(0, -3, 0, 1, -2, 1))),
    VOID_CUBE(Landing.STANDING, List.of(new Volume(-2, -1, -2, 3, 4, 3))),
    BRINE_PORTAL(Landing.SWIMMING, List.of(new Volume(-2, -4, -2, 3, 1, 3))),
    DORMIS_CAGE(Landing.STANDING, List.of(new Volume(-10, -9, -7, 10, 39, 13))),
    OMNI_PLATFORM(Landing.STANDING, List.of(new Volume(-5, -2, -1, 6, 5, 10)));

    private static final Set<Material> HAZARDS = Set.of(Material.LAVA, Material.FIRE, Material.SOUL_FIRE,
            Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE, Material.CACTUS,
            Material.MAGMA_BLOCK, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.POINTED_DRIPSTONE,
            Material.END_PORTAL, Material.NETHER_PORTAL);

    private final Landing landing;
    private final List<Volume> volumes;

    ClaimArrivalProfile(Landing landing, List<Volume> volumes) {
        this.landing = landing;
        this.volumes = volumes;
    }

    boolean horizontal() {
        return this != NONE;
    }

    CompletableFuture<Void> load(Player player, Location destination, ClaimChunkRetention retention) {
        double radius = player.getWidth() / 2.0 + 1;
        int minX = Location.locToBlock(destination.getX() - radius);
        int maxX = Location.locToBlock(destination.getX() + radius);
        int minZ = Location.locToBlock(destination.getZ() - radius);
        int maxZ = Location.locToBlock(destination.getZ() + radius);
        if (!spectator(player)) {
            for (Volume volume : volumes) {
                minX = Math.min(minX, destination.getBlockX() + volume.minX());
                maxX = Math.max(maxX, destination.getBlockX() + volume.maxX());
                minZ = Math.min(minZ, destination.getBlockZ() + volume.minZ());
                maxZ = Math.max(maxZ, destination.getBlockZ() + volume.maxZ());
            }
        }
        List<CompletableFuture<Void>> loading = new ArrayList<>(9);
        for (int x = minX >> 4; x <= maxX >> 4; x++) {
            for (int z = minZ >> 4; z <= maxZ >> 4; z++) {
                loading.add(retention.load(destination.getWorld(), x, z));
            }
        }
        return CompletableFuture.allOf(loading.toArray(new CompletableFuture<?>[0]));
    }

    boolean permitted(ClaimsService claims, ClaimSafeLocation locations, Player player, Location destination) {
        if (!allowed(claims, player, destination)) {
            return false;
        }
        if (spectator(player)) {
            return true;
        }
        if (volumes.isEmpty()) {
            return geometry(locations, player, destination);
        }
        if (!unmodifiedBodyClear(player, destination)) {
            return false;
        }
        if (this == UNDERDARK_TO_OVERWORLD || this == NETHERRACK_COLUMN || this == FAEWILD_PLATFORM) {
            return keptFloor(player, destination);
        }
        return true;
    }

    boolean ready(ClaimsService claims, ClaimSafeLocation locations, Player player, Location destination) {
        return allowed(claims, player, destination)
                && (spectator(player) || geometry(locations, player, destination));
    }

    private boolean allowed(ClaimsService claims, Player player, Location destination) {
        World world = destination.getWorld();
        BoundingBox body = ClaimSafeLocation.standingBody(player, destination);
        if (world == null || !claims.canTeleport(player, destination)
                || landing == Landing.STANDING && (destination.getY() < world.getMinHeight()
                || body.getMaxY() > world.getMaxHeight())) {
            return false;
        }
        if (!insideBorder(world, body)) {
            return false;
        }
        for (int x = Location.locToBlock(body.getMinX()); x <= Location.locToBlock(body.getMaxX()); x++) {
            for (int z = Location.locToBlock(body.getMinZ()); z <= Location.locToBlock(body.getMaxZ()); z++) {
                if (!claims.canTeleport(player, new Location(world, x + 0.5, destination.getY(), z + 0.5))) {
                    return false;
                }
            }
        }
        if (spectator(player)) {
            return true;
        }
        for (int x = Location.locToBlock(body.getMinX()) >> 4; x <= Location.locToBlock(Math.nextDown(body.getMaxX())) >> 4; x++) {
            for (int z = Location.locToBlock(body.getMinZ()) >> 4; z <= Location.locToBlock(Math.nextDown(body.getMaxZ())) >> 4; z++) {
                if (!world.isChunkLoaded(x, z)) {
                    return false;
                }
            }
        }
        for (Volume volume : volumes) {
            BoundingBox edits = volume.at(destination);
            if (edits.getMinY() < world.getMinHeight() || edits.getMaxY() > world.getMaxHeight()
                    || !insideBorder(world, edits) || !claims.canPrepareArrival(player, world, edits)) {
                return false;
            }
        }
        return true;
    }

    private boolean geometry(ClaimSafeLocation locations, Player player, Location destination) {
        if (this == NONE) {
            return locations.safeLandingOwned(player, destination);
        }
        World world = destination.getWorld();
        BoundingBox body = ClaimSafeLocation.standingBody(player, destination);
        BoundingBox support = new BoundingBox(body.getMinX(), destination.getY() - 0.05, body.getMinZ(),
                body.getMaxX(), destination.getY(), body.getMaxZ());
        boolean supported = landing != Landing.STANDING;
        int maxY = Math.min(world.getMaxHeight() - 1, Location.locToBlock(Math.nextDown(body.getMaxY())));
        for (int x = Location.locToBlock(body.getMinX()); x <= Location.locToBlock(body.getMaxX()); x++) {
            for (int z = Location.locToBlock(body.getMinZ()); z <= Location.locToBlock(body.getMaxZ()); z++) {
                for (int y = Math.max(world.getMinHeight(), destination.getBlockY() - 1); y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material material = block.getType();
                    if (material.isAir()) {
                        continue;
                    }
                    BoundingBox cell = new BoundingBox(x, y, z, x + 1.0, y + 1.0, z + 1.0);
                    if (HAZARDS.contains(material) && (cell.overlaps(body) || landing == Landing.STANDING && cell.overlaps(support))) {
                        return false;
                    }
                    boolean water = material == Material.WATER
                            || block.getBlockData() instanceof Waterlogged data && data.isWaterlogged();
                    if (water && landing != Landing.SWIMMING && cell.overlaps(body)) {
                        return false;
                    }
                    if (block.getCollisionShape().overlaps(body.clone().shift(-x, -y, -z))) {
                        return false;
                    }
                    if (!supported && ClaimSafeLocation.safeFloor(material)
                            && block.getCollisionShape().overlaps(support.clone().shift(-x, -y, -z))) {
                        supported = true;
                    }
                }
            }
        }
        return supported;
    }

    private boolean spectator(Player player) {
        return this != ASTRAL_RETURN && player.getGameMode() == GameMode.SPECTATOR;
    }

    private static boolean keptFloor(Player player, Location destination) {
        int x = destination.getBlockX();
        int y = destination.getBlockY() - 1;
        int z = destination.getBlockZ();
        Block floor = destination.getWorld().getBlockAt(x, y, z);
        if (floor.getType().isAir()) {
            return true;
        }
        if (!ClaimSafeLocation.safeFloor(floor.getType())) {
            return false;
        }
        BoundingBox body = ClaimSafeLocation.standingBody(player, destination).shift(-x, -y, -z);
        BoundingBox support = new BoundingBox(body.getMinX(), body.getMinY() - 0.05, body.getMinZ(),
                body.getMaxX(), body.getMinY(), body.getMaxZ());
        return !floor.getCollisionShape().overlaps(body) && floor.getCollisionShape().overlaps(support);
    }

    private boolean unmodifiedBodyClear(Player player, Location destination) {
        BoundingBox body = ClaimSafeLocation.standingBody(player, destination);
        World world = destination.getWorld();
        for (int x = Location.locToBlock(body.getMinX()); x <= Location.locToBlock(Math.nextDown(body.getMaxX())); x++) {
            for (int z = Location.locToBlock(body.getMinZ()); z <= Location.locToBlock(Math.nextDown(body.getMaxZ())); z++) {
                for (int y = Math.max(world.getMinHeight(), destination.getBlockY()); y <= Math.min(world.getMaxHeight() - 1, Location.locToBlock(Math.nextDown(body.getMaxY()))); y++) {
                    if (preparedBlock(destination, x, y, z)) {
                        continue;
                    }
                    Block block = world.getBlockAt(x, y, z);
                    Material material = block.getType();
                    if (material.isAir()) {
                        continue;
                    }
                    boolean water = material == Material.WATER
                            || block.getBlockData() instanceof Waterlogged data && data.isWaterlogged();
                    if (HAZARDS.contains(material) || water && landing != Landing.SWIMMING
                            || block.getCollisionShape().overlaps(body.clone().shift(-x, -y, -z))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean preparedBlock(Location destination, int x, int y, int z) {
        int dx = x - destination.getBlockX();
        int dy = y - destination.getBlockY();
        int dz = z - destination.getBlockZ();
        for (Volume volume : volumes) {
            if (dx >= volume.minX() && dx < volume.maxX() && dy >= volume.minY() && dy < volume.maxY()
                    && dz >= volume.minZ() && dz < volume.maxZ()) {
                return true;
            }
        }
        return false;
    }

    private static boolean insideBorder(World world, BoundingBox bounds) {
        return world.getWorldBorder().isInside(new Location(world, bounds.getMinX(), bounds.getMinY(), bounds.getMinZ()))
                && world.getWorldBorder().isInside(new Location(world, Math.nextDown(bounds.getMaxX()),
                bounds.getMinY(), Math.nextDown(bounds.getMaxZ())));
    }

    private enum Landing {
        STANDING,
        FALLING,
        SWIMMING
    }

    private record Volume(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private BoundingBox at(Location destination) {
            return new BoundingBox(destination.getBlockX() + minX, destination.getBlockY() + minY,
                    destination.getBlockZ() + minZ, destination.getBlockX() + maxX,
                    destination.getBlockY() + maxY, destination.getBlockZ() + maxZ);
        }
    }
}
