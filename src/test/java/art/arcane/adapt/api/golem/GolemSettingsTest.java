package art.arcane.adapt.api.golem;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GolemSettingsTest {
  @Test
  void defaultsMatchTheBrief() {
    GolemSettings settings = GolemSettings.defaults();

    assertThat(settings.dormantInvulnerable()).isTrue();
    assertThat(settings.guardWhileOwnerOffline()).isFalse();
    assertThat(settings.serverMaxActiveGolems()).isEqualTo(1500);
    assertThat(settings.maxBoundGolemsPerChunk()).isEqualTo(8);
    assertThat(settings.hardCapPerPlayer()).isEqualTo(32);
    assertThat(settings.deathDropCoreChance()).isEqualTo(0.5D);
    assertThat(settings.deathDropRuneChance()).isEqualTo(0.5D);
    assertThat(settings.allowedCores(GolemBody.FROST)).containsExactlyInAnyOrder(
        GolemCoreType.BLANK, GolemCoreType.GUARDIAN, GolemCoreType.GATHERER, GolemCoreType.HARVESTER);
    assertThat(settings.allowedCores(GolemBody.COPPER)).containsExactlyInAnyOrder(
        GolemCoreType.BLANK, GolemCoreType.GATHERER, GolemCoreType.HARVESTER, GolemCoreType.LUMBER,
        GolemCoreType.RANCHER, GolemCoreType.SORTER, GolemCoreType.ANGLER);
    assertThat(settings.allowedCores(GolemBody.IRON)).containsExactlyInAnyOrder(
        GolemCoreType.BLANK, GolemCoreType.GUARDIAN, GolemCoreType.GATHERER, GolemCoreType.LUMBER,
        GolemCoreType.BREAKER, GolemCoreType.RANCHER, GolemCoreType.ANGLER);
    assertThat(settings.allowedCores(GolemBody.BONE)).containsExactly(GolemCoreType.GUARDIAN);
    assertThat(settings.bodyCarry(GolemBody.FROST)).isEqualTo(8);
    assertThat(settings.bodyCarry(GolemBody.COPPER)).isEqualTo(16);
    assertThat(settings.bodyCarry(GolemBody.IRON)).isEqualTo(64);
    assertThat(settings.bodyCarry(GolemBody.BONE)).isZero();
  }

  @Test
  void allowsChecksTheBodyMatrix() {
    GolemSettings settings = GolemSettings.defaults();

    assertThat(settings.allows(GolemBody.IRON, GolemCoreType.BREAKER)).isTrue();
    assertThat(settings.allows(GolemBody.COPPER, GolemCoreType.BREAKER)).isFalse();
    assertThat(settings.allows(GolemBody.FROST, GolemCoreType.SORTER)).isFalse();
    assertThat(settings.allows(GolemBody.COPPER, GolemCoreType.GUARDIAN)).isFalse();
  }

  @Test
  void valuesAreClampedAtTheBoundary() {
    GolemSettings settings = new GolemSettings(true, true, -5, -1, -2,
        Map.of(GolemBody.FROST, 0.001D, GolemBody.IRON, 400D), Map.of(GolemBody.FROST, -4, GolemBody.IRON, 200, GolemBody.BONE, 30),
        Map.of(), 1.5D, -0.25D);

    assertThat(settings.serverMaxActiveGolems()).isZero();
    assertThat(settings.maxBoundGolemsPerChunk()).isZero();
    assertThat(settings.hardCapPerPlayer()).isZero();
    assertThat(settings.bodyScale(GolemBody.FROST)).isEqualTo(GolemScale.MIN_SCALE);
    assertThat(settings.bodyScale(GolemBody.IRON)).isEqualTo(GolemScale.MAX_SCALE);
    assertThat(settings.bodyScale(GolemBody.COPPER)).isEqualTo(1D);
    assertThat(settings.deathDropCoreChance()).isEqualTo(1D);
    assertThat(settings.deathDropRuneChance()).isZero();
    assertThat(settings.allowedCores(GolemBody.FROST)).isEmpty();
    assertThat(settings.allowedCores(GolemBody.BONE)).containsExactly(GolemCoreType.GUARDIAN);
    assertThat(settings.bodyCarry(GolemBody.FROST)).isZero();
    assertThat(settings.bodyCarry(GolemBody.IRON)).isEqualTo(200);
    assertThat(settings.bodyCarry(GolemBody.COPPER)).isZero();
    assertThat(settings.bodyCarry(GolemBody.BONE)).isZero();
  }

  @Test
  void allowedCoreSetsAreDefensiveCopies() {
    Set<GolemCoreType> frost = EnumSet.of(GolemCoreType.BLANK);
    GolemSettings settings = new GolemSettings(true, false, 1, 1, 1, Map.of(), Map.of(), Map.of(GolemBody.FROST, frost), 0D, 0D);

    frost.add(GolemCoreType.SORTER);

    assertThat(settings.allowedCores(GolemBody.FROST)).containsExactly(GolemCoreType.BLANK);
  }

  @Test
  void coreNamesParseCaseInsensitivelyAndSkipUnknownNames() {
    assertThat(GolemSettings.parseCores(List.of("BLANK", "guardian", " Sorter ", "WIZARD", ""))).containsExactlyInAnyOrder(
        GolemCoreType.BLANK, GolemCoreType.GUARDIAN, GolemCoreType.SORTER);
    assertThat(GolemSettings.parseCores(null)).isEmpty();
  }
}
