package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.PlayerStateRegistry;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

public class AdaptDebugModeBypassTest extends AdaptTestBase {
  private final UUID uuid = UUID.randomUUID();
  private AdaptPlayer owner;

  @BeforeEach
  void bindOwner() {
    Player player = mock(Player.class);
    lenient().when(player.getUniqueId()).thenReturn(uuid);
    owner = mock(AdaptPlayer.class);
    lenient().when(owner.getPlayer()).thenReturn(player);
  }

  @AfterEach
  void deactivate() {
    AdaptDebugMode.setActive(uuid, false);
    PlayerStateRegistry.reset();
  }

  @Test
  public void spendKnowledge_debugActive_succeedsWithoutDeducting() {
    PlayerSkillLine line = new PlayerSkillLine();
    line.setLine("herbalism");
    line.setKnowledge(3);
    line.bindRuntimeOwner(owner);

    AdaptDebugMode.setActive(uuid, true);

    assertThat(line.spendKnowledge(500)).isTrue();
    assertThat(line.getKnowledge()).isEqualTo(3);
  }

  @Test
  public void spendKnowledge_debugInactive_failsWhenUnaffordable() {
    PlayerSkillLine line = new PlayerSkillLine();
    line.setLine("herbalism");
    line.setKnowledge(3);
    line.bindRuntimeOwner(owner);

    assertThat(line.spendKnowledge(500)).isFalse();
    assertThat(line.getKnowledge()).isEqualTo(3);
  }

  @Test
  public void spendKnowledge_debugInactive_deductsNormally() {
    PlayerSkillLine line = new PlayerSkillLine();
    line.setLine("herbalism");
    line.setKnowledge(10);
    line.bindRuntimeOwner(owner);

    assertThat(line.spendKnowledge(4)).isTrue();
    assertThat(line.getKnowledge()).isEqualTo(6);
  }

  @Test
  public void spendKnowledge_noRuntimeOwner_neverBypasses() {
    PlayerSkillLine line = new PlayerSkillLine();
    line.setLine("herbalism");
    line.setKnowledge(3);

    AdaptDebugMode.setActive(uuid, true);

    assertThat(line.spendKnowledge(500)).isFalse();
  }

  @Test
  public void hasPowerAvailable_debugActive_alwaysTrue() {
    PlayerData data = new PlayerData();
    data.bindRuntimeOwner(owner);

    AdaptDebugMode.setActive(uuid, true);

    assertThat(data.hasPowerAvailable(1_000_000)).isTrue();
  }

  @Test
  public void hasPowerAvailable_debugInactive_respectsBudget() {
    PlayerData data = new PlayerData();
    data.bindRuntimeOwner(owner);

    assertThat(data.hasPowerAvailable(1_000_000)).isFalse();
  }
}
