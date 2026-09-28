package art.arcane.adapt.api.preference;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class PreferenceConfirmationTest extends AdaptTestBase {
  @Test
  void approvalRequiresTheSamePlayerActionAndItemAndIsConsumedOnce() {
    Adaptation<?> adaptation = mock(Adaptation.class);
    when(adaptation.getName()).thenReturn("crafting-deconstruction");
    Player first = mock(Player.class);
    Player second = mock(Player.class);
    when(first.getUniqueId()).thenReturn(UUID.randomUUID());
    when(second.getUniqueId()).thenReturn(UUID.randomUUID());
    when(first.isOnline()).thenReturn(true);
    when(second.isOnline()).thenReturn(true);
    ItemStack item = mock(ItemStack.class);
    ItemStack snapshot = mock(ItemStack.class);
    ItemStack changed = mock(ItemStack.class);
    when(item.clone()).thenReturn(snapshot);
    when(changed.clone()).thenReturn(changed);
    try (MockedStatic<J> scheduler = mockStatic(J.class);
         MockedStatic<AdaptLanguage> language = mockStatic(AdaptLanguage.class)) {
      scheduler.when(() -> J.isOwnedByCurrentRegion(first)).thenReturn(true);
      scheduler.when(() -> J.isOwnedByCurrentRegion(second)).thenReturn(true);
      assertThat(PreferenceConfirmation.confirm(adaptation, first, "salvage", item)).isFalse();
      assertThat(PreferenceConfirmation.confirm(adaptation, second, "salvage", item)).isFalse();
      assertThat(PreferenceConfirmation.confirm(adaptation, first, "salvage", changed)).isFalse();
      assertThat(PreferenceConfirmation.confirm(adaptation, first, "salvage", item)).isFalse();
      assertThat(PreferenceConfirmation.confirm(adaptation, first, "different-action", item)).isFalse();
      assertThat(PreferenceConfirmation.confirm(adaptation, first, "different-action", item)).isTrue();
      assertThat(PreferenceConfirmation.confirm(adaptation, first, "different-action", item)).isFalse();
      PreferenceConfirmation.clear(first);
      assertThat(PreferenceConfirmation.confirm(adaptation, first, "different-action", item)).isFalse();
    } finally {
      PreferenceConfirmation.clear(first);
      PreferenceConfirmation.clear(second);
    }
  }
}
