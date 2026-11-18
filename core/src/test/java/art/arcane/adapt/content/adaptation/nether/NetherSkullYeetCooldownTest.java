package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetherSkullYeetCooldownTest {
  @Test
  void cooldownScalesDownPerLevelFromDefaults() {
    assertThat(NetherSkullYeet.cooldownSeconds(15, 5, 1)).isEqualTo(10);
    assertThat(NetherSkullYeet.cooldownSeconds(15, 5, 2)).isEqualTo(5);
  }

  @Test
  void cooldownNeverDropsBelowOneSecondAtOrBeyondMaxLevel() {
    assertThat(NetherSkullYeet.cooldownSeconds(15, 5, 3)).isEqualTo(1);
    assertThat(NetherSkullYeet.cooldownSeconds(15, 5, 4)).isEqualTo(1);
    assertThat(NetherSkullYeet.cooldownSeconds(15, 5, 100)).isEqualTo(1);
  }

  @Test
  void longBombDistanceRequiresTheSameWorld() {
    WorldRef sourceWorld = mock(WorldRef.class);
    WorldRef targetWorld = mock(WorldRef.class);
    Loc source = mock(Loc.class);
    Loc target = mock(Loc.class);
    when(source.world()).thenReturn(sourceWorld);
    when(target.world()).thenReturn(targetWorld);

    assertThat(NetherSkullYeet.isLongBomb(source, target, 40D)).isFalse();
    verify(source, never()).distanceSquared(target);

    when(target.world()).thenReturn(sourceWorld);
    when(source.distanceSquared(target)).thenReturn(1600D);
    assertThat(NetherSkullYeet.isLongBomb(source, target, 40D)).isTrue();
  }
}
