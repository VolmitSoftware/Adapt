package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.platform.entity.display.BlockDisplayRef;
import art.arcane.adapt.platform.entity.display.DisplayRef;
import art.arcane.adapt.platform.entity.display.ItemDisplayRef;
import art.arcane.adapt.platform.entity.display.TextDisplayRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.bukkit.entity.Display;

import java.util.function.Consumer;

public interface BukkitDisplayFactory {
  DisplayRef wrap(Display display);

  ItemDisplayRef spawnItem(Loc location, ItemRef item, Consumer<ItemDisplayRef> prepare);

  BlockDisplayRef spawnBlock(Loc location, BlockStateRef block, Consumer<BlockDisplayRef> prepare);

  TextDisplayRef spawnText(Loc location, String markup, Consumer<TextDisplayRef> prepare);
}
