package art.arcane.adapt.fabric;

import art.arcane.adapt.mod.platform.PermissionBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Adapt nodes through fabric-permissions-api ({@code me.lucko.fabric.api.permissions.v0.Permissions}), looked up
 * reflectively so the mod loads without it. {@code DEFAULT} leaves the decision to the registered defaults.
 */
final class FabricPermissions implements PermissionBridge {
    static final String MOD_ID = "fabric-permissions-api-v0";
    private static final String API = "me.lucko.fabric.api.permissions.v0.Permissions";
    private static final String TRI_STATE = "net.fabricmc.fabric.api.util.TriState";

    private final MethodHandle permissionValue;

    private FabricPermissions(MethodHandle permissionValue) {
        this.permissionValue = permissionValue;
    }

    /** The bridge, or null when the API is absent or its signature changed. */
    static FabricPermissions create() {
        try {
            Class<?> api = Class.forName(API);
            Class<?> triState = Class.forName(TRI_STATE);
            MethodHandle handle = MethodHandles.publicLookup().findStatic(api, "getPermissionValue",
                    MethodType.methodType(triState, Entity.class, String.class));
            return new FabricPermissions(handle.asType(MethodType.methodType(Object.class, Entity.class, String.class)));
        } catch (ReflectiveOperationException | LinkageError absent) {
            return null;
        }
    }

    @Override
    public Boolean check(ServerPlayer player, String node) {
        Object state;
        try {
            state = (Object) permissionValue.invokeExact((Entity) player, node);
        } catch (Throwable failure) {
            return null;
        }
        if (!(state instanceof Enum<?> value)) {
            return null;
        }
        return switch (value.name()) {
            case "TRUE" -> Boolean.TRUE;
            case "FALSE" -> Boolean.FALSE;
            default -> null;
        };
    }
}
