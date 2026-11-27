package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.engine.ComponentPort;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.MockedEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscoveryBetterMendingTest {
  private MockedEngine engine;

  @BeforeEach
  void startEngine() {
    engine = new MockedEngine(TestCores.install());
  }

  @AfterEach
  void tearDown() {
    AdaptPorts.uninstall(ComponentPort.class);
    engine.close();
    TestCores.clear();
  }

  @Test
  void spendsCurrentExperiencePointsAndUpdatesLevelProgress() {
    PlayerRef player = mock(PlayerRef.class);
    ComponentPort experience = mock(ComponentPort.class);
    when(experience.totalExperiencePoints(player)).thenReturn(25);
    AdaptPorts.install(ComponentPort.class, experience);

    assertThat(XpMending.spendExperiencePoints(player, 7)).isTrue();
    verify(experience).setExperiencePoints(player, 18);
  }

  @Test
  void refusesMissingOrInvalidExperienceCost() {
    PlayerRef player = mock(PlayerRef.class);
    ComponentPort experience = mock(ComponentPort.class);
    when(experience.totalExperiencePoints(player)).thenReturn(6);
    AdaptPorts.install(ComponentPort.class, experience);

    assertThat(XpMending.spendExperiencePoints(player, 7)).isFalse();
    assertThat(XpMending.spendExperiencePoints(player, 0)).isFalse();
    assertThat(XpMending.spendExperiencePoints(null, 1)).isFalse();
    verify(experience, never()).setExperiencePoints(any(PlayerRef.class), anyInt());
  }

  @Test
  void neutralExperiencePortSetsLevelAndBarFromPoints() {
    FakePlayer player = new FakePlayer("Mender", new Loc(new FakeWorld("world"), 0, 64, 0));
    player.setLevel(2);
    player.setExpProgress(9F / 11F);

    assertThat(XpMending.spendExperiencePoints(player, 7)).isTrue();
    assertThat(player.level()).isEqualTo(2);
    assertThat(player.expProgress()).isCloseTo(2F / 11F, within(1.0E-6F));
  }

  @Test
  void restoredDurabilityNeverOvercountsRepairPastFull() {
    assertThat(DiscoveryBetterMending.restoredDurability(5, 20)).isEqualTo(5);
    assertThat(DiscoveryBetterMending.restoredDurability(20, 5)).isEqualTo(5);
    assertThat(DiscoveryBetterMending.restoredDurability(-1, 5)).isZero();
    assertThat(DiscoveryBetterMending.restoredDurability(5, -1)).isZero();
  }
}
