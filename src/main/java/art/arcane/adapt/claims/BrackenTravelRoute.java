package art.arcane.adapt.claims;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

enum BrackenTravelRoute {
    BRINE_TO_OVERWORLD(new Worlds(Set.of("bracken:the_brine"), Set.of("minecraft:overworld"), false), ClaimArrivalProfile.BODY_AIR),
    DORMIS_TO_VOID(new Worlds(Set.of("bracken:dormis"), Set.of("bracken:void"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    FAEWILD_TO_OVERWORLD(new Worlds(Set.of("bracken:the_faewild"), Set.of("minecraft:overworld"), false), ClaimArrivalProfile.BODY_AIR),
    FAEWILD_TO_PAX(new Worlds(Set.of("bracken:the_faewild"), Set.of("bracken:pax"), false), ClaimArrivalProfile.PAX_ARRIVAL),
    GLACIUM_TO_OVERWORLD(new Worlds(Set.of("bracken:glacium"), Set.of("minecraft:overworld"), false), ClaimArrivalProfile.BODY_AIR),
    NETHER_TO_OMNIDROME(new Worlds(Set.of("minecraft:the_nether"), Set.of("bracken:omnidrome"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    NETHER_TO_SANCTUM(new Worlds(Set.of("minecraft:the_nether"), Set.of("bracken:sanctum"), false), ClaimArrivalProfile.BODY_AIR),
    NETHER_TO_UNDERDARK(new Worlds(Set.of("minecraft:the_nether"), Set.of("bracken:the_underdark"), false), ClaimArrivalProfile.BODY_AIR),
    OMNIDROME_TO_NETHER(new Worlds(Set.of("bracken:omnidrome"), Set.of("minecraft:the_nether"), false), ClaimArrivalProfile.BODY_AIR),
    OMNIDROME_TO_SANCTUM(new Worlds(Set.of("bracken:omnidrome"), Set.of("bracken:sanctum"), false), ClaimArrivalProfile.BODY_AIR),
    OVERWORLD_TO_FAEWILD(new Worlds(Set.of("minecraft:overworld"), Set.of("bracken:the_faewild"), false), ClaimArrivalProfile.FAEWILD_PLATFORM),
    OVERWORLD_TO_GLACIUM(new Worlds(Set.of("minecraft:overworld", "bracken:varskspace"), Set.of("bracken:glacium"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    OVERWORLD_TO_PANACEA(new Worlds(Set.of("minecraft:overworld"), Set.of("bracken:panacea"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    OVERWORLD_TO_UNDERDARK(new Worlds(Set.of("minecraft:overworld"), Set.of("bracken:the_underdark"), false), ClaimArrivalProfile.BODY_AIR),
    PANACEA_TO_OVERWORLD(new Worlds(Set.of("bracken:panacea"), Set.of("minecraft:overworld"), false), ClaimArrivalProfile.BODY_AIR),
    PAX_TO_FAEWILD(new Worlds(Set.of("bracken:pax"), Set.of("bracken:the_faewild"), false), ClaimArrivalProfile.FAEWILD_LEAF),
    SANCTUM_TO_NETHER(new Worlds(Set.of("bracken:sanctum"), Set.of("minecraft:the_nether"), false), ClaimArrivalProfile.NETHERRACK_COLUMN),
    UNDERDARK_TO_NETHER(new Worlds(Set.of("bracken:the_underdark"), Set.of("minecraft:the_nether"), false), ClaimArrivalProfile.BODY_AIR),
    UNDERDARK_TO_OVERWORLD(new Worlds(Set.of("bracken:the_underdark"), Set.of("minecraft:overworld"), false), ClaimArrivalProfile.UNDERDARK_TO_OVERWORLD),
    VARSKSPACE_TO_OVERWORLD(new Worlds(Set.of("bracken:varskspace"), Set.of("minecraft:overworld"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    VARSKSPACE_TRAVEL(new Worlds(Set.of(), Set.of("bracken:varskspace"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    SANCTUM_TO_OMNIDROME_EXISTING(new Worlds(Set.of("bracken:sanctum"), Set.of("bracken:omnidrome"), false), ClaimArrivalProfile.BRACKEN_STANDING),
    SANCTUM_TO_OMNIDROME_NEW(new Worlds(Set.of("bracken:sanctum"), Set.of("bracken:omnidrome"), false), ClaimArrivalProfile.OMNI_PLATFORM),
    OVERWORLD_TO_BRINE_EXISTING(new Worlds(Set.of("minecraft:overworld"), Set.of("bracken:the_brine"), false), ClaimArrivalProfile.BRACKEN_SWIMMING),
    OVERWORLD_TO_BRINE_NEW(new Worlds(Set.of("minecraft:overworld"), Set.of("bracken:the_brine"), false), ClaimArrivalProfile.BRINE_PORTAL),
    GATEWAY_TO_DORMIS_EXISTING(new Worlds(Set.of("bracken:pax"), Set.of("bracken:dormis"), false), ClaimArrivalProfile.BRACKEN_STANDING),
    GATEWAY_TO_DORMIS_NEW(new Worlds(Set.of("bracken:pax"), Set.of("bracken:dormis"), false), ClaimArrivalProfile.DORMIS_CAGE),
    GATEWAY_TO_BRINE(new Worlds(Set.of("bracken:pax"), Set.of("bracken:the_brine"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    GATEWAY_TO_END(new Worlds(Set.of("bracken:pax"), Set.of("minecraft:the_end"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    GATEWAY_TO_FAEWILD(new Worlds(Set.of("bracken:pax"), Set.of("bracken:the_faewild"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    GATEWAY_TO_GLACIUM(new Worlds(Set.of("bracken:pax"), Set.of("bracken:glacium"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    GATEWAY_TO_OMNIDROME(new Worlds(Set.of("bracken:pax"), Set.of("bracken:omnidrome"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    GATEWAY_TO_OVERWORLD(new Worlds(Set.of("bracken:pax"), Set.of("minecraft:overworld"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    GATEWAY_TO_PANACEA(new Worlds(Set.of("bracken:pax"), Set.of("bracken:panacea"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    GATEWAY_TO_PAX(new Worlds(Set.of("bracken:pax"), Set.of("bracken:pax"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    GATEWAY_TO_SANCTUM(new Worlds(Set.of("bracken:pax"), Set.of("bracken:sanctum"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    GATEWAY_TO_VARSKSPACE(new Worlds(Set.of("bracken:pax"), Set.of("bracken:varskspace"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    GATEWAY_TO_VOID(new Worlds(Set.of("bracken:pax"), Set.of("bracken:void"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    GATEWAY_TO_NETHER(new Worlds(Set.of("bracken:pax"), Set.of("minecraft:the_nether"), false), ClaimArrivalProfile.BODY_AIR),
    GATEWAY_TO_UNDERDARK(new Worlds(Set.of("bracken:pax"), Set.of("bracken:the_underdark"), false), ClaimArrivalProfile.BODY_AIR),
    SANCTUM_FALL_TO_VOID(new Worlds(Set.of("bracken:sanctum"), Set.of("bracken:void"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    END_FALL_TO_VOID(new Worlds(Set.of("minecraft:the_end"), Set.of("bracken:void"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    PANACEA_FALL_TO_VOID(new Worlds(Set.of("bracken:panacea"), Set.of("bracken:void"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    GLACIUM_FALL_TO_VOID(new Worlds(Set.of("bracken:glacium"), Set.of("bracken:void"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    BRINE_FALL_TO_VOID(new Worlds(Set.of("bracken:the_brine"), Set.of("bracken:void"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    VARSKSPACE_FALL_TO_VOID(new Worlds(Set.of("bracken:varskspace"), Set.of("bracken:void"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    VOID_ROLL_1_TO_END(new Worlds(Set.of("bracken:void"), Set.of("minecraft:the_end"), false), ClaimArrivalProfile.VOID_CUBE),
    VOID_ROLL_3_TO_GLACIUM(new Worlds(Set.of("bracken:void"), Set.of("bracken:glacium"), false), ClaimArrivalProfile.VOID_CUBE),
    VOID_ROLL_4_TO_OMNIDROME(new Worlds(Set.of("bracken:void"), Set.of("bracken:omnidrome"), false), ClaimArrivalProfile.VOID_CUBE),
    VOID_ROLL_5_TO_PANACEA(new Worlds(Set.of("bracken:void"), Set.of("bracken:panacea"), false), ClaimArrivalProfile.VOID_CUBE),
    VOID_ROLL_6_TO_VOID(new Worlds(Set.of("bracken:void"), Set.of("bracken:void"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    VOID_ROLL_7_TO_SANCTUM(new Worlds(Set.of("bracken:void"), Set.of("bracken:sanctum"), false), ClaimArrivalProfile.VOID_CUBE),
    VOID_ROLL_8_TO_BRINE(new Worlds(Set.of("bracken:void"), Set.of("bracken:the_brine"), false), ClaimArrivalProfile.VOID_CUBE),
    VOID_ROLL_9_TO_FAEWILD(new Worlds(Set.of("bracken:void"), Set.of("bracken:the_faewild"), false), ClaimArrivalProfile.VOID_CUBE),
    VOID_ROLL_10_TO_UNDERDARK(new Worlds(Set.of("bracken:void"), Set.of("bracken:the_underdark"), false), ClaimArrivalProfile.VOID_CUBE),
    VOID_ROLL_11_TO_VARSKSPACE(new Worlds(Set.of("bracken:void"), Set.of("bracken:varskspace"), false), ClaimArrivalProfile.VOID_CUBE),
    VOID_ROLL_12_TO_OVERWORLD(new Worlds(Set.of("bracken:void"), Set.of("minecraft:overworld"), false), ClaimArrivalProfile.VOID_CUBE),
    VOID_ROLL_13_TO_NETHER(new Worlds(Set.of("bracken:void"), Set.of("minecraft:the_nether"), false), ClaimArrivalProfile.VOID_CUBE),
    STARTING_DIMENSION_OMNIDROME(new Worlds(Set.of(), Set.of("bracken:omnidrome"), false), ClaimArrivalProfile.BRACKEN_STANDING),
    STARTING_DIMENSION_GLACIUM(new Worlds(Set.of(), Set.of("bracken:glacium"), false), ClaimArrivalProfile.BRACKEN_STANDING),
    STARTING_DIMENSION_FAEWILD(new Worlds(Set.of(), Set.of("bracken:the_faewild"), false), ClaimArrivalProfile.BRACKEN_STANDING),
    STARTING_DIMENSION_PANACEA(new Worlds(Set.of(), Set.of("bracken:panacea"), false), ClaimArrivalProfile.BRACKEN_STANDING),
    STARTING_DIMENSION_UNDERDARK(new Worlds(Set.of(), Set.of("bracken:the_underdark"), false), ClaimArrivalProfile.BRACKEN_STANDING),
    STARTING_DIMENSION_END(new Worlds(Set.of(), Set.of("minecraft:the_end"), false), ClaimArrivalProfile.BRACKEN_STANDING),
    STARTING_DIMENSION_BRINE(new Worlds(Set.of(), Set.of("bracken:the_brine"), false), ClaimArrivalProfile.BRACKEN_FALLING),
    STARTING_DIMENSION_NETHER(new Worlds(Set.of(), Set.of("minecraft:the_nether"), false), ClaimArrivalProfile.BRACKEN_STANDING),
    STARTING_DIMENSION_SANCTUM(new Worlds(Set.of(), Set.of("bracken:sanctum"), false), ClaimArrivalProfile.BRACKEN_STANDING),
    STARTING_DIMENSION_PAX(new Worlds(Set.of(), Set.of("bracken:pax"), false), ClaimArrivalProfile.BRACKEN_STANDING),
    STARTING_DIMENSION_DORMIS(new Worlds(Set.of(), Set.of("bracken:dormis"), false), ClaimArrivalProfile.BRACKEN_STANDING),
    RECALL(new Worlds(Set.of(), Set.of(), false), ClaimArrivalProfile.BRACKEN_STANDING),
    ASTRAL_RETURN(new Worlds(Set.of(), Set.of(), false), ClaimArrivalProfile.ASTRAL_RETURN),
    SCATTERSTORM(new Worlds(Set.of(), Set.of(), true), ClaimArrivalProfile.BRACKEN_STANDING),
    BANISHMENT(new Worlds(Set.of(), Set.of(), true), ClaimArrivalProfile.BRACKEN_STANDING),
    ENDERLING_CONTROLLED(new Worlds(Set.of(), Set.of(), true), ClaimArrivalProfile.BRACKEN_STANDING),
    OMNI_NEAREST_PLAYER(new Worlds(Set.of("bracken:omnidrome"), Set.of("bracken:omnidrome"), false), ClaimArrivalProfile.BRACKEN_SWIMMING),
    OMNIDROME_LOOP_BOTTOM(new Worlds(Set.of("bracken:omnidrome"), Set.of(), true), ClaimArrivalProfile.BRACKEN_FALLING),
    OMNIDROME_LOOP_TOP(new Worlds(Set.of("bracken:omnidrome"), Set.of(), true), ClaimArrivalProfile.BRACKEN_FALLING),
    KRAKEN_SPIN_PUSH(new Worlds(Set.of(), Set.of(), true), ClaimArrivalProfile.BRACKEN_SWIMMING),
    OSTIARIUS_XP_PULL(new Worlds(Set.of(), Set.of(), true), ClaimArrivalProfile.BRACKEN_FALLING),
    ADMIN_DIALOG_TO_DORMIS(new Worlds(Set.of(), Set.of("bracken:dormis"), false), ClaimArrivalProfile.BRACKEN_SWIMMING),
    ADMIN_DIALOG_TO_GLACIUM(new Worlds(Set.of(), Set.of("bracken:glacium"), false), ClaimArrivalProfile.BRACKEN_SWIMMING),
    ADMIN_DIALOG_TO_OMNIDROME(new Worlds(Set.of(), Set.of("bracken:omnidrome"), false), ClaimArrivalProfile.BRACKEN_SWIMMING),
    ADMIN_DIALOG_TO_OVERWORLD(new Worlds(Set.of(), Set.of("minecraft:overworld"), false), ClaimArrivalProfile.BRACKEN_SWIMMING),
    ADMIN_DIALOG_TO_PANACEA(new Worlds(Set.of(), Set.of("bracken:panacea"), false), ClaimArrivalProfile.BRACKEN_SWIMMING),
    ADMIN_DIALOG_TO_PAX(new Worlds(Set.of(), Set.of("bracken:pax"), false), ClaimArrivalProfile.BRACKEN_SWIMMING),
    ADMIN_DIALOG_TO_SANCTUM(new Worlds(Set.of(), Set.of("bracken:sanctum"), false), ClaimArrivalProfile.BRACKEN_SWIMMING),
    ADMIN_DIALOG_TO_BRINE(new Worlds(Set.of(), Set.of("bracken:the_brine"), false), ClaimArrivalProfile.BRACKEN_SWIMMING),
    ADMIN_DIALOG_TO_END(new Worlds(Set.of(), Set.of("minecraft:the_end"), false), ClaimArrivalProfile.BRACKEN_SWIMMING),
    ADMIN_DIALOG_TO_FAEWILD(new Worlds(Set.of(), Set.of("bracken:the_faewild"), false), ClaimArrivalProfile.BRACKEN_SWIMMING),
    ADMIN_DIALOG_TO_NETHER(new Worlds(Set.of(), Set.of("minecraft:the_nether"), false), ClaimArrivalProfile.BRACKEN_SWIMMING),
    ADMIN_DIALOG_TO_UNDERDARK(new Worlds(Set.of(), Set.of("bracken:the_underdark"), false), ClaimArrivalProfile.BRACKEN_SWIMMING),
    ADMIN_DIALOG_TO_VARSKSPACE(new Worlds(Set.of(), Set.of("bracken:varskspace"), false), ClaimArrivalProfile.BRACKEN_SWIMMING),
    ADMIN_DIALOG_TO_VOID(new Worlds(Set.of(), Set.of("bracken:void"), false), ClaimArrivalProfile.BRACKEN_SWIMMING);

    private static final Map<String, BrackenTravelRoute> ROUTES = routes();

    private final Worlds worlds;
    private final ClaimArrivalProfile profile;
    private final String id;

    BrackenTravelRoute(Worlds worlds, ClaimArrivalProfile profile) {
        this.worlds = worlds;
        this.profile = profile;
        this.id = name().toLowerCase(Locale.ROOT);
    }

    static Optional<BrackenTravelRoute> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(ROUTES.get(id));
    }

    ClaimArrivalProfile profile() {
        return profile;
    }

    boolean accepts(Player player, Location destination) {
        return destination != null && destination.getWorld() != null
                && Double.isFinite(destination.getX()) && Double.isFinite(destination.getY())
                && Double.isFinite(destination.getZ()) && Float.isFinite(destination.getYaw())
                && Float.isFinite(destination.getPitch()) && worlds.accepts(player.getWorld(), destination.getWorld());
    }

    private static Map<String, BrackenTravelRoute> routes() {
        Map<String, BrackenTravelRoute> routes = new HashMap<>();
        for (BrackenTravelRoute route : values()) {
            routes.put(route.id, route);
        }
        return Map.copyOf(routes);
    }

    private record Worlds(Set<String> sources, Set<String> destinations, boolean sameWorld) {
        private boolean accepts(World source, World destination) {
            return source != null && (sources.isEmpty() || sources.contains(source.getKey().toString()))
                    && (sameWorld ? source.equals(destination) : destinations.isEmpty()
                    || destinations.contains(destination.getKey().toString()));
        }
    }
}
