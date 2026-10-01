package art.arcane.adapt.gameplay.demo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.BoundingBox;

public final class DemoStudio {
    static final int PLATE_HALF_WIDTH = 15;
    static final int PLATE_NORTH = 60;
    static final int PLATE_SOUTH = 10;
    static final int PLATE_DEPTH = 3;
    static final int CLEAR_HEIGHT = 32;
    private static final int RANDOM_TICK_SPEED = 3;
    private static final List<Integer> SAMPLE_X_OFFSETS = List.of(-10, 0, 10);
    private static final List<Integer> SAMPLE_Z_OFFSETS = List.of(-45, -20, 5);
    private static final String TEAM = "adaptdemo";
    private static final String VANILLA_NAMESPACE = NamespacedKey.MINECRAFT + ":";

    private final Plugin plugin;
    private final Set<UUID> sparringIds = new HashSet<>();
    private World world;
    private boolean plated;
    private int originX;
    private int originY;
    private int originZ;
    private Ground ground = Ground.GRASSLAND;
    private DemoSet current;
    private PlateSearch search;

    public DemoStudio(Plugin plugin) {
        this.plugin = plugin;
    }

    public String world(String name) {
        World target = loadedWorld(name);
        if (target == null && name.startsWith(VANILLA_NAMESPACE)) {
            throw new IllegalStateException(name + " is not loaded; vanilla dimensions load at server start, the Nether only with"
                    + " misc.enable-nether: true in config/paper-global.yml");
        }
        if (target == null) {
            return "ADAPT_QA DEMO WORLD PENDING " + name;
        }
        target.setGameRule(GameRules.ADVANCE_TIME, false);
        target.setGameRule(GameRules.ADVANCE_WEATHER, false);
        target.setGameRule(GameRules.SPAWN_MOBS, false);
        target.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        target.setGameRule(GameRules.KEEP_INVENTORY, true);
        target.setGameRule(GameRules.FALL_DAMAGE, false);
        target.setGameRule(GameRules.SEND_COMMAND_FEEDBACK, false);
        target.setDifficulty(Difficulty.NORMAL);
        if (target.getEnvironment() == World.Environment.NORMAL) {
            target.setTime(6000);
        }
        target.setStorm(false);
        target.setThundering(false);
        world = target;
        plated = false;
        current = null;
        cancelSearch();
        return "ADAPT_QA DEMO WORLD " + name;
    }

    public String locate(int radius, int step) {
        requireWorld();
        if (search == null || !search.matches(world, radius, step)) {
            cancelSearch();
            search = PlateSearch.start(world, radius, step, survey());
        }
        String reply = search.poll();
        if (!reply.startsWith(PlateSearch.PENDING)) {
            search = null;
        }
        return reply;
    }

    public String plate(int x, int z) {
        requireWorld();
        ground = ground(world);
        originX = x;
        originZ = z;
        originY = (ground == Ground.NETHERRACK ? netherFloor(x, z) : medianSurface(x, z)) + 1;
        for (int dx = -PLATE_HALF_WIDTH; dx <= PLATE_HALF_WIDTH; dx++) {
            for (int dz = -PLATE_NORTH; dz <= PLATE_SOUTH; dz++) {
                world.getBlockAt(x + dx, originY - 3, z + dz).setType(ground.fill(), false);
                world.getBlockAt(x + dx, originY - 2, z + dz).setType(ground.fill(), false);
                world.getBlockAt(x + dx, originY - 1, z + dz).setType(ground.top(), false);
                for (int dy = 0; dy <= CLEAR_HEIGHT; dy++) {
                    world.getBlockAt(x + dx, originY + dy, z + dz).setType(Material.AIR, false);
                }
            }
        }
        plated = true;
        current = null;
        return "ADAPT_QA DEMO PLATE " + x + " " + originY + " " + z;
    }

    public String set(String name) {
        requirePlate();
        DemoSet next = DemoSets.get(name);
        reset();
        resetGameRules();
        current = next;
        current.build(world, originX, originY, originZ);
        return "ADAPT_QA DEMO SET " + name + " " + json(current);
    }

