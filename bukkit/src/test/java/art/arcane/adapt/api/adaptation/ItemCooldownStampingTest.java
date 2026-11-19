package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.content.item.BoundEyeOfEnder;
import art.arcane.adapt.content.item.BoundRedstoneTorch;
import art.arcane.adapt.platform.item.ItemRef;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.UseCooldownComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

  private ItemRef stackWith(ItemMeta meta) {
    ItemStack stack = mock(ItemStack.class);
    lenient().when(stack.getItemMeta()).thenReturn(meta);
    return BukkitRefs.item(stack);
  }

  @Test
  void stampingWritesTheGroupOntoTheUseCooldownComponent() {
    ItemMeta meta = mock(ItemMeta.class);
    UseCooldownComponent component = mock(UseCooldownComponent.class);
    when(meta.hasUseCooldown()).thenReturn(false);
    when(meta.getUseCooldown()).thenReturn(component);

    assertThat(ItemCooldowns.stampGroup(stackWith(meta), BoundEyeOfEnder.COOLDOWN_GROUP)).isTrue();

    verify(component).setCooldownGroup(BukkitRefs.key(BoundEyeOfEnder.COOLDOWN_GROUP));
    verify(component).setCooldownSeconds(org.mockito.ArgumentMatchers.floatThat(seconds -> seconds > 0F));
    verify(meta).setUseCooldown(component);
  }

  @Test
  void stampingIsIdempotentSoRepeatedUseDoesNotRewriteTheStack() {
    ItemMeta meta = mock(ItemMeta.class);
    UseCooldownComponent component = mock(UseCooldownComponent.class);
    when(meta.hasUseCooldown()).thenReturn(true);
    when(meta.getUseCooldown()).thenReturn(component);
    when(component.getCooldownGroup()).thenReturn(BukkitRefs.key(BoundEyeOfEnder.COOLDOWN_GROUP));

    assertThat(ItemCooldowns.stampGroup(stackWith(meta), BoundEyeOfEnder.COOLDOWN_GROUP)).isFalse();

    verify(meta, never()).setUseCooldown(any());
  }

  @Test
  void stampingReplacesAForeignGroupRatherThanLeavingTheWrongSweep() {
    ItemMeta meta = mock(ItemMeta.class);
    UseCooldownComponent component = mock(UseCooldownComponent.class);
    when(meta.hasUseCooldown()).thenReturn(true);
    when(meta.getUseCooldown()).thenReturn(component);
    when(component.getCooldownGroup()).thenReturn(BukkitRefs.key(BoundRedstoneTorch.COOLDOWN_GROUP));

    assertThat(ItemCooldowns.stampGroup(stackWith(meta), BoundEyeOfEnder.COOLDOWN_GROUP)).isTrue();

    verify(component).setCooldownGroup(BukkitRefs.key(BoundEyeOfEnder.COOLDOWN_GROUP));
    verify(meta).setUseCooldown(component);
  }

  @Test
  void stampingNullsIsANoOpSoCallersCanPassAnUndeclaredGroup() {
    ItemMeta meta = mock(ItemMeta.class);

    assertThat(ItemCooldowns.stampGroup(stackWith(meta), null)).isFalse();
    assertThat(ItemCooldowns.stampGroup((ItemRef) null, BoundEyeOfEnder.COOLDOWN_GROUP)).isFalse();

    verify(meta, never()).setUseCooldown(any());
  }
}
