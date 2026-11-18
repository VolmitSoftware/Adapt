package art.arcane.adapt.bukkit.feature.display;

import art.arcane.adapt.bukkit.platform.BukkitDisplayFactory;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.display.BlockDisplayRef;
import art.arcane.adapt.platform.entity.display.DisplayRef;
import art.arcane.adapt.platform.entity.display.ItemDisplayRef;
import art.arcane.adapt.platform.entity.display.TextDisplayRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.bukkit.platform.BukkitText;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * Display entities spawned the way the plugin spawns them ({@code PaperCompat.spawnHydrated}): the prepare
 * callback runs on the entity before it enters the world, so hidden-by-default displays never flash.
 */
public final class BukkitDisplays implements BukkitDisplayFactory {
  @Override
  public DisplayRef wrap(Display display) {
    if (display instanceof ItemDisplay item) {
      return new BukkitItemDisplayRef(item);
    }
    if (display instanceof BlockDisplay block) {
      return new BukkitBlockDisplayRef(block);
    }
    if (display instanceof TextDisplay text) {
      return new BukkitTextDisplayRef(text);
    }
    return new BukkitDisplayRef(display);
  }

  @Override
  public ItemDisplayRef spawnItem(Loc location, ItemRef item, Consumer<ItemDisplayRef> prepare) {
    ItemStack stack = item == null ? null : BukkitRefs.item(item);
    Location at = BukkitRefs.location(location);
    ItemDisplay spawned = PaperCompat.spawnHydrated(at.getWorld(), at, ItemDisplay.class, (ItemDisplay display) -> {
      display.setItemStack(stack == null ? null : stack.clone());
      if (prepare != null) {
        prepare.accept(new BukkitItemDisplayRef(display));
      }
    });
    return (ItemDisplayRef) registered(spawned);
  }

  @Override
  public BlockDisplayRef spawnBlock(Loc location, BlockStateRef block, Consumer<BlockDisplayRef> prepare) {
    Location at = BukkitRefs.location(location);
    BlockDisplay spawned = PaperCompat.spawnHydrated(at.getWorld(), at, BlockDisplay.class, (BlockDisplay display) -> {
      if (block != null) {
        display.setBlock(BukkitRefs.blockData(block));
      }
      if (prepare != null) {
        prepare.accept(new BukkitBlockDisplayRef(display));
      }
    });
    return (BlockDisplayRef) registered(spawned);
  }

  @Override
  public TextDisplayRef spawnText(Loc location, String markup, Consumer<TextDisplayRef> prepare) {
    Location at = BukkitRefs.location(location);
    TextDisplay spawned = PaperCompat.spawnHydrated(at.getWorld(), at, TextDisplay.class, (TextDisplay display) -> {
      PaperCompat.text(display, BukkitText.component(markup));
      if (prepare != null) {
        prepare.accept(new BukkitTextDisplayRef(display));
      }
    });
    return (TextDisplayRef) registered(spawned);
  }

  private DisplayRef registered(Display spawned) {
    Entity entity = spawned;
    if (BukkitRefs.entity(entity) instanceof DisplayRef ref) {
      return ref;
    }
    return wrap(spawned);
  }
}
