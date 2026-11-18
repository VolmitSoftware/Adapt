package art.arcane.adapt.mod.feature.menu;

import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHost;
import art.arcane.adapt.platform.menu.MenuSession;
import art.arcane.adapt.platform.menu.StorageView;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Adapt chest menus as vanilla chest windows that refuse every click (see {@link AdaptChestMenu}). */
public final class ModMenuHost implements MenuHost {
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, StorageView> storages = new ConcurrentHashMap<>();

    @Override
    public MenuSession open(PlayerRef viewer, ChestMenu menu) {
        ServerPlayer player = ModRefs.player(viewer);
        Session existing = sessions.get(player.getUUID());
        if (existing != null && existing.isOpen() && existing.window.windowSize() == menu.size() && existing.menu.title().equals(menu.title())) {
            existing.update(menu);
            return existing;
        }
        Session session = new Session(viewer, player.getUUID(), menu);
        session.openWindow(player);
        return session;
    }

    @Override
    public MenuSession current(PlayerRef viewer) {
        Session session = sessions.get(viewer.uuid());
        return session != null && session.isOpen() ? session : null;
    }

    @Override
    public StorageView openStorage(PlayerRef viewer, String title, int size, Consumer<StorageView> onClose) {
        ServerPlayer player = ModRefs.player(viewer);
        ModStorageView view = new ModStorageView(viewer, size, (StorageView closed) -> {
            storages.remove(viewer.uuid(), closed);
            if (onClose != null) {
                onClose.accept(closed);
            }
        });
        if (!view.open(player, title)) {
            return null;
        }
        storages.put(viewer.uuid(), view);
        return view;
    }

    @Override
    public StorageView currentStorage(PlayerRef viewer) {
        StorageView view = storages.get(viewer.uuid());
        return view != null && view.isOpen() ? view : null;
    }

    @Override
    public void closeAll() {
        for (Session session : new ArrayList<>(sessions.values())) {
            session.close();
        }
        sessions.clear();
    }

    private final class Session implements MenuSession {
        private final PlayerRef viewer;
        private final UUID owner;
        private ChestMenu menu;
        private AdaptChestMenu window;
        private ServerPlayer player;

        private Session(PlayerRef viewer, UUID owner, ChestMenu menu) {
            this.viewer = viewer;
            this.owner = owner;
            this.menu = menu;
        }

        private void openWindow(ServerPlayer target) {
            Session previous = sessions.get(owner);
            if (previous != null && previous.window != null) {
                previous.window.markReplaced();
            }
            player = target;
            Component title = MenuIcons.plain(menu.title());
            target.openMenu(new SimpleMenuProvider((int id, Inventory inventory, Player who) -> {
                AdaptChestMenu created = AdaptChestMenu.create(id, inventory, menu.rows(), this::clicked, this::closed);
                window = created;
                fill();
                return created;
            }, title));
            if (window != null && target.containerMenu == window) {
                sessions.put(owner, this);
            }
        }

        private void fill() {
            window.setFreePlayerInventory(menu.freePlayerInventory());
            for (int slot = 0; slot < window.windowSize(); slot++) {
                MenuButton button = menu.button(slot);
                window.show(slot, button == null ? ItemStack.EMPTY : MenuIcons.stack(button.icon()));
            }
        }

        private void clicked(AdaptChestMenu clicked, int slot, ClickKind kind) {
            if (clicked != window) {
                return;
            }
            MenuButton button = menu.button(slot);
            if (button == null || !button.clickable()) {
                return;
            }
            button.action().click(new MenuClick(viewer, this, slot, kind));
        }

        private void closed(AdaptChestMenu closed) {
            if (closed != window) {
                return;
            }
            sessions.remove(owner, this);
            Consumer<MenuSession> callback = menu.onClose();
            if (callback != null) {
                callback.accept(this);
            }
        }

        @Override
        public PlayerRef viewer() {
            return viewer;
        }

        @Override
        public ChestMenu menu() {
            return menu;
        }

        @Override
        public boolean isOpen() {
            return window != null && player != null && !player.hasDisconnected() && player.containerMenu == window;
        }

        @Override
        public void update(ChestMenu next) {
            boolean reopen = window == null || next.size() != window.windowSize() || !next.title().equals(menu.title());
            menu = next;
            if (!isOpen()) {
                return;
            }
            if (reopen) {
                openWindow(player);
                return;
            }
            fill();
            window.broadcastChanges();
        }

        @Override
        public void close() {
            if (isOpen()) {
                player.closeContainer();
            }
        }
    }
}
