package art.arcane.adapt.api.ability;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbilityValueTypesTest {
  @Test
  void reasonTextIsStrippedOfControlCharactersAndTruncated() {
    AbilityUseDecision decision = AbilityUseDecision.deny("no summoning\u0007here");

    assertThat(decision.reason()).isEqualTo("no summoning here");
    assertThat(AbilityUseDecision.deny("x".repeat(400)).reason()).hasSize(128);
  }

  @Test
  void allowIsTheOnlyNonDenyFactory() {
    assertThat(AbilityUseDecision.allow().allowed()).isTrue();
    assertThat(AbilityUseDecision.deny("nope").allowed()).isFalse();
    assertThat(AbilityUseDecision.allow().status()).isEqualTo(AbilityUseStatus.ALLOW);
  }

  @Test
  void aQuoteRejectsNegativePrices() {
    assertThatThrownBy(() -> AbilityQuote.payable("3 Mana").withPrice(-1L, "Mana"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void onlyWaivedAndPayableSuppressTheDefaultCost() {
    assertThat(AbilityQuote.pass().suppressesDefaultCost()).isFalse();
    assertThat(AbilityQuote.waived("free").suppressesDefaultCost()).isTrue();
    assertThat(AbilityQuote.payable("3 Mana").suppressesDefaultCost()).isTrue();
    assertThat(AbilityQuote.insufficient("3 Mana").suppressesDefaultCost()).isFalse();
    assertThat(AbilityQuote.denied("no").suppressesDefaultCost()).isFalse();
  }

  @Test
  void aReservedReservationRequiresAReceipt() {
    assertThatThrownBy(() -> new AbilityReservation(AbilityReservationStatus.RESERVED, null, ""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(AbilityReservation.reserved(AbilityReceipt.of("x")).reserved()).isTrue();
    assertThat(AbilityReservation.failed("broke").reserved()).isFalse();
  }

  @Test
  void aContextNormalizesIdsAndRefusesNegativeLevels() {
    Player player = mock(Player.class);
    UUID id = UUID.randomUUID();
    when(player.getUniqueId()).thenReturn(id);

    AbilityContext context = AbilityContext.check("Tragoul-Skeletal-Servant", "TRAGOUL", 3, player);

    assertThat(context.abilityId()).isEqualTo("tragoul-skeletal-servant");
    assertThat(context.skillId()).isEqualTo("tragoul");
    assertThat(context.playerId()).isEqualTo(id);
    assertThat(context.origin()).isEmpty();
    assertThatThrownBy(() -> new AbilityContext(UUID.randomUUID(), "a", "b", -1, AbilityPhase.CHECK, player,
        Optional.empty())).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theTwoAbilityEventsDoNotShareAHandlerList() {
    assertThat(AdaptAbilityActivateEvent.getHandlerList())
        .isNotSameAs(AdaptAbilityActivatedEvent.getHandlerList());
  }
}
