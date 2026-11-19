package art.arcane.adapt.util.common.compat;

import net.kyori.adventure.text.Component;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Paper before 26.x has no copper golem, and cave vines report berries through the older Bukkit getter. */
class PaperCompatVersionTest {
  @Test
  void copperStateIsUnweatheredUnwaxedAndUntouchedWithoutCopperGolems() {
    IronGolem iron = mock(IronGolem.class);

    assertThat(PaperCompat.weatheringStage(iron)).isZero();
    assertThat(PaperCompat.copperWaxed(iron)).isFalse();
    PaperCompat.restoreCopper(iron, 3, true);

    verifyNoInteractions(iron);
  }

  @Test
  void berriesAndCustomNamesReadThroughTheServerApi() {
    CaveVinesPlant ripe = mock(CaveVinesPlant.class);
    CaveVinesPlant bare = mock(CaveVinesPlant.class);
    when(ripe.isBerries()).thenReturn(true);
    Zombie named = mock(Zombie.class);
    Zombie nameless = mock(Zombie.class);
    when(named.customName()).thenReturn(Component.text("Bob"));

    assertThat(PaperCompat.hasBerries(ripe)).isTrue();
    assertThat(PaperCompat.hasBerries(bare)).isFalse();
    assertThat(PaperCompat.hasCustomName(named)).isTrue();
    assertThat(PaperCompat.hasCustomName(nameless)).isFalse();
  }
}
