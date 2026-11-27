package art.arcane.adapt.mod.platform;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.permission.PermissionDefault;
import art.arcane.adapt.api.internal.HostPermissions;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Permissions in two steps: a loader {@link PermissionBridge} when one is installed and answers, then the registered
 * default, with operators being players at gamemaster level (2). These versions have no vanilla permission atoms.
 * Unregistered nodes default to operators, as on Bukkit.
 */
public final class ModPermissionHost implements HostPermissions {
    private static final int GAMEMASTER_LEVEL = 2;
    private static final Map<String, PermissionDefault> DEFAULTS = new ConcurrentHashMap<>();
    private static volatile PermissionBridge bridge;

    public ModPermissionHost() {
        DEFAULTS.clear();
    }

    public static void bridge(PermissionBridge installed) {
        bridge = installed;
    }

    /** Every node registered with a default since the host was created. */
    public static Set<String> registeredNodes() {
        return Set.copyOf(DEFAULTS.keySet());
    }

    @Override
    public boolean has(PlayerRef player, String node) {
        if (node == null || node.isEmpty()) {
            return true;
        }
        ServerPlayer handle = ModRefs.player(player);
        PermissionBridge active = bridge;
        if (active != null) {
            Boolean answer = active.check(handle, node);
            if (answer != null) {
                return answer;
            }
        }
        return fallback(handle, node);
    }

    /**
     * Set means a loader permission system answered (Forge and NeoForge answer whenever a permission mod is active,
     * fabric-permissions-api only for TRUE or FALSE).
     */
    @Override
    public boolean isSet(PlayerRef player, String node) {
        if (node == null || node.isEmpty()) {
            return false;
        }
        PermissionBridge active = bridge;
        return active != null && active.check(ModRefs.player(player), node) != null;
    }

    /** The answer without a bridge: the registered default. */
    public static boolean fallback(ServerPlayer handle, String node) {
        boolean operator = handle.hasPermissions(GAMEMASTER_LEVEL);
        return switch (DEFAULTS.getOrDefault(node, PermissionDefault.OP)) {
            case TRUE -> true;
            case FALSE -> false;
            case OP -> operator;
            case NOT_OP -> !operator;
        };
    }

    @Override
    public boolean add(String node, String description, PermissionDefault fallback) {
        return node != null && fallback != null && DEFAULTS.putIfAbsent(node, fallback) == null;
    }

    @Override
    public void unregister(String node) {
        if (node != null) {
            DEFAULTS.remove(node);
        }
    }
}
