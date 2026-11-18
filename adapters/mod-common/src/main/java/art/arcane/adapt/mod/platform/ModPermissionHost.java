package art.arcane.adapt.mod.platform;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.permission.PermissionDefault;
import art.arcane.adapt.platform.permission.PermissionHost;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Permissions in three steps: a loader {@link PermissionBridge} when one is installed and answers, then vanilla
 * permission atoms, which permission mods grant (node {@code adapt.skill.x} is atom {@code adapt:skill.x}), then the
 * registered default, with operators being players at gamemaster level. Unregistered nodes default to operators, as
 * on Bukkit.
 */
public final class ModPermissionHost implements PermissionHost {
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
     * fabric-permissions-api only for TRUE or FALSE) or a vanilla permission atom is granted.
     */
    @Override
    public boolean isSet(PlayerRef player, String node) {
        if (node == null || node.isEmpty()) {
            return false;
        }
        ServerPlayer handle = ModRefs.player(player);
        PermissionBridge active = bridge;
        if (active != null && active.check(handle, node) != null) {
            return true;
        }
        Identifier atom = atom(node);
        return atom != null && handle.permissions().hasPermission(Permission.Atom.create(atom));
    }

    /** The answer without a bridge: vanilla permission atoms, then the registered default. */
    public static boolean fallback(ServerPlayer handle, String node) {
        PermissionSet permissions = handle.permissions();
        Identifier atom = atom(node);
        if (atom != null && permissions.hasPermission(Permission.Atom.create(atom))) {
            return true;
        }
        boolean operator = permissions.hasPermission(Permissions.COMMANDS_GAMEMASTER);
        return switch (DEFAULTS.getOrDefault(node, PermissionDefault.OP)) {
            case TRUE -> true;
            case FALSE -> false;
            case OP -> operator;
            case NOT_OP -> !operator;
        };
    }

    @Override
    public void register(String node, String description, PermissionDefault fallback) {
        if (node != null && fallback != null) {
            DEFAULTS.put(node, fallback);
        }
    }

    /** Atom identifier for a node: the first dot becomes the namespace separator. */
    public static Identifier atom(String node) {
        int dot = node.indexOf('.');
        if (dot <= 0 || dot == node.length() - 1) {
            return null;
        }
        return Identifier.tryBuild(node.substring(0, dot).toLowerCase(Locale.ROOT), node.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
