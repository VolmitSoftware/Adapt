package art.arcane.adapt.mod.platform.entity;

import art.arcane.adapt.platform.entity.CraftingStation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Utility block windows opened without the block, like Paper's {@code openWorkbench(null, true)}: the menu works at the
 * player's position and never closes for a missing block or distance.
 */
final class ModStations {
    private ModStations() {
    }

    static void open(ServerPlayer player, CraftingStation station) {
        ContainerLevelAccess access = new Portable(player.level(), player.blockPosition());
        Component title = Component.translatable(switch (station) {
            case WORKBENCH -> "container.crafting";
            case GRINDSTONE -> "container.grindstone_title";
            case ANVIL -> "container.repair";
            case STONECUTTER -> "container.stonecutter";
            case CARTOGRAPHY -> "container.cartography_table";
            case LOOM -> "container.loom";
        });
        player.openMenu(new SimpleMenuProvider((int id, Inventory inventory, Player who) -> menu(station, id, inventory, access), title));
    }

    private static AbstractContainerMenu menu(CraftingStation station, int id, Inventory inventory, ContainerLevelAccess access) {
        return switch (station) {
            case WORKBENCH -> new CraftingMenu(id, inventory, access);
            case GRINDSTONE -> new GrindstoneMenu(id, inventory, access);
            case ANVIL -> new AnvilMenu(id, inventory, access);
            case STONECUTTER -> new StonecutterMenu(id, inventory, access);
            case CARTOGRAPHY -> new CartographyTableMenu(id, inventory, access);
            case LOOM -> new LoomMenu(id, inventory, access);
        };
    }

    /** Runs the menu's world work at the player's position but answers every validity check with its default. */
    private record Portable(Level level, BlockPos pos) implements ContainerLevelAccess {
        @Override
        public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> action) {
            return Optional.ofNullable(action.apply(level, pos));
        }

        @Override
        public <T> T evaluate(BiFunction<Level, BlockPos, T> action, T fallback) {
            return fallback;
        }
    }
}
