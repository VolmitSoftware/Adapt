package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.golem.GolemFriendlyFire.Party;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class GolemFriendlyFireTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID RIVAL = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
  private static final BooleanSupplier UNASKED = () -> {
    throw new AssertionError("player policy must not be consulted");
  };
  private static final BooleanSupplier ALLOWED = () -> true;
  private static final BooleanSupplier DENIED = () -> false;

  @Test
  void boundGolemNeverDamagesItsOwner() {
    assertThat(GolemFriendlyFire.cancels(golem(OWNER), player(OWNER), UNASKED)).isTrue();
  }

  @Test
  void boundGolemNeverDamagesSameOwnerMinions() {
    assertThat(GolemFriendlyFire.cancels(golem(OWNER), golem(OWNER), UNASKED)).isTrue();
    assertThat(GolemFriendlyFire.cancels(golem(OWNER), servant(OWNER), UNASKED)).isTrue();
  }

  @Test
  void boundGolemMayDamageOtherOwnersMinionsAndMobs() {
    assertThat(GolemFriendlyFire.cancels(golem(OWNER), golem(RIVAL), UNASKED)).isFalse();
    assertThat(GolemFriendlyFire.cancels(golem(OWNER), servant(RIVAL), UNASKED)).isFalse();
    assertThat(GolemFriendlyFire.cancels(golem(OWNER), mob(), UNASKED)).isFalse();
  }

  @Test
  void boundGolemDamagesOtherPlayersOnlyWhenThePlayerPolicyAllows() {
    assertThat(GolemFriendlyFire.cancels(golem(OWNER), player(RIVAL), DENIED)).isTrue();
    assertThat(GolemFriendlyFire.cancels(golem(OWNER), player(RIVAL), ALLOWED)).isFalse();
  }

  @Test
  void ownerNeverDamagesTheirOwnGolem() {
    assertThat(GolemFriendlyFire.cancels(player(OWNER), golem(OWNER), UNASKED)).isTrue();
  }

  @Test
  void otherPlayersMayDamageAGolem() {
    assertThat(GolemFriendlyFire.cancels(player(RIVAL), golem(OWNER), UNASKED)).isFalse();
  }

  @Test
  void sameOwnerMinionsNeverDamageAGolem() {
    assertThat(GolemFriendlyFire.cancels(servant(OWNER), golem(OWNER), UNASKED)).isTrue();
    assertThat(GolemFriendlyFire.cancels(servant(RIVAL), golem(OWNER), UNASKED)).isFalse();
  }

  @Test
  void damageWithoutABoundGolemIsLeftAlone() {
    assertThat(GolemFriendlyFire.cancels(player(OWNER), servant(OWNER), UNASKED)).isFalse();
    assertThat(GolemFriendlyFire.cancels(servant(OWNER), player(OWNER), UNASKED)).isFalse();
    assertThat(GolemFriendlyFire.cancels(mob(), player(OWNER), UNASKED)).isFalse();
    assertThat(GolemFriendlyFire.cancels(mob(), golem(OWNER), UNASKED)).isFalse();
  }

  @Test
  void golemWithUnknownOwnerStillChecksPlayersAgainstThePolicy() {
    assertThat(GolemFriendlyFire.cancels(golem(null), player(RIVAL), DENIED)).isTrue();
    assertThat(GolemFriendlyFire.cancels(golem(null), servant(null), UNASKED)).isFalse();
  }

  private static Party golem(UUID owner) {
    return new Party(UUID.randomUUID(), false, true, owner);
  }

  private static Party servant(UUID owner) {
    return new Party(UUID.randomUUID(), false, false, owner);
  }

  private static Party player(UUID id) {
    return new Party(id, true, false, null);
  }

  private static Party mob() {
    return new Party(UUID.randomUUID(), false, false, null);
  }
}