    public String actor(String playerName, boolean opponent) {
        requireSet();
        Player player = onlinePlayer(playerName);
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setHealth(Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue());
        player.setFallDistance(0f);
        hideNameTag(player);
        player.teleport(absolute(opponent ? current.opponentStart() : current.actorStart()));
        removeStrays();
        player.setLevel(0);
        player.setExp(0f);
        player.setTotalExperience(0);
        player.setRemainingAir(player.getMaximumAir());
        player.setFireTicks(0);
        player.setArrowsInBody(0);
        player.updateInventory();
        return "ADAPT_QA DEMO ACTOR " + playerName;
    }

    public String park(String playerName) {
        Player player = onlinePlayer(playerName);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
        return "ADAPT_QA DEMO PARK " + playerName;
    }

    public String sparring() {
        requireSet();
        removeSparring();
        for (DemoSet.Sparring spawn : current.sparring()) {
            Location location = new Location(world, originX + spawn.x(), originY + spawn.y(), originZ + spawn.z());
            Entity entity = world.spawnEntity(location, EntityType.valueOf(spawn.entityType()), false);
            prepareSparring(entity);
            sparringIds.add(entity.getUniqueId());
        }
        return "ADAPT_QA DEMO SPARRING " + sparringIds.size();
    }

    public String hit(String playerName, String amount) {
        requireSet();
        double damage = Double.parseDouble(amount);
        Player player = onlinePlayer(playerName);
        LivingEntity source = nearestSparring(player);
        if (source == null) {
            player.damage(damage);
        } else {
            player.damage(damage, source);
        }
        return "ADAPT_QA DEMO HIT " + playerName + " " + amount;
    }

    public String sync(String playerName) {
        onlinePlayer(playerName).updateInventory();
        return "ADAPT_QA DEMO SYNC " + playerName;
    }

    public String reset() {
        requirePlate();
        removeSparring();
        removeStrays();
        if (current != null) {
            current.clear(world, originX, originY, originZ);
        }
        for (int dx = -PLATE_HALF_WIDTH; dx <= PLATE_HALF_WIDTH; dx++) {
            for (int dz = -PLATE_NORTH; dz <= PLATE_SOUTH; dz++) {
                world.getBlockAt(originX + dx, originY - 1, originZ + dz).setType(ground.top(), false);
                for (int dy = 0; dy <= CLEAR_HEIGHT; dy++) {
                    world.getBlockAt(originX + dx, originY + dy, originZ + dz).setType(Material.AIR, false);
                }
            }
        }
        current = null;
        return "ADAPT_QA DEMO RESET";
    }

    private PlateSurvey survey() {
        if (ground(world) == Ground.NETHERRACK) {
            return new NetherSurvey(world, Bukkit.getScheduler().getMainThreadExecutor(plugin));
        }
        return IrisSurvey.create(world);
    }

    private void cancelSearch() {
        if (search != null) {
            search.cancel();
            search = null;
        }
    }

    private static Player onlinePlayer(String name) {
        return Objects.requireNonNull(Bukkit.getPlayerExact(name), "Player not online: " + name);
    }

    private static World loadedWorld(String name) {
        NamespacedKey key = name.contains(":") ? NamespacedKey.fromString(name) : null;
        return key == null ? Bukkit.getWorld(name) : Bukkit.getWorld(key);
    }

    private int medianSurface(int x, int z) {
        int[] heights = new int[SAMPLE_X_OFFSETS.size() * SAMPLE_Z_OFFSETS.size()];
        int index = 0;
        for (int dx : SAMPLE_X_OFFSETS) {
            for (int dz : SAMPLE_Z_OFFSETS) {
                heights[index] = world.getHighestBlockYAt(x + dx, z + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                index++;
            }
        }
        Arrays.sort(heights);
        return heights[heights.length / 2];
    }

    private int netherFloor(int x, int z) {
        OptionalInt floor = NetherSurvey.floor(world, x, z);
        if (floor.isEmpty()) {
            throw new IllegalStateException("No Nether floor with " + NetherSurvey.HEADROOM + " blocks of headroom at " + x + " " + z);
        }
        return floor.getAsInt();
    }

    private static void prepareSparring(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        living.setRemoveWhenFarAway(false);
        if (living instanceof Zombie zombie) {
            zombie.setShouldBurnInDay(false);
            Objects.requireNonNull(zombie.getEquipment()).setHelmet(new ItemStack(Material.LEATHER_HELMET));
        }
        if (living instanceof AbstractSkeleton skeleton) {
            skeleton.setShouldBurnInDay(false);
            Objects.requireNonNull(skeleton.getEquipment()).setItemInMainHand(new ItemStack(Material.BOW));
        }
    }

    private void resetGameRules() {
        world.setGameRule(GameRules.FALL_DAMAGE, false);
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, RANDOM_TICK_SPEED);
        world.setGameRule(GameRules.MOB_GRIEFING, true);
    }

