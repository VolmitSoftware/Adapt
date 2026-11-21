package art.arcane.adapt.client.items;

import art.arcane.adapt.client.protocol.ItemDescriptor;
import com.mojang.serialization.DataResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/** The client-only half of the index: the client thread, the game directory and the connection's registries. */
final class ClientItems {
    private static final Logger LOGGER = LoggerFactory.getLogger("Adapt");

    private ClientItems() {
    }

    static void execute(Runnable task) {
        Minecraft.getInstance().execute(task);
    }

    static Path basesFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("adapt").resolve("viewer-items.txt");
    }

    /**
     * The stack in {@code descriptor}'s components (gzip NBT in the codec form of this Minecraft version). A stack this
     * client cannot read becomes its base item named like the server's item, which stays searchable.
     */
    static ItemStack decode(ItemDescriptor descriptor) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            try {
                CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(descriptor.components()), NbtAccounter.unlimitedHeap());
                DataResult<ItemStack> parsed = ItemStack.CODEC.parse(connection.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag);
                Optional<ItemStack> stack = parsed.result();
                if (stack.isPresent() && !stack.get().isEmpty()) {
                    return stack.get();
                }
                LOGGER.warn("Adapt item {} could not be rebuilt: {}", descriptor.id(), parsed.error().map(Object::toString).orElse("empty stack"));
            } catch (IOException unreadable) {
                LOGGER.warn("Adapt item {} is not readable NBT: {}", descriptor.id(), unreadable.getMessage());
            }
        }
        return named(descriptor);
    }

    private static ItemStack named(ItemDescriptor descriptor) {
        Optional<Item> base = ItemStacks.item(descriptor.baseItemId());
        if (base.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(base.get());
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(descriptor.name()));
        return stack;
    }
}
