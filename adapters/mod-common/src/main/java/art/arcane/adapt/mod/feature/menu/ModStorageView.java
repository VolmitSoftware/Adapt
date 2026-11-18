package art.arcane.adapt.mod.feature.menu;

import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.entity.ModInventoryRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.StorageView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.MenuType;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
  * A vanilla chest or dispenser window over Adapt's own container. The window itself stays a vanilla menu so clicks and
  * drags are posted like any container's; the container reports the close when the menu releases it.
  */
final class ModStorageView implements StorageView {
    private final PlayerRef viewer;
    private final StorageContainer container;
    private final Consumer<StorageView> onClose;
    private final AtomicBoolean closed = new AtomicBoolean();
    private AbstractContainerMenu menu;

    ModStorageView(PlayerRef viewer, int size, Consumer<StorageView> onClose) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.container = new StorageContainer(size);
        this.onClose = onClose;
    }

    boolean open(ServerPlayer player, String title) {
        int rows = container.getContainerSize() / 9;
        player.openMenu(new SimpleMenuProvider((int id, Inventory inventory, Player who) -> {
            AbstractContainerMenu created = rows == 1
                    ? new DispenserMenu(id, inventory, container)
                    : new ChestMenu(menuType(rows), id, inventory, container, rows);
            menu = created;
            return created;
        }, MenuIcons.plain(title)));
        return menu != null && player.containerMenu == menu;
    }

    private static MenuType<ChestMenu> menuType(int rows) {
        return switch (rows) {
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    @Override
    public PlayerRef viewer() {
        return viewer;
    }

    @Override
    public InventoryRef inventory() {
        return new ModInventoryRef(() -> container);
    }

    @Override
    public int size() {
        return container.getContainerSize();
    }

    @Override
    public boolean isOpen() {
        ServerPlayer player = ModRefs.player(viewer);
        return player != null && menu != null && player.containerMenu == menu;
    }

    @Override
    public void close() {
        ServerPlayer player = ModRefs.player(viewer);
        if (isOpen()) {
            player.closeContainer();
        }
    }

    private final class StorageContainer extends SimpleContainer {
        private StorageContainer(int size) {
            super(size == 9 ? 9 : Math.max(18, Math.min(54, size)));
        }

        @Override
        public void stopOpen(ContainerUser user) {
            super.stopOpen(user);
            if (closed.compareAndSet(false, true) && onClose != null) {
                onClose.accept(ModStorageView.this);
            }
        }
    }
}
