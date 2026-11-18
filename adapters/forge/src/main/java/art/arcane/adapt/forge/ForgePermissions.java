package art.arcane.adapt.forge;

import art.arcane.adapt.mod.AdaptModIdentity;
import art.arcane.adapt.mod.platform.ModPermissionHost;
import art.arcane.adapt.mod.platform.PermissionBridge;
import art.arcane.adapt.mod.platform.PermissionNodeCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.handler.DefaultPermissionHandler;
import net.minecraftforge.server.permission.nodes.PermissionDynamicContext;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapt nodes in the Forge PermissionAPI, gathered from {@link PermissionNodeCache}. The node defaults resolve
 * through {@link ModPermissionHost#fallback}, and the default handler is skipped, so without a permission mod the
 * answer is the same as on Fabric.
 */
final class ForgePermissions implements PermissionBridge {
    private final Path configDirectory;
    private final Map<String, PermissionNode<Boolean>> nodes = new ConcurrentHashMap<>();

    ForgePermissions(Path configDirectory) {
        this.configDirectory = configDirectory;
    }

    void gather(PermissionGatherEvent.Nodes event) {
        nodes.clear();
        List<PermissionNode<?>> created = new ArrayList<>();
        for (String name : PermissionNodeCache.nodes(configDirectory)) {
            PermissionNode<Boolean> node = new PermissionNode<>(AdaptModIdentity.ID, PermissionNodeCache.path(name), PermissionTypes.BOOLEAN,
                    (ServerPlayer player, UUID id, PermissionDynamicContext<?>... context) -> player != null && ModPermissionHost.fallback(player, name));
            nodes.put(name, node);
            created.add(node);
        }
        event.addNodes(created);
        ModPermissionHost.bridge(this);
    }

    void remember() {
        PermissionNodeCache.remember(configDirectory);
    }

    @Override
    public Boolean check(ServerPlayer player, String node) {
        PermissionNode<Boolean> registered = nodes.get(node);
        if (registered == null || DefaultPermissionHandler.IDENTIFIER.equals(PermissionAPI.getActivePermissionHandler())) {
            return null;
        }
        return PermissionAPI.getPermission(player, registered);
    }
}
