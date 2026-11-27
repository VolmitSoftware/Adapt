package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdaptationRuntimeGuardsMainHandTest {
  @Test
  void materialCooldownDoesNotSuppressPredicateValidatedPassiveAbilities() {
    Adaptation<?> adaptation = mock(Adaptation.class);
    PlayerRef player = mock(PlayerRef.class);
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    ItemRef sword = mock(ItemRef.class);
    Id ironSword = Id.minecraft("iron_sword");
    when(player.inventory()).thenReturn(inventory);
    when(inventory.mainHand()).thenReturn(sword);
    when(sword.type()).thenReturn(ironSword);
    when(player.hasCooldown(ironSword)).thenReturn(true);

    ItemRef resolved = AdaptationRuntimeGuards.readyMainHand(adaptation, player,
        item -> item == sword);

    assertThat(resolved).isSameAs(sword);
    verify(player, never()).hasCooldown(ironSword);
  }
}
