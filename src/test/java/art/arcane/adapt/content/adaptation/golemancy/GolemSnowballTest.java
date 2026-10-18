package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Snowball;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class GolemSnowballTest {
  private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-0000000000f3");

  @Test
  void taggedSnowballsCarryTheLevelAndTheTargetUnderTheGolemSnowballKey() {
    Snowball snowball = snowball();

    GolemSnowball.tag(snowball, 4, TARGET, false);

    assertThat(GolemSnowball.KEY.toString()).isEqualTo("adapt:golem_snowball");
    assertThat(snowball.getPersistentDataContainer().get(GolemSnowball.KEY, PersistentDataType.INTEGER)).isEqualTo(4);
    assertThat(GolemSnowball.read(snowball)).contains(new GolemSnowball.Tag(4, TARGET, false));
    assertThat(snowball.getPersistentDataContainer().get(GolemSnowball.EXTRA_KEY, PersistentDataType.BYTE)).isNull();
  }

  @Test
  void snowballsWithoutATargetOrTagReadAsExpected() {
    Snowball untargeted = snowball();
    GolemSnowball.tag(untargeted, 2, null, false);
    Snowball vanilla = snowball();
    Snowball corrupt = snowball();
    corrupt.getPersistentDataContainer().set(GolemSnowball.KEY, PersistentDataType.INTEGER, 3);
    corrupt.getPersistentDataContainer().set(GolemSnowball.TARGET_KEY, PersistentDataType.STRING, "not-a-uuid");

    assertThat(GolemSnowball.read(untargeted)).contains(new GolemSnowball.Tag(2, null, false));
    assertThat(GolemSnowball.read(vanilla)).isEmpty();
    assertThat(GolemSnowball.read(corrupt)).contains(new GolemSnowball.Tag(3, null, false));
  }

  @Test
  void extraSnowballsCarryTheExtraFlag() {
    Snowball extra = snowball();

    GolemSnowball.tag(extra, 4, TARGET, true);

    assertThat(GolemSnowball.EXTRA_KEY.toString()).isEqualTo("adapt:golem_snowball_extra");
    assertThat(extra.getPersistentDataContainer().get(GolemSnowball.EXTRA_KEY, PersistentDataType.BYTE)).isEqualTo((byte) 1);
    assertThat(GolemSnowball.read(extra)).contains(new GolemSnowball.Tag(4, TARGET, true));
  }

  private static Snowball snowball() {
    Map<NamespacedKey, Object> values = new HashMap<>();
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    lenient().when(data.get(any(NamespacedKey.class), any(PersistentDataType.class)))
        .thenAnswer(invocation -> values.get(invocation.<NamespacedKey>getArgument(0)));
    lenient().doAnswer(invocation -> values.put(invocation.getArgument(0), invocation.getArgument(2)))
        .when(data).set(any(NamespacedKey.class), any(PersistentDataType.class), any());
    Snowball snowball = mock(Snowball.class);
    lenient().when(snowball.getPersistentDataContainer()).thenReturn(data);
    return snowball;
  }
}
