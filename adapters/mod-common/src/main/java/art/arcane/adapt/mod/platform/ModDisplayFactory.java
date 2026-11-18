package art.arcane.adapt.mod.platform;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.display.BlockDisplayRef;
import art.arcane.adapt.platform.entity.display.DisplayRef;
import art.arcane.adapt.platform.entity.display.ItemDisplayRef;
import art.arcane.adapt.platform.entity.display.TextDisplayRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockStateRef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;

import java.util.function.Consumer;

public interface ModDisplayFactory {
    DisplayRef wrap(Display display);

    ItemDisplayRef spawnItem(ServerLevel level, Loc location, ItemRef item, Consumer<ItemDisplayRef> prepare);

    BlockDisplayRef spawnBlock(ServerLevel level, Loc location, BlockStateRef block, Consumer<BlockDisplayRef> prepare);

    TextDisplayRef spawnText(ServerLevel level, Loc location, String markup, Consumer<TextDisplayRef> prepare);

    EntityRef spawnInteraction(ServerLevel level, Loc location, float width, float height, boolean visibleByDefault,
                               Consumer<EntityRef> prepare);
}
