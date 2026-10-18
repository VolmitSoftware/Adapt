package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class AnimusBindingRulesTest {
  private static final UUID BUILDER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
  private static final long BUILT_AT = 1_000_000L;
  private static final long WINDOW = 300_000L;

  @Test
  void perLevelValuesUseTheLevelEntryAndRepeatTheLastEntryAboveTheList() {
    List<Integer> values = List.of(1, 2, 3);

    assertThat(AnimusBindingRules.atLevel(values, 1, 0)).isEqualTo(1);
    assertThat(AnimusBindingRules.atLevel(values, 3, 0)).isEqualTo(3);
    assertThat(AnimusBindingRules.atLevel(values, 9, 0)).isEqualTo(3);
    assertThat(AnimusBindingRules.atLevel(values, 0, 0)).isZero();
    assertThat(AnimusBindingRules.atLevel(List.of(), 2, 7)).isEqualTo(7);
    assertThat(AnimusBindingRules.atLevel(null, 2, 7)).isEqualTo(7);
  }

  @Test
  void golemCapFollowsTheLevelTableAndNeverExceedsTheHardCap() {
    List<Integer> caps = List.of(1, 2, 3, 5, 7);

    assertThat(AnimusBindingRules.golemCap(caps, 1, 32)).isEqualTo(1);
    assertThat(AnimusBindingRules.golemCap(caps, 4, 32)).isEqualTo(5);
    assertThat(AnimusBindingRules.golemCap(caps, 5, 32)).isEqualTo(7);
    assertThat(AnimusBindingRules.golemCap(caps, 5, 4)).isEqualTo(4);
    assertThat(AnimusBindingRules.golemCap(caps, 0, 32)).isZero();
    assertThat(AnimusBindingRules.golemCap(List.of(-3), 1, 32)).isZero();
  }

  @Test
  void tithesAreNeverNegative() {
    assertThat(AnimusBindingRules.tithe(List.of(3, 2, 1), 2)).isEqualTo(2);
    assertThat(AnimusBindingRules.tithe(List.of(-4), 1)).isZero();
    assertThat(AnimusBindingRules.tithe(List.of(), 1)).isZero();
  }

  @Test
  void ritualSecondsBecomeTicksOfAtLeastOne() {
    List<Double> seconds = List.of(4.0, 3.5, 2.0);

    assertThat(AnimusBindingRules.ritualTicks(seconds, 1)).isEqualTo(80);
    assertThat(AnimusBindingRules.ritualTicks(seconds, 2)).isEqualTo(70);
    assertThat(AnimusBindingRules.ritualTicks(seconds, 5)).isEqualTo(40);
    assertThat(AnimusBindingRules.ritualTicks(List.of(0.0), 1)).isEqualTo(1);
    assertThat(AnimusBindingRules.ritualTicks(List.of(Double.NaN), 1)).isEqualTo(1);
    assertThat(AnimusBindingRules.ritualTicks(List.of(), 1)).isEqualTo(1);
  }

  @Test
  void frostBindsWithAnimusBindingAloneWhileCopperAndIronNeedTheirFrames() {
    assertThat(AnimusBindingRules.bodyUnlocked(GolemBody.FROST, 0, 0)).isTrue();
    assertThat(AnimusBindingRules.bodyUnlocked(GolemBody.COPPER, 0, 5)).isFalse();
    assertThat(AnimusBindingRules.bodyUnlocked(GolemBody.COPPER, 1, 0)).isTrue();
    assertThat(AnimusBindingRules.bodyUnlocked(GolemBody.IRON, 5, 0)).isFalse();
    assertThat(AnimusBindingRules.bodyUnlocked(GolemBody.IRON, 0, 1)).isTrue();
    assertThat(AnimusBindingRules.bodyUnlocked(GolemBody.BONE, 5, 5)).isFalse();
  }

  @Test
  void onlyTheBuilderMayBindWithinTheExclusiveWindow() {
    OptionalLong built = OptionalLong.of(BUILT_AT);

    assertThat(AnimusBindingRules.builderExclusive(BUILDER, built, STRANGER, BUILT_AT + WINDOW - 1, WINDOW)).isTrue();
    assertThat(AnimusBindingRules.builderExclusive(BUILDER, built, STRANGER, BUILT_AT + WINDOW, WINDOW)).isFalse();
    assertThat(AnimusBindingRules.builderExclusive(BUILDER, built, BUILDER, BUILT_AT + 1, WINDOW)).isFalse();
    assertThat(AnimusBindingRules.builderExclusive(null, OptionalLong.empty(), STRANGER, BUILT_AT, WINDOW)).isFalse();
    assertThat(AnimusBindingRules.builderExclusive(BUILDER, OptionalLong.empty(), STRANGER, BUILT_AT, WINDOW)).isFalse();
    assertThat(AnimusBindingRules.builderExclusive(BUILDER, built, STRANGER, BUILT_AT + 1, 0L)).isFalse();
  }

  @Test
  void aGolemThatPassesEveryCheckIsReady() {
    assertThat(AnimusBindingRules.verdict(new Check().build())).isEqualTo(BindingVerdict.READY);
  }

  @Test
  void eachFailedCheckNamesItsOwnReason() {
    assertThat(verdict(check -> check.registryBlock = GolemRegistry.BindBlock.ALREADY_BOUND)).isEqualTo(BindingVerdict.ALREADY_BOUND);
    assertThat(verdict(check -> check.playerCreated = false)).isEqualTo(BindingVerdict.VILLAGE_GOLEM);
    assertThat(verdict(check -> check.builderExclusive = true)).isEqualTo(BindingVerdict.NOT_BUILDER);
    assertThat(verdict(check -> check.bodyUnlocked = false)).isEqualTo(BindingVerdict.BODY_LOCKED);
    assertThat(verdict(check -> check.registryBlock = GolemRegistry.BindBlock.BODY_MISMATCH)).isEqualTo(BindingVerdict.BODY_LOCKED);
    assertThat(verdict(check -> check.registryBlock = GolemRegistry.BindBlock.CORE_NOT_ALLOWED)).isEqualTo(BindingVerdict.CORE_NOT_ALLOWED);
    assertThat(verdict(check -> check.coreUnlocked = false)).isEqualTo(BindingVerdict.CORE_LOCKED);
    assertThat(verdict(check -> check.canInteract = false)).isEqualTo(BindingVerdict.PROTECTED);
    assertThat(verdict(check -> check.registryBlock = GolemRegistry.BindBlock.OWNER_DATA_UNAVAILABLE)).isEqualTo(BindingVerdict.UNAVAILABLE);
    assertThat(verdict(check -> check.ownedGolems = 3)).isEqualTo(BindingVerdict.GOLEM_CAP);
    assertThat(verdict(check -> check.registryBlock = GolemRegistry.BindBlock.PLAYER_CAP)).isEqualTo(BindingVerdict.GOLEM_CAP);
    assertThat(verdict(check -> check.registryBlock = GolemRegistry.BindBlock.CHUNK_CAP)).isEqualTo(BindingVerdict.CHUNK_CAP);
    assertThat(verdict(check -> check.playerLevel = 1)).isEqualTo(BindingVerdict.NOT_ENOUGH_LEVELS);
  }

  @Test
  void villageGolemsBindOnlyWhenTheServerAllowsIt() {
    assertThat(verdict(check -> {
      check.playerCreated = false;
      check.allowVillageGolems = true;
    })).isEqualTo(BindingVerdict.READY);
  }

  @Test
  void aZeroTitheNeedsNoLevels() {
    assertThat(verdict(check -> {
      check.tithe = 0;
      check.playerLevel = 0;
    })).isEqualTo(BindingVerdict.READY);
  }

  @Test
  void earlierChecksWinWhenSeveralFail() {
    assertThat(verdict(check -> {
      check.registryBlock = GolemRegistry.BindBlock.ALREADY_BOUND;
      check.playerCreated = false;
      check.playerLevel = 0;
    })).isEqualTo(BindingVerdict.ALREADY_BOUND);
    assertThat(verdict(check -> {
      check.playerCreated = false;
      check.builderExclusive = true;
    })).isEqualTo(BindingVerdict.VILLAGE_GOLEM);
    assertThat(verdict(check -> {
      check.bodyUnlocked = false;
      check.coreUnlocked = false;
    })).isEqualTo(BindingVerdict.BODY_LOCKED);
    assertThat(verdict(check -> {
      check.canInteract = false;
      check.ownedGolems = 9;
    })).isEqualTo(BindingVerdict.PROTECTED);
    assertThat(verdict(check -> {
      check.ownedGolems = 9;
      check.registryBlock = GolemRegistry.BindBlock.CHUNK_CAP;
      check.playerLevel = 0;
    })).isEqualTo(BindingVerdict.GOLEM_CAP);
    assertThat(verdict(check -> {
      check.registryBlock = GolemRegistry.BindBlock.CHUNK_CAP;
      check.playerLevel = 0;
    })).isEqualTo(BindingVerdict.CHUNK_CAP);
  }

  private static BindingVerdict verdict(Consumer<Check> change) {
    Check check = new Check();
    change.accept(check);
    return AnimusBindingRules.verdict(check.build());
  }

  private static final class Check {
    private GolemRegistry.BindBlock registryBlock;
    private boolean playerCreated = true;
    private boolean allowVillageGolems;
    private boolean builderExclusive;
    private boolean bodyUnlocked = true;
    private boolean coreUnlocked = true;
    private boolean canInteract = true;
    private int ownedGolems = 1;
    private int golemCap = 3;
    private int playerLevel = 5;
    private int tithe = 2;

    private BindingCheck build() {
      return new BindingCheck(registryBlock, playerCreated, allowVillageGolems, builderExclusive, bodyUnlocked, coreUnlocked,
          canInteract, ownedGolems, golemCap, playerLevel, tithe);
    }
  }
}
