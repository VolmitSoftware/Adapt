package art.arcane.adapt.claims;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ClaimRespawnLocation {
    private static final int MAX_ANCHORS = 8;
    private static final int MAX_LOADED_COLUMNS = 9;
    private static final int VERTICAL_RANGE = 8;
    private static final List<Offset> OFFSETS = offsets();

    private final ClaimsService claims;
    private final ClaimSafeLocation terrain;

    public ClaimRespawnLocation(ClaimsService claims, ClaimSafeLocation terrain) {
        this.claims = Objects.requireNonNull(claims);
        this.terrain = Objects.requireNonNull(terrain);
    }

    public Optional<Location> find(Player player, Location intended) {
        for (Location anchor : anchors(player, intended)) {
            int loadedColumns = 0;
            for (Offset offset : OFFSETS) {
                Location column = new Location(anchor.getWorld(), anchor.getBlockX() + offset.x() + 0.5,
                        anchor.getBlockY(), anchor.getBlockZ() + offset.z() + 0.5, intended.getYaw(), intended.getPitch());
                if (!claims.canTeleport(player, column)
                        || !column.getWorld().isChunkLoaded(column.getBlockX() >> 4, column.getBlockZ() >> 4)) {
                    continue;
                }
                if (++loadedColumns > MAX_LOADED_COLUMNS) {
                    break;
                }
                for (int distance = 0; distance <= VERTICAL_RANGE; distance++) {
                    Location below = column.clone().subtract(0, distance, 0);
                    if (safe(player, anchor, below)) {
                        return Optional.of(below);
                    }
                    if (distance > 0) {
                        Location above = column.clone().add(0, distance, 0);
                        if (safe(player, anchor, above)) {
                            return Optional.of(above);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static List<Offset> offsets() {
        List<Offset> offsets = new ArrayList<>(33);
        offsets.add(new Offset(0, 0));
        for (int radius = 16; radius <= 64; radius += 16) {
            offsets.add(new Offset(-radius, 0));
            offsets.add(new Offset(radius, 0));
            offsets.add(new Offset(0, -radius));
            offsets.add(new Offset(0, radius));
            offsets.add(new Offset(-radius, -radius));
            offsets.add(new Offset(-radius, radius));
            offsets.add(new Offset(radius, -radius));
            offsets.add(new Offset(radius, radius));
        }
        return List.copyOf(offsets);
    }

    private List<Location> anchors(Player player, Location intended) {
        List<Location> anchors = new ArrayList<>(MAX_ANCHORS);
        Location source = player.getLocation();
        add(anchors, intended);
        add(anchors, source);
        add(anchors, intended.getWorld().getSpawnLocation());
        add(anchors, source.getWorld().getSpawnLocation());
        for (World world : Bukkit.getWorlds()) {
            if (anchors.size() == MAX_ANCHORS) {
                break;
            }
            add(anchors, world.getSpawnLocation());
        }
        return anchors;
    }

    private void add(List<Location> anchors, Location location) {
        if (!anchors.contains(location)) {
            anchors.add(location.clone());
        }
    }

    private boolean safe(Player player, Location anchor, Location candidate) {
        World world = candidate.getWorld();
        BoundingBox body = ClaimSafeLocation.standingBody(player, candidate);
        if (candidate.getBlockY() <= world.getMinHeight() || body.getMaxY() > world.getMaxHeight()) {
            return false;
        }
        if (world.hasCeiling() && world.getLogicalHeight() > 0) {
            int ceiling = world.getMinHeight() + world.getLogicalHeight();
            if (anchor.getY() < ceiling ? body.getMaxY() > ceiling : candidate.getY() < ceiling) {
                return false;
            }
        }
        for (int x = Location.locToBlock(body.getMinX()) >> 4; x <= Location.locToBlock(Math.nextDown(body.getMaxX())) >> 4; x++) {
            for (int z = Location.locToBlock(body.getMinZ()) >> 4; z <= Location.locToBlock(Math.nextDown(body.getMaxZ())) >> 4; z++) {
                if (!world.isChunkLoaded(x, z) || !claims.canTeleport(player,
                        new Location(world, (x << 4) + 8.5, candidate.getY(), (z << 4) + 8.5))) {
                    return false;
                }
            }
        }
        return terrain.safeLandingOwned(player, candidate);
    }

    private record Offset(int x, int z) {
    }
}
