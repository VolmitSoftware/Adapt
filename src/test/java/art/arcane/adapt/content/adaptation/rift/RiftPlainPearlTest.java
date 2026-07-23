package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.AdaptTestBase;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RiftPlainPearlTest extends AdaptTestBase {
  private static final Path VOID_SKIN_SOURCE = Path.of("src/main/java/art/arcane/adapt/content/adaptation/rift/RiftVoidSkin.java");
  private static final Path CONDUIT_SOURCE = Path.of("src/main/java/art/arcane/adapt/content/adaptation/rift/RiftConduit.java");

  @BeforeEach
  void configurePluginIdentity() {
    lenient().when(plugin.namespace()).thenReturn("adapt");
  }

  @Test
  void metaLessPearlIsPlain() {
    ItemStack stack = mock(ItemStack.class);
    when(stack.getType()).thenReturn(Material.ENDER_PEARL);
    when(stack.getAmount()).thenReturn(1);
    when(stack.hasItemMeta()).thenReturn(false);

    assertThat(RiftPearls.isPlainPearl(stack)).isTrue();
  }

  @Test
  void renamedPearlWithoutAdaptKeysIsStillPlain() {
    StubPearl pearl = pearlWithMeta();

    assertThat(RiftPearls.isPlainPearl(pearl.stack())).isTrue();
  }

  @Test
  void conduitTaglockPearlsAreNotPlain() {
    for (String keyName : List.of(RiftConduit.TAGLOCK_LOC_KEY_NAME, RiftConduit.TAGLOCK_ID_KEY_NAME)) {
      StubPearl pearl = pearlWithMeta();
      when(pearl.data().has(eq(new NamespacedKey(plugin, keyName)), eq(PersistentDataType.STRING))).thenReturn(true);

      assertThat(RiftPearls.isPlainPearl(pearl.stack())).isFalse();
    }
  }

  @Test
  void enderTaglockTaggedPearlIsNotPlain() {
    StubPearl pearl = pearlWithMeta();
    when(pearl.data().has(eq(new NamespacedKey(plugin, RiftEnderTaglock.TARGET_KEY_NAME)), eq(PersistentDataType.STRING))).thenReturn(true);

    assertThat(RiftPearls.isPlainPearl(pearl.stack())).isFalse();
  }

  @Test
  void boundEnderPearlIsNotPlain() {
    StubPearl pearl = pearlWithMeta();
    when(pearl.data().has(any(NamespacedKey.class), eq(PersistentDataType.STRING))).thenReturn(true);

    assertThat(RiftPearls.isPlainPearl(pearl.stack())).isFalse();
  }

  @Test
  void missingWrongTypedAndEmptyStacksAreNotPlain() {
    ItemStack wrongMaterial = mock(ItemStack.class);
    when(wrongMaterial.getType()).thenReturn(Material.STONE);

    ItemStack emptyStack = mock(ItemStack.class);
    when(emptyStack.getType()).thenReturn(Material.ENDER_PEARL);
    when(emptyStack.getAmount()).thenReturn(0);

    assertThat(RiftPearls.isPlainPearl(null)).isFalse();
    assertThat(RiftPearls.isPlainPearl(wrongMaterial)).isFalse();
    assertThat(RiftPearls.isPlainPearl(emptyStack)).isFalse();
  }

  @Test
  void voidSkinAndConduitCaptureShareThePlainPearlPredicate() throws IOException {
    String voidSkin = Files.readString(VOID_SKIN_SOURCE);
    String conduit = Files.readString(CONDUIT_SOURCE);

    assertThat(voidSkin)
        .contains("RiftPearls.isPlainPearl")
        .doesNotContain("hasItemMeta");
    assertThat(conduit)
        .contains("RiftPearls.isPlainPearl(hand)")
        .doesNotContain("!hand.hasItemMeta()");
  }

  private StubPearl pearlWithMeta() {
    ItemStack stack = mock(ItemStack.class);
    ItemMeta meta = mock(ItemMeta.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    when(stack.getType()).thenReturn(Material.ENDER_PEARL);
    when(stack.getAmount()).thenReturn(1);
    when(stack.hasItemMeta()).thenReturn(true);
    when(stack.getItemMeta()).thenReturn(meta);
    when(meta.getPersistentDataContainer()).thenReturn(data);
    return new StubPearl(stack, data);
  }

  private record StubPearl(ItemStack stack, PersistentDataContainer data) {
  }
}
