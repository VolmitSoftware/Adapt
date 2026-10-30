package art.arcane.adapt.gameplay.demo.sets;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.claims.ClaimBlock;
import art.arcane.adapt.api.claims.ClaimChunk;
import art.arcane.adapt.api.claims.ClaimCity;
import art.arcane.adapt.api.claims.ClaimPlot;
import art.arcane.adapt.api.claims.ClaimProgress;
import art.arcane.adapt.api.claims.ClaimRanks;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.claims.ClaimsService;
import art.arcane.adapt.gameplay.demo.DemoGeometry;
import art.arcane.adapt.gameplay.demo.DemoScope;
import art.arcane.adapt.gameplay.demo.DemoSet;
import art.arcane.adapt.gameplay.demo.DemoSetProvider;
import art.arcane.adapt.util.common.scheduling.J;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class ClaimsSets implements DemoSetProvider {
    private static final String OPPONENT = "AQAOpponent";
    private static final DemoSet.Pose NEAR_ACTOR = new DemoSet.Pose(0.5, 0, 0.5, 180F, 0F);
    private static final DemoSet.Pose NEAR_OPPONENT = new DemoSet.Pose(3.5, 0, -1.5, 90F, 0F);
    private static final DemoSet.Pose NEAR_CAMERA = new DemoSet.Pose(7.5, 4.5, 4.5, 136F, 24F);
    private static final List<DemoSet> SETS = List.of(
            new ClaimsSet("claims-founding", NEAR_ACTOR, NEAR_OPPONENT, NEAR_CAMERA),
            new ClaimsSet("claims-citizenship", NEAR_ACTOR, new DemoSet.Pose(2.5, 0, -1.5, 45F, 0F), NEAR_CAMERA),
            new ClaimsSet("claims-fortification", NEAR_ACTOR, new DemoSet.Pose(3.5, 0, -3.5, 35F, 0F), NEAR_CAMERA),
            new ClaimsSet("claims-expansion", new DemoSet.Pose(2.5, 0, -28.5, 180F, 0F),
                    NEAR_OPPONENT, new DemoSet.Pose(12.5, 12.0, -36.0, 90F, 35F)),
            new ClaimsSet("claims-homestead", new DemoSet.Pose(0.5, 0, -47.5, 180F, 0F),
                    new DemoSet.Pose(3.5, 0, -47.5, 180F, 20F), new DemoSet.Pose(8.5, 5.5, -43.0, 133F, 27F)));
    private static volatile Scene current;
    private static volatile FlightPreparation flightPreparation;
    private static FlightObserver flightObserver;

    @Override
    public String skill() {
        return "claims";
    }

    @Override
    public List<DemoSet> sets() {
        return SETS;
    }

    public static void clearDemoState(World world, BoundingBox plate) {
        ClaimsService service = ClaimsService.current();
        if (service != null) {
            for (ClaimCity city : service.manager().snapshot().cities().values()) {
                Player founder = Bukkit.getPlayer(city.founder());
                ClaimBlock anchor = city.anchor();
                if (founder != null && DemoScope.ownsDemoState(founder.getName()) && anchor.worldId().equals(world.getUID())
                        && DemoScope.contains(plate, anchor.x(), anchor.y(), anchor.z())) {
                    service.manager().removeCity(city.id());
                }
            }
        }
        current = null;
        flightPreparation = null;
        clearFlightObserver();
    }

    public static String control(Player actor, String phase) {
        if (!DemoScope.ownsDemoState(actor.getName())) {
            throw new IllegalArgumentException("Claims scenes require a studio actor");
        }
        ClaimsService service = Objects.requireNonNull(ClaimsService.current(), "Claims service unavailable");
        Scene scene = Objects.requireNonNull(current, "Select a Claims set first");
        if (!service.enabled() || !actor.getWorld().getUID().equals(scene.world())) {
            throw new IllegalStateException("Enable Claims and place the actor on the Claims set");
        }
        if (service.bypass(actor)) {
            throw new IllegalStateException("The Claims actor must not have administrative bypass");
        }
        switch (phase) {
            case "flight-prepare", "flight-inside-prepare", "glide-inside-prepare" -> {
                return prepareFlight(service, scene, actor, phase);
            }
            case "flight-snapshot" -> {
                return flightSnapshot(service, scene, actor);
            }
            case "city" -> createCity(service, scene, actor);
            case "raid" -> prepareRaid(service, scene);
            case "homestead" -> prepareHomestead(service, scene, actor);
            case "boundaries" -> service.interactions().boundaries(actor);
            case "members" -> service.interactions().members(actor);
            case "accept" -> service.interactions().accept(actor);
            case "assert-founded" -> requireCitySize(service, actor, 9);
            case "assert-expanded" -> requireCitySize(service, actor, 10);
            case "assert-invited" -> {
                if (service.manager().invitation(opponent().getUniqueId()).isEmpty()) {
                    throw new IllegalStateException("The shovel did not send the city invitation");
                }
            }
            case "assert-joined" -> {
                ClaimCity city = service.manager().snapshot().cityOf(actor.getUniqueId()).orElseThrow();
                if (!city.members().contains(opponent().getUniqueId())) {
                    throw new IllegalStateException("The opponent did not join the city");
                }
            }
            case "assert-resisting" -> requireResistance(service, scene.block(0, 1, -2));
            case "assert-raided" -> requireAir(scene.block(0, 1, -2));
            case "assert-plot" -> {
                ClaimPlot plot = service.manager().snapshot().plots().get(actor.getUniqueId());
                if (plot == null || !plot.anchor().equals(scene.block(0, 0, -50))) {
                    throw new IllegalStateException("The personal lodestone was not activated");
                }
            }
            case "assert-personal-resistance" -> {
                requireAir(scene.block(-2, 0, -50));
                requireResistance(service, scene.block(2, 0, -50));
            }
            case "assert-personal-breach" -> requireAir(scene.block(2, 0, -50));
            default -> throw new IllegalArgumentException("Unknown Claims scene phase " + phase);
        }
        return "ADAPT_QA DEMO CLAIMS " + phase + " OK";
    }

    private static void build(ClaimsSet set, World world, int x, int y, int z) {
        current = new Scene(world.getUID(), x, y, z, set.name());
        int anchorZ = set.name().equals("claims-expansion") ? -31 : -2;
        world.getBlockAt(x, y, z + anchorZ).setType(Material.LODESTONE, false);
        DemoGeometry.fill(world, Material.STONE_BRICKS, x - 1, y - 1, z + anchorZ - 1, x + 1, y - 1, z + anchorZ + 1);
        if (set.name().equals("claims-fortification")) {
            world.getBlockAt(x, y, z - 2).setType(Material.STONE_BRICKS, false);
            world.getBlockAt(x, y, z - 6).setType(Material.LODESTONE, false);
            DemoGeometry.fill(world, Material.STONE_BRICKS, x - 1, y, z - 2, x + 1, y + 2, z - 2);
            world.getBlockAt(x, y + 1, z - 2).setType(Material.WHITE_WOOL, false);
        } else if (set.name().equals("claims-homestead")) {
            world.getBlockAt(x, y, z - 50).setType(Material.LODESTONE, false);
            world.getBlockAt(x - 2, y, z - 50).setType(Material.WHITE_WOOL, false);
            world.getBlockAt(x + 2, y, z - 50).setType(Material.WHITE_WOOL, false);
            DemoGeometry.fill(world, Material.STONE_BRICKS, x - 3, y - 1, z - 51, x + 3, y - 1, z - 49);
        }
    }

    private static void createCity(ClaimsService service, Scene scene, Player founder) {
        int anchorZ = switch (scene.name()) {
            case "claims-expansion" -> -31;
            case "claims-fortification" -> -6;
            default -> -2;
        };
        service.manager().found(founder.getUniqueId(), "Riverside", scene.block(0, 0, anchorZ),
                service.refreshRanks(founder), ClaimsService.world(founder.getWorld()));
        service.manager().setOnline(founder.getUniqueId(), true, System.currentTimeMillis());
    }

    private static void prepareRaid(ClaimsService service, Scene scene) {
        Player founder = opponent();
        learnFounder(service, founder);
        createCity(service, scene, founder);
    }

    private static void prepareHomestead(ClaimsService service, Scene scene, Player owner) {
        Player founder = opponent();
        learnFounder(service, founder);
        createCity(service, scene, founder);
        ClaimChunk anchor = scene.block(0, 0, -2).chunk();
        for (int north = 2; north <= 4; north++) {
            for (int east = -1; east <= 1; east++) {
                service.manager().expand(founder.getUniqueId(), new ClaimChunk(scene.world(), anchor.x() + east, anchor.z() - north),
                        ClaimsService.world(founder.getWorld()));
            }
        }
        service.manager().invite(founder.getUniqueId(), owner.getUniqueId(), System.currentTimeMillis());
        service.manager().accept(owner.getUniqueId(), founder.getUniqueId(), System.currentTimeMillis());
    }

    private static void learnFounder(ClaimsService service, Player founder) {
        if (service.bypass(founder)) {
            throw new IllegalStateException("The Claims founder must not have administrative bypass");
        }
        AdaptPlayer runtime = Adapt.instance.getAdaptServer().getOnlineAdaptPlayer(founder.getUniqueId());
        if (runtime == null || !runtime.isRuntimeReady()) {
            throw new IllegalStateException("The founder profile is not ready");
        }
        Skill<?> skill = Adapt.instance.getAdaptServer().getSkillRegistry().getAnySkill("claims");
        PlayerSkillLine line = Objects.requireNonNull(runtime.getData().getSkillLine("claims"));
        for (Adaptation<?> adaptation : Objects.requireNonNull(skill).getAdaptations()) {
            line.setAdaptation(adaptation, adaptation.getName().equals("claims-homestead") ? 0 : 1);
        }
        ClaimRanks ranks = service.refreshRanks(founder);
        if (!ranks.founding() || ranks.fortification() != 1) {
            throw new IllegalStateException("The founder lacks the required Claims ranks");
        }
    }

    private static String prepareFlight(ClaimsService service, Scene scene, Player actor, String phase) {
        if (!scene.name().equals("claims-founding")) {
            throw new IllegalStateException("Select claims-founding before flight checks");
        }
        FlightPreparation previous = flightPreparation;
        if (previous != null && !previous.ready() && previous.error() == null) {
            throw new IllegalStateException("A flight preparation is already running");
        }
        learnFounder(service, actor);
        ClaimBlock anchor = scene.block(0, 0, -2);
        ClaimCity city = service.manager().snapshot().cityAt(anchor.chunk()).orElse(null);
        if (city != null) {
            if (!city.founder().equals(actor.getUniqueId()) || !city.anchor().equals(anchor)) {
                throw new IllegalStateException("The flight scene must belong to its studio actor");
            }
            service.manager().removeCity(city.id());
        }
        boolean outside = phase.equals("flight-prepare");
        double x = outside ? ((anchor.chunk().x() - 1) << 4) - 8.5 : scene.x() + 2.5;
        double y = scene.y() + (outside ? 12 : phase.equals("glide-inside-prepare") ? 10 : 0);
        double z = outside ? anchor.z() + 0.5 : scene.z() + 2.5;
        Location destination = new Location(actor.getWorld(), x, y, z, -90F, 0F);
        actor.setFlying(false);
        actor.setGliding(false);
        actor.setAllowFlight(false);
        actor.setFallDistance(0);
        actor.setVelocity(new Vector());
        flightPreparation = new FlightPreparation(actor.getUniqueId(), phase, false, null);
        clearFlightObserver();
        flightObserver = new FlightObserver(actor.getUniqueId());
        Bukkit.getPluginManager().registerEvents(flightObserver, JavaPlugin.getProvidingPlugin(ClaimsSets.class));
        FlightLaunch launch = new FlightLaunch(service, scene, actor, phase);
        try {
            actor.teleportAsync(destination).whenComplete((moved, failure) -> J.runEntity(actor,
                    () -> finishFlightPreparation(launch, Boolean.TRUE.equals(moved), failure)));
        } catch (RuntimeException failure) {
            flightPreparation = new FlightPreparation(actor.getUniqueId(), phase, false, failure.getMessage());
            throw failure;
        }
        return "ADAPT_QA DEMO CLAIMS " + phase + " PREPARING";
    }

    private static void finishFlightPreparation(FlightLaunch launch, boolean moved, Throwable failure) {
        if (failure != null) {
            Adapt.error(failure);
        }
        if (current != launch.scene()) {
            return;
        }
        Player actor = launch.actor();
        String phase = launch.phase();
        if (failure != null || !moved || !actor.isOnline()) {
            flightPreparation = new FlightPreparation(actor.getUniqueId(), phase, false, "Preparation teleport failed or set changed");
            return;
        }
        try {
            createCity(launch.service(), launch.scene(), actor);
            actor.setGameMode(GameMode.SURVIVAL);
            actor.setVelocity(new Vector());
            actor.setAllowFlight(phase.equals("flight-inside-prepare"));
            actor.setFallDistance(0);
            actor.setHealth(20);
            actor.setFoodLevel(20);
            actor.setSaturation(20);
            actor.getInventory().setChestplate(new ItemStack(phase.equals("flight-inside-prepare") ? Material.IRON_CHESTPLATE : Material.ELYTRA));
            actor.getInventory().setItem(0, new ItemStack(Material.FIREWORK_ROCKET, 64));
            actor.getInventory().setItem(1, new ItemStack(Material.IRON_SHOVEL));
            actor.getInventory().setHeldItemSlot(0);
            flightPreparation = new FlightPreparation(actor.getUniqueId(), phase, true, null);
            actor.sendMessage("ADAPT_QA DEMO CLAIMS " + phase + " READY");
        } catch (RuntimeException error) {
            Adapt.error(error);
            flightPreparation = new FlightPreparation(actor.getUniqueId(), phase, false, error.getMessage());
        }
    }

    private static String flightSnapshot(ClaimsService service, Scene scene, Player actor) {
        FlightPreparation preparation = flightPreparation;
        ClaimBlock anchor = scene.block(0, 0, -2);
        ClaimChunk center = anchor.chunk();
        ClaimCity city = service.manager().snapshot().cityAt(ClaimsService.chunk(actor.getLocation())).orElse(null);
        Vector velocity = actor.getVelocity();
        ItemStack rockets = actor.getInventory().getItem(0);
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("ready", preparation != null && preparation.actor().equals(actor.getUniqueId()) && preparation.ready());
        snapshot.addProperty("phase", preparation == null ? null : preparation.phase());
        snapshot.addProperty("error", preparation == null ? null : preparation.error());
        snapshot.addProperty("x", actor.getX());
        snapshot.addProperty("y", actor.getY());
        snapshot.addProperty("z", actor.getZ());
        snapshot.addProperty("minimumX", (center.x() - 1) << 4);
        snapshot.addProperty("maximumXExclusive", (center.x() + 2) << 4);
        snapshot.addProperty("minimumZ", (center.z() - 1) << 4);
        snapshot.addProperty("maximumZExclusive", (center.z() + 2) << 4);
        snapshot.addProperty("claimed", city != null);
        snapshot.addProperty("member", city != null && city.includes(actor.getUniqueId()));
        snapshot.addProperty("bypass", service.bypass(actor));
        snapshot.addProperty("allowFlight", actor.getAllowFlight());
        snapshot.addProperty("flying", actor.isFlying());
        snapshot.addProperty("gliding", actor.isGliding());
        snapshot.addProperty("onGround", actor.isOnGround());
        snapshot.addProperty("health", actor.getHealth());
        snapshot.addProperty("fallDistance", actor.getFallDistance());
        snapshot.addProperty("velocityX", velocity.getX());
        snapshot.addProperty("velocityY", velocity.getY());
        snapshot.addProperty("velocityZ", velocity.getZ());
        snapshot.addProperty("rockets", rockets == null || rockets.getType() != Material.FIREWORK_ROCKET ? 0 : rockets.getAmount());
        if (flightObserver != null && flightObserver.actor.equals(actor.getUniqueId())) {
            snapshot.addProperty("glideRequests", flightObserver.glideRequests.get());
            snapshot.addProperty("glideDenials", flightObserver.glideDenials.get());
            snapshot.addProperty("flightRequests", flightObserver.flightRequests.get());
            snapshot.addProperty("flightDenials", flightObserver.flightDenials.get());
            snapshot.addProperty("boundaryDenials", flightObserver.boundaryDenials.get());
            snapshot.addProperty("boundaryEntries", flightObserver.boundaryEntries.get());
        }
        return "ADAPT_QA DEMO CLAIMS flight-snapshot " + snapshot;
    }

    private static void clearFlightObserver() {
        if (flightObserver != null) {
            HandlerList.unregisterAll(flightObserver);
            flightObserver = null;
        }
    }

    private static Player opponent() {
        return Objects.requireNonNull(Bukkit.getPlayerExact(OPPONENT), "The Claims set requires an opponent client");
    }

    private static void requireCitySize(ClaimsService service, Player founder, int chunks) {
        ClaimCity city = service.manager().snapshot().cityOf(founder.getUniqueId()).orElseThrow();
        if (city.chunks().size() != chunks) {
            throw new IllegalStateException("City has " + city.chunks().size() + " chunks; expected " + chunks);
        }
    }

    private static void requireResistance(ClaimsService service, ClaimBlock block) {
        ClaimProgress progress = service.manager().progress(block).orElseThrow(() -> new IllegalStateException("No completed raid break was recorded"));
        if (progress.breaking() <= 0D || progress.breaking() >= 1D || material(block) != Material.WHITE_WOOL) {
            throw new IllegalStateException("The target did not resist the completed break");
        }
    }

    private static void requireAir(ClaimBlock block) {
        if (!material(block).isAir()) {
            throw new IllegalStateException("The completed break did not remove the target block");
        }
    }

    private static Material material(ClaimBlock block) {
        World world = Objects.requireNonNull(Bukkit.getWorld(block.worldId()));
        return world.getBlockAt(block.x(), block.y(), block.z()).getType();
    }

    private record Scene(UUID world, int x, int y, int z, String name) {
        private ClaimBlock block(int dx, int dy, int dz) {
            return new ClaimBlock(world, x + dx, y + dy, z + dz);
        }
    }

    private record FlightPreparation(UUID actor, String phase, boolean ready, String error) {
    }

    private record FlightLaunch(ClaimsService service, Scene scene, Player actor, String phase) {
    }

    private static final class FlightObserver implements Listener {
        private final UUID actor;
        private final AtomicInteger glideRequests = new AtomicInteger();
        private final AtomicInteger glideDenials = new AtomicInteger();
        private final AtomicInteger flightRequests = new AtomicInteger();
        private final AtomicInteger flightDenials = new AtomicInteger();
        private final AtomicInteger boundaryDenials = new AtomicInteger();
        private final AtomicInteger boundaryEntries = new AtomicInteger();

        private FlightObserver(UUID actor) {
            this.actor = actor;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onGlide(EntityToggleGlideEvent event) {
            if (!event.isGliding() || !event.getEntity().getUniqueId().equals(actor)) {
                return;
            }
            glideRequests.incrementAndGet();
            if (event.isCancelled()) {
                glideDenials.incrementAndGet();
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onFlight(PlayerToggleFlightEvent event) {
            if (!event.isFlying() || !event.getPlayer().getUniqueId().equals(actor)) {
                return;
            }
            flightRequests.incrementAndGet();
            if (event.isCancelled()) {
                flightDenials.incrementAndGet();
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onMove(PlayerMoveEvent event) {
            if (event instanceof PlayerTeleportEvent
                    || !event.getPlayer().getUniqueId().equals(actor) || event.getTo() == null) {
                return;
            }
            Player player = event.getPlayer();
            ClaimsService service = ClaimsService.current();
            if (service != null && (player.isFlying() || player.isGliding() || !player.isOnGround())
                    && service.manager().snapshot().cityAt(ClaimsService.chunk(event.getTo())).isPresent()) {
                if (event.isCancelled()) {
                    boundaryDenials.incrementAndGet();
                } else if (service.manager().snapshot().cityAt(ClaimsService.chunk(event.getFrom())).isEmpty()) {
                    boundaryEntries.incrementAndGet();
                }
            }
        }
    }

    private record ClaimsSet(String name, Pose actorStart, Pose opponentStart, Pose camera) implements DemoSet {
        @Override
        public void build(World world, int x, int y, int z) {
            ClaimsSets.build(this, world, x, y, z);
        }

        @Override
        public List<Sparring> sparring() {
            return List.of();
        }
    }
}
