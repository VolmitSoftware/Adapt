package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.content.item.BoundEyeOfEnder;
import art.arcane.adapt.platform.item.ItemRef;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** Items have no use-cooldown component before 1.21.2, so stamping never touches the stack. */
class ItemCooldownStampingTest extends AdaptTestBase {
  @BeforeEach
  void installBukkitStamping() throws ReflectiveOperationException {
    Class<? extends ItemCooldownGroupPort> type = Class
        .forName("art.arcane.adapt.bukkit.port.BukkitItemCooldownGroupPort")
        .asSubclass(ItemCooldownGroupPort.class);
    Constructor<? extends ItemCooldownGroupPort> constructor = type.getDeclaredConstructor();
    constructor.setAccessible(true);
    AdaptPorts.install(ItemCooldownGroupPort.class, constructor.newInstance());
  }

  @AfterEach
  void resetRegistry() {
    AdaptPorts.uninstall(ItemCooldownGroupPort.class);
    PlayerStateRegistry.reset();
  }

  @Test
  void stampingReportsNoChangeAndLeavesTheItemAlone() {
    ItemMeta meta = mock(ItemMeta.class);
    ItemStack stack = mock(ItemStack.class);
    lenient().when(stack.getItemMeta()).thenReturn(meta);
    ItemRef item = BukkitRefs.item(stack);

    assertThat(ItemCooldowns.stampGroup(item, BoundEyeOfEnder.COOLDOWN_GROUP)).isFalse();
    assertThat(ItemCooldowns.stampGroup(item, null)).isFalse();
    assertThat(ItemCooldowns.stampGroup((ItemRef) null, BoundEyeOfEnder.COOLDOWN_GROUP)).isFalse();

    verifyNoInteractions(meta);
  }
}
