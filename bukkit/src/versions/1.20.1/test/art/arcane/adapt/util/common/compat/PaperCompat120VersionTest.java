package art.arcane.adapt.util.common.compat;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Paper 1.20.1 always has the level enchanting call and no Bukkit helper to fall back to. */
class PaperCompat120VersionTest {
  @Test
  void enchantingWithLevelsUsesPaperWithTreasure() {
    ItemFactory factory = mock(ItemFactory.class);
    ItemStack book = mock(ItemStack.class);
    ItemStack paperBook = mock(ItemStack.class);
    Random random = new Random(7L);
    when(factory.enchantWithLevels(book, 30, true, random)).thenReturn(paperBook);

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getItemFactory).thenReturn(factory);

      assertThat(PaperCompat.enchantWithLevels(book, 30, random)).isSameAs(paperBook);
      assertThat(PaperCompat.enchantWithLevels(book, 30, random)).isSameAs(paperBook);
    }

    verify(factory, times(2)).enchantWithLevels(book, 30, true, random);
  }
}