    private BoundingBox plateBox() {
        return new BoundingBox(originX - PLATE_HALF_WIDTH, originY - PLATE_DEPTH, originZ - PLATE_NORTH,
                originX + PLATE_HALF_WIDTH + 1, originY + CLEAR_HEIGHT + 1, originZ + PLATE_SOUTH + 1);
    }

    private void removeStrays() {
        for (Entity entity : world.getNearbyEntities(plateBox())) {
            if (!(entity instanceof Player) && !sparringIds.contains(entity.getUniqueId())) {
                entity.remove();
            }
        }
    }

    private void removeSparring() {
        for (UUID id : sparringIds) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        sparringIds.clear();
    }

    private LivingEntity nearestSparring(Player player) {
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        Location playerLocation = player.getLocation();
        for (UUID id : sparringIds) {
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof LivingEntity living) || !living.isValid() || !living.getWorld().equals(player.getWorld())) {
                continue;
            }
            double distance = living.getLocation().distanceSquared(playerLocation);
            if (distance < best) {
                best = distance;
                nearest = living;
            }
        }
        return nearest;
    }

    private Location absolute(DemoSet.Pose pose) {
        return new Location(world, originX + pose.x(), originY + pose.y(), originZ + pose.z(), pose.yaw(), pose.pitch());
    }

    private String json(DemoSet set) {
        String camera = set.followCamera() ? followJson(set.camera()) : json(absolute(set.camera()));
        return "{\"actor\":" + json(absolute(set.actorStart())) + ",\"camera\":" + camera + "}";
    }

    private static String followJson(DemoSet.Pose pose) {
        return "{\"x\":" + pose.x() + ",\"y\":" + pose.y() + ",\"z\":" + pose.z()
                + ",\"yaw\":" + pose.yaw() + ",\"pitch\":" + pose.pitch() + ",\"follow\":true}";
    }

    private static String json(Location location) {
        return "{\"x\":" + location.getX() + ",\"y\":" + location.getY() + ",\"z\":" + location.getZ()
                + ",\"yaw\":" + location.getYaw() + ",\"pitch\":" + location.getPitch() + "}";
    }

    private static void hideNameTag(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(TEAM);
        if (team == null) {
            team = scoreboard.registerNewTeam(TEAM);
        }
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        team.addEntry(player.getName());
    }

    private static Ground ground(World world) {
        return world.getEnvironment() == World.Environment.NETHER ? Ground.NETHERRACK : Ground.GRASSLAND;
    }

    private void requireWorld() {
        if (world == null) {
            throw new IllegalStateException("Run demo world <name> first");
        }
    }

    private void requirePlate() {
        requireWorld();
        if (!plated) {
            throw new IllegalStateException("Run demo plate <x> <z> first");
        }
    }

    private void requireSet() {
        requirePlate();
        if (current == null) {
            throw new IllegalStateException("Run demo set <name> first");
        }
    }

    private enum Ground {
        GRASSLAND(Material.DIRT, Material.GRASS_BLOCK),
        NETHERRACK(Material.NETHERRACK, Material.NETHERRACK);

        private final Material fill;
        private final Material top;

        Ground(Material fill, Material top) {
            this.fill = fill;
            this.top = top;
        }

        Material fill() {
            return fill;
        }

        Material top() {
            return top;
        }
    }
}
