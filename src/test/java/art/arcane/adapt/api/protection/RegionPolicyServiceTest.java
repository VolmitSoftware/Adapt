package art.arcane.adapt.api.protection;

import art.arcane.adapt.AdaptTestBase;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RegionPolicyServiceTest extends AdaptTestBase {
  private FakeRegionPolicySource source;
  private Player player;
  private Location location;

  @BeforeEach
  void installSource() {
    source = new FakeRegionPolicySource();
    player = mock(Player.class);
    location = new Location(null, 0, 64, 0);
    RegionPolicyService.install(source);
  }

  @AfterEach
  void clearSource() {
    RegionPolicyService.clear();
  }

  @Test
  @DisplayName("no installed source resolves to the default policy")
  void absentSourceResolvesDefault() {
    RegionPolicyService.clear();

    assertThat(RegionPolicyService.isActive()).isFalse();
    assertThat(RegionPolicyService.resolve(player, location)).isSameAs(RegionPolicy.DEFAULT);
  }

  @Test
  @DisplayName("a missing player or location resolves to the default policy")
  void missingArgumentsResolveDefault() {
    source.setPolicy(new RegionPolicy(false, 2D, 4, Set.of("axe-shield-splitter")));

    assertThat(RegionPolicyService.resolve(null, location)).isSameAs(RegionPolicy.DEFAULT);
    assertThat(RegionPolicyService.resolve(player, null)).isSameAs(RegionPolicy.DEFAULT);
    assertThat(source.getCalls()).isZero();
  }

  @Test
  @DisplayName("the installed source answers the query")
  void installedSourceAnswers() {
    RegionPolicy policy = new RegionPolicy(false, 3D, 7, Set.of("axe-shield-splitter"));
    source.setPolicy(policy);

    assertThat(RegionPolicyService.resolve(player, location)).isSameAs(policy);
    assertThat(source.getCalls()).isEqualTo(1);
  }

  @Test
  @DisplayName("a throwing source is quarantined after one failure and stops being consulted")
  void throwingSourceIsQuarantined() {
    source.setFailure(new IllegalStateException("worldguard exploded"));

    assertThat(RegionPolicyService.resolve(player, location)).isSameAs(RegionPolicy.DEFAULT);
    assertThat(RegionPolicyService.isActive()).isFalse();

    assertThat(RegionPolicyService.resolve(player, location)).isSameAs(RegionPolicy.DEFAULT);
    assertThat(source.getCalls()).isEqualTo(1);
  }

  @Test
  @DisplayName("reinstalling a source lifts the quarantine")
  void reinstallLiftsQuarantine() {
    source.setFailure(new IllegalStateException("worldguard exploded"));
    RegionPolicyService.resolve(player, location);

    source.setFailure(null);
    RegionPolicyService.install(source);

    assertThat(RegionPolicyService.isActive()).isTrue();
    assertThat(RegionPolicyService.resolve(player, location)).isSameAs(RegionPolicy.DEFAULT);
    assertThat(source.getCalls()).isEqualTo(2);
  }

  @Test
  @DisplayName("a denied region zeroes the xp award")
  void deniedRegionZeroesXp() {
    assertThat(RegionPolicyService.adjustXp(25D, new RegionPolicy(false, 4D, 0, Set.of()))).isZero();
  }

  @Test
  @DisplayName("the region multiplier scales the xp award")
  void regionMultiplierScalesXp() {
    assertThat(RegionPolicyService.adjustXp(25D, new RegionPolicy(true, 4D, 0, Set.of()))).isEqualTo(100D);
    assertThat(RegionPolicyService.adjustXp(25D, new RegionPolicy(true, 0D, 0, Set.of()))).isZero();
    assertThat(RegionPolicyService.adjustXp(25D, RegionPolicy.DEFAULT)).isEqualTo(25D);
    assertThat(RegionPolicyService.adjustXp(25D, null)).isZero();
  }
}
