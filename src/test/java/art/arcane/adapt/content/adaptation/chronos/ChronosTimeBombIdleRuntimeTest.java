package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.content.item.ChronoTimeBombItem;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ChronosTimeBombIdleRuntimeTest extends AdaptTestBase {
  @Test
  void emptyRuntimeParksAndActivationWakesItRaceSafely() throws Exception {
    when(plugin.getName()).thenReturn("Adapt");
    when(plugin.namespace()).thenReturn("adapt");
    try (MockedStatic<ChronoTimeBombItem> itemFactory = mockStatic(ChronoTimeBombItem.class);
         MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      itemFactory.when(ChronoTimeBombItem::withData).thenReturn(mock(ItemStack.class));
      bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of());
      ChronosTimeBomb adaptation = new ChronosTimeBomb();

      assertThat(adaptation.getInterval()).isEqualTo(Long.MAX_VALUE);

      adaptation.onRuntimeActivated();

      assertThat(adaptation.isBursting()).isTrue();
      assertThat(adaptation.getInterval()).isZero();

      adaptation.setInterval(Long.MAX_VALUE);
      assertThat(adaptation.getInterval()).isZero();

      adaptation.stopBursting();
      startupSweepDone(adaptation).set(true);
      adaptation.onTick();

      assertThat(adaptation.getInterval()).isEqualTo(Long.MAX_VALUE);
    }
  }

  private static AtomicBoolean startupSweepDone(ChronosTimeBomb adaptation) throws Exception {
    Field field = ChronosTimeBomb.class.getDeclaredField("startupSweepDone");
    field.setAccessible(true);
    return (AtomicBoolean) field.get(adaptation);
  }
}
