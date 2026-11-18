package art.arcane.adapt.bukkit.feature.display;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.display.ItemDisplayMode;
import art.arcane.adapt.platform.entity.display.ItemDisplayRef;
import art.arcane.adapt.platform.item.ItemRef;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

final class BukkitItemDisplayRef extends BukkitDisplayRef implements ItemDisplayRef {
  BukkitItemDisplayRef(ItemDisplay display) {
    super(display);
  }

  private ItemDisplay itemDisplay() {
    return (ItemDisplay) handle();
  }

  @Override
  public ItemRef item() {
    ItemStack stack = itemDisplay().getItemStack();
    return BukkitRefs.item(stack == null ? ItemStack.empty() : stack);
  }

  @Override
  public void setItem(ItemRef item) {
    ItemStack stack = item == null ? null : BukkitRefs.item(item);
    itemDisplay().setItemStack(stack == null ? null : stack.clone());
  }

  @Override
  public ItemDisplayMode displayMode() {
    return switch (itemDisplay().getItemDisplayTransform()) {
      case NONE -> ItemDisplayMode.NONE;
      case THIRDPERSON_LEFTHAND -> ItemDisplayMode.THIRD_PERSON_LEFT_HAND;
      case THIRDPERSON_RIGHTHAND -> ItemDisplayMode.THIRD_PERSON_RIGHT_HAND;
      case FIRSTPERSON_LEFTHAND -> ItemDisplayMode.FIRST_PERSON_LEFT_HAND;
      case FIRSTPERSON_RIGHTHAND -> ItemDisplayMode.FIRST_PERSON_RIGHT_HAND;
      case HEAD -> ItemDisplayMode.HEAD;
      case GUI -> ItemDisplayMode.GUI;
      case GROUND -> ItemDisplayMode.GROUND;
      case FIXED -> ItemDisplayMode.FIXED;
    };
  }

  @Override
  public void setDisplayMode(ItemDisplayMode mode) {
    itemDisplay().setItemDisplayTransform(switch (mode) {
      case NONE -> ItemDisplay.ItemDisplayTransform.NONE;
      case THIRD_PERSON_LEFT_HAND -> ItemDisplay.ItemDisplayTransform.THIRDPERSON_LEFTHAND;
      case THIRD_PERSON_RIGHT_HAND -> ItemDisplay.ItemDisplayTransform.THIRDPERSON_RIGHTHAND;
      case FIRST_PERSON_LEFT_HAND -> ItemDisplay.ItemDisplayTransform.FIRSTPERSON_LEFTHAND;
      case FIRST_PERSON_RIGHT_HAND -> ItemDisplay.ItemDisplayTransform.FIRSTPERSON_RIGHTHAND;
      case HEAD -> ItemDisplay.ItemDisplayTransform.HEAD;
      case GUI -> ItemDisplay.ItemDisplayTransform.GUI;
      case GROUND -> ItemDisplay.ItemDisplayTransform.GROUND;
      case FIXED -> ItemDisplay.ItemDisplayTransform.FIXED;
    });
  }
}
