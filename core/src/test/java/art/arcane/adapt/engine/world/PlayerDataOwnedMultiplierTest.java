package art.arcane.adapt.engine.world;

import art.arcane.adapt.engine.xp.XPMultiplier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerDataOwnedMultiplierTest {
  @Test
  void persistedOwnershipRemovesOnlyTheRequestedGrant() {
    PlayerData data = new PlayerData();
    data.globalXPMultiplier(0.2D, 60000L);
    data.globalXPMultiplier(XPMultiplier.owned("adapt:discovery/polymath", 0.4D, 60000L));
    data.globalXPMultiplier(XPMultiplier.owned("another-source", 0.8D, 60000L));

    PlayerData restored = PlayerData.fromJson(data.toJson(true));
    restored.removeGlobalXPMultipliers("adapt:discovery/polymath");

    assertThat(restored.getMultipliers()).hasSize(2);
    assertThat(restored.getMultipliers()).extracting(XPMultiplier::getSource)
        .containsExactly(null, "another-source");
    assertThat(restored.getMultipliers()).extracting(XPMultiplier::getMultiplier)
        .containsExactly(0.2D, 0.8D);
  }

  @Test
  void absentSourceDoesNotRemoveUnownedOrOtherGrants() {
    PlayerData data = new PlayerData();
    data.globalXPMultiplier(0.3D, 60000L);
    data.getMultipliers().add((XPMultiplier) null);
    data.globalXPMultiplier(XPMultiplier.owned("another-source", 0.5D, 60000L));

    data.removeGlobalXPMultipliers("adapt:discovery/polymath");

    assertThat(data.getMultipliers()).hasSize(3);
    assertThat(data.getMultipliers().get(0).getMultiplier()).isEqualTo(0.3D);
    assertThat(data.getMultipliers().get(2).getSource()).isEqualTo("another-source");
  }
}
