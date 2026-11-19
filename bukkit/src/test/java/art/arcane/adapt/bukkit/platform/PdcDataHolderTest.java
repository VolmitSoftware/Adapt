package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.bukkit.platform.data.PdcDataHolder;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PdcDataHolderTest {
  private static final DataKey<Integer> LEVEL = DataKey.adapt("level", DataCodecs.INT);
  private static final DataKey<String> LEVEL_AS_STRING = DataKey.adapt("level", DataCodecs.STRING);

  @Test
  void everyStorageTypeRoundTrips() {
    FakeDataContainer container = new FakeDataContainer();
    DataHolder data = PdcDataHolder.live(container);

    data.set(DataKey.adapt("b", DataCodecs.BYTE), (byte) 3);
    data.set(DataKey.adapt("s", DataCodecs.SHORT), (short) 4);
    data.set(LEVEL, 5);
    data.set(DataKey.adapt("l", DataCodecs.LONG), 6L);
    data.set(DataKey.adapt("f", DataCodecs.FLOAT), 7.5F);
    data.set(DataKey.adapt("d", DataCodecs.DOUBLE), 8.25D);
    data.set(DataKey.adapt("t", DataCodecs.STRING), "text");
    data.set(DataKey.adapt("flag", DataCodecs.BOOLEAN), true);
    data.set(DataKey.adapt("ba", DataCodecs.BYTE_ARRAY), new byte[]{1, 2});
    data.set(DataKey.adapt("ia", DataCodecs.INT_ARRAY), new int[]{3, 4});
    data.set(DataKey.adapt("la", DataCodecs.LONG_ARRAY), new long[]{5L, 6L});

    assertThat(data.get(DataKey.adapt("b", DataCodecs.BYTE))).isEqualTo((byte) 3);
    assertThat(data.get(DataKey.adapt("s", DataCodecs.SHORT))).isEqualTo((short) 4);
    assertThat(data.get(LEVEL)).isEqualTo(5);
    assertThat(data.get(DataKey.adapt("l", DataCodecs.LONG))).isEqualTo(6L);
    assertThat(data.get(DataKey.adapt("f", DataCodecs.FLOAT))).isEqualTo(7.5F);
    assertThat(data.get(DataKey.adapt("d", DataCodecs.DOUBLE))).isEqualTo(8.25D);
    assertThat(data.get(DataKey.adapt("t", DataCodecs.STRING))).isEqualTo("text");
    assertThat(data.get(DataKey.adapt("flag", DataCodecs.BOOLEAN))).isTrue();
    assertThat(data.get(DataKey.adapt("ba", DataCodecs.BYTE_ARRAY))).containsExactly(1, 2);
    assertThat(data.get(DataKey.adapt("ia", DataCodecs.INT_ARRAY))).containsExactly(3, 4);
    assertThat(data.get(DataKey.adapt("la", DataCodecs.LONG_ARRAY))).containsExactly(5L, 6L);
    assertThat(data.keys()).hasSize(11).contains(Id.adapt("level"));
  }

  @Test
  void valuesUseThePluginsNativeKeysAndTypes() {
    FakeDataContainer container = new FakeDataContainer();
    container.set(new NamespacedKey("adapt", "level"), PersistentDataType.INTEGER, 9);
    container.set(new NamespacedKey("adapt", "flag"), PersistentDataType.BOOLEAN, true);
    DataHolder data = PdcDataHolder.live(container);

    assertThat(data.get(LEVEL)).isEqualTo(9);
    assertThat(data.get(DataKey.adapt("flag", DataCodecs.BOOLEAN))).isTrue();

    data.set(LEVEL, 10);
    assertThat(container.values.get(new NamespacedKey("adapt", "level"))).isEqualTo(10);
  }

  @Test
  void typeMismatchReadsAsAbsentButStillCountsAsPresent() {
    DataHolder data = PdcDataHolder.live(new FakeDataContainer());
    data.set(LEVEL, 2);

    assertThat(data.get(LEVEL_AS_STRING)).isNull();
    assertThat(data.has(LEVEL_AS_STRING)).isFalse();
    assertThat(data.has(LEVEL)).isTrue();
    assertThat(data.hasAny(Id.adapt("level"))).isTrue();
  }

  @Test
  void settingNullOrRemovingClearsTheValue() {
    DataHolder data = PdcDataHolder.live(new FakeDataContainer());
    data.set(LEVEL, 2);
    data.set(LEVEL, null);
    assertThat(data.isEmpty()).isTrue();

    data.set(LEVEL, 3);
    data.remove(LEVEL);
    assertThat(data.has(LEVEL)).isFalse();
    assertThat(data.keys()).isEmpty();
  }

  @Test
  void emptyItemsCarryNoData() {
    ItemStack stack = mock(ItemStack.class);
    when(stack.getType()).thenReturn(Material.AIR);

    DataHolder data = BukkitRefs.item(stack).data();
    data.set(LEVEL, 4);

    assertThat(data.isEmpty()).isTrue();
    verify(stack, never()).setItemMeta(any());
  }

  @Test
  void entityDataUsesTheEntityContainer() {
    FakeDataContainer container = new FakeDataContainer();
    Zombie zombie = mock(Zombie.class);
    when(zombie.getUniqueId()).thenReturn(UUID.randomUUID());
    when(zombie.getType()).thenReturn(EntityType.ZOMBIE);
    when(zombie.getPersistentDataContainer()).thenReturn(container);

    BukkitRefs.entity(zombie).data().set(LEVEL, 7);

    assertThat(container.values.get(new NamespacedKey("adapt", "level"))).isEqualTo(7);
  }
}
