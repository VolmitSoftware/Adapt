package art.arcane.adapt.mod.platform.entity;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Containers a player opened remotely through Adapt. While that menu stays open its block entities skip the vanilla
 * reach check, like Bukkit's openInventory; the block entity must still be in place.
 */
public final class ModRemoteContainers {
    private static final Map<Player, Session> SESSIONS = Collections.synchronizedMap(new WeakHashMap<>());

    private ModRemoteContainers() {
    }

    static void open(Player player, AbstractContainerMenu menu, Set<BlockEntity> containers) {
        SESSIONS.put(player, new Session(menu, Set.copyOf(containers)));
    }

    public static boolean reachable(Player player, BlockEntity container) {
        Session session = SESSIONS.get(player);
        if (session == null) {
            return false;
        }
        if (session.menu() != player.containerMenu) {
            SESSIONS.remove(player, session);
            return false;
        }
        return session.containers().contains(container)
            && !container.isRemoved()
            && container.getLevel() != null
            && container.getLevel().getBlockEntity(container.getBlockPos()) == container;
    }

    private record Session(AbstractContainerMenu menu, Set<BlockEntity> containers) {
    }
}
