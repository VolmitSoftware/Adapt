package art.arcane.adapt.gameplay;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationTeleportPort;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.rift.RiftBlink;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class BlinkPreferencesFixtures {
    private static final Map<UUID, JsonObject> attacks = new HashMap<>();
    private static final Map<UUID, Integer> teleports = new HashMap<>();
    private static final Map<UUID, JsonObject> destinations = new HashMap<>();
    private static final Set<UUID> denied = new HashSet<>();

    private BlinkPreferencesFixtures() {
    }

    public static void install() {
        Fixtures.register(new BlinkPreferencesFixtures());
    }

    public static JsonObject execute(PlayerRef player, String[] args) {
        RiftBlink blink = Fixtures.adaptation(RiftBlink.class);
        AdaptPlayer runtime = Objects.requireNonNull(Fixtures.adaptPlayer(player));
        if (!runtime.isRuntimeReady()) throw new IllegalStateException("Player runtime is not ready");
        boolean changed = false;
        switch (args[2]) {
            case "snapshot" -> { }
            case "open" -> blink.openGui(player);
            case "level" -> runtime.getData().getSkillLine("rift").setAdaptation(blink, Integer.parseInt(args[3]));
            case "enabled" -> changed = PlayerPreferences.set(blink, runtime, CommonPreferences.ENABLED, CommonPreferences.Toggle.valueOf(args[3]));
            case "phasing" -> changed = PlayerPreferences.set(blink, runtime, RiftBlink.PHASING, RiftBlink.Phasing.valueOf(args[3]));
            case "targeting" -> changed = PlayerPreferences.set(blink, runtime, RiftBlink.TARGETING, RiftBlink.Targeting.valueOf(args[3]));
            case "activation" -> changed = PlayerPreferences.set(blink, runtime, RiftBlink.ACTIVATION, RiftBlink.Activation.valueOf(args[3]));
            case "direction" -> changed = PlayerPreferences.set(blink, runtime, RiftBlink.DIRECTION, RiftBlink.Direction.valueOf(args[3]));
            case "direction-lock" -> PlayerPreferences.policy(blink, RiftBlink.DIRECTION).playerEditable = !Boolean.parseBoolean(args[3]);
            case "reactive-direction" -> changed = PlayerPreferences.set(blink, runtime, RiftBlink.REACTIVE_DIRECTION, RiftBlink.ReactiveDirection.valueOf(args[3]));
            case "lock" -> PlayerPreferences.policy(blink, RiftBlink.ACTIVATION).playerEditable = !Boolean.parseBoolean(args[3]);
            case "deny" -> {
                if (Boolean.parseBoolean(args[3])) {
                    DenyingTeleports.install();
                    denied.add(player.uuid());
                } else {
                    denied.remove(player.uuid());
                }
            }
            case "position" -> {
                player.closeInventory();
                player.setHealth(20);
                player.setNoDamageTicks(0);
                player.setFallDistance(0);
                player.setVelocity(Vec3.ZERO);
                Fixtures.teleport(player, Fixtures.loc(player.world(), Double.parseDouble(args[3]), 100, 0.5, -90, 0));
            }
            case "weapons" -> {
                Fixtures.give(player, "wooden_sword", 1);
                Fixtures.give(player, "bow", 1);
                Fixtures.give(player, "arrow", 16);
            }
            case "terrain" -> {
                for (int x = -30; x <= 40; x++) {
                    for (int z = -4; z <= 4; z++) Fixtures.set(player.world(), x, 99, z, "stone");
                }
                for (int y = 100; y < 105; y++) {
                    for (int z = -1; z <= 1; z++) Fixtures.set(player.world(), 8, y, z, "stone");
                }
            }
            default -> throw new IllegalArgumentException("Unknown Blink fixture action " + args[2]);
        }
        JsonObject result = snapshot(blink, player, runtime);
        result.addProperty("changed", changed);
        return result;
    }

    private static JsonObject snapshot(RiftBlink blink, PlayerRef player, AdaptPlayer runtime) {
        int level = blink.getLevel(player);
        Loc location = player.location();
        JsonObject result = new JsonObject();
        result.addProperty("player", player.name());
        result.addProperty("level", level);
        result.addProperty("enabled", PlayerPreferences.resolve(blink, runtime.getData(), level, CommonPreferences.ENABLED).name());
        result.addProperty("phasing", PlayerPreferences.resolve(blink, runtime.getData(), level, RiftBlink.PHASING).name());
        result.addProperty("targeting", PlayerPreferences.resolve(blink, runtime.getData(), level, RiftBlink.TARGETING).name());
        result.addProperty("activation", PlayerPreferences.resolve(blink, runtime.getData(), level, RiftBlink.ACTIVATION).name());
        result.addProperty("direction", PlayerPreferences.resolve(blink, runtime.getData(), level, RiftBlink.DIRECTION).name());
        result.addProperty("savedDirection", runtime.getData().getPreferences().get("rift-blink", "direction"));
        result.addProperty("reactive-direction", PlayerPreferences.resolve(blink, runtime.getData(), level, RiftBlink.REACTIVE_DIRECTION).name());
        result.addProperty("health", player.health());
        result.addProperty("x", location.x());
        result.addProperty("y", location.y());
        result.addProperty("z", location.z());
        result.addProperty("processId", ProcessHandle.current().pid());
        result.addProperty("teleports", teleports.getOrDefault(player.uuid(), 0));
        result.addProperty("savedActivation", runtime.getData().getPreferences().get("rift-blink", "activation"));
        result.add("attack", attacks.get(player.uuid()));
        result.add("destination", destinations.get(player.uuid()));
        result.add("slots", FixtureItems.menuSlots(player));
        return result;
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void observeTeleport(PlayerTeleportEvent event) {
        PlayerRef player = event.player();
        if (event.cause() == TeleportCause.PLUGIN && Fixtures.owns(player)) {
            teleports.merge(player.uuid(), 1, Integer::sum);
            JsonObject destination = new JsonObject();
            destination.addProperty("x", event.to().x());
            destination.addProperty("y", event.to().y());
            destination.addProperty("z", event.to().z());
            destination.addProperty("dx", event.to().x() - event.from().x());
            destination.addProperty("dy", event.to().y() - event.from().y());
            destination.addProperty("dz", event.to().z() - event.from().z());
            destinations.put(player.uuid(), destination);
        }
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void observeAttack(EntityDamageByEntityEvent event) {
        if (!(event.entity() instanceof PlayerRef player) || !Fixtures.owns(player)) return;
        JsonObject hit = new JsonObject();
        hit.addProperty("cancelled", event.isCancelled());
        hit.addProperty("cause", event.cause().name());
        hit.addProperty("damage", event.finalDamage());
        hit.addProperty("tick", player.ticksLived());
        attacks.put(player.uuid(), hit);
    }

    private static final class DenyingTeleports implements AdaptationTeleportPort {
        private final AdaptationTeleportPort delegate;

        private DenyingTeleports(AdaptationTeleportPort delegate) {
            this.delegate = delegate;
        }

        private static void install() {
            AdaptationTeleportPort current = AdaptPorts.get(AdaptationTeleportPort.class, AdaptationTeleportPort.NONE);
            if (!(current instanceof DenyingTeleports)) {
                AdaptPorts.install(AdaptationTeleportPort.class, new DenyingTeleports(current));
            }
        }

        @Override
        public Loc announce(PlayerRef player, Adaptation<?> adaptation, Loc from, Loc to) {
            if (adaptation instanceof RiftBlink && denied.contains(player.uuid())) {
                return null;
            }
            return delegate.announce(player, adaptation, from, to);
        }
    }
}
