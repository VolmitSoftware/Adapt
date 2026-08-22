package art.arcane.adapt.content.adaptation.taming;

import org.bukkit.entity.Bee;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TamingWildEmpathyTest {
  @Test
  void wildNeutralAggressorsRemainPacifiable() {
    Wolf wolf = mock(Wolf.class);
    Bee bee = mock(Bee.class);
    when(wolf.isTamed()).thenReturn(false);

    assertThat(TamingWildEmpathy.isPacifiableNeutral(wolf)).isTrue();
    assertThat(TamingWildEmpathy.isPacifiableNeutral(bee)).isTrue();
  }

  @Test
  void tamedAggressorsAreNeverCancelledByWildEmpathy() {
    Wolf wolf = mock(Wolf.class);
    when(wolf.isTamed()).thenReturn(true);

    assertThat(TamingWildEmpathy.isPacifiableNeutral(wolf)).isFalse();
  }

  @Test
  void unrelatedEntitiesAreNotPacified() {
    assertThat(TamingWildEmpathy.isPacifiableNeutral(mock(Player.class))).isFalse();
  }
}
