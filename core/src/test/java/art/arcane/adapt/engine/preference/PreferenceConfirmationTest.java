package art.arcane.adapt.engine.preference;

import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.mockito.Mockito.lenient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class PreferenceConfirmationTest {
  @Test
  void approvalRequiresTheSamePlayerActionAndItemAndIsConsumedOnce() {
    Adaptation<?> adaptation = mock(Adaptation.class);
    lenient().when(adaptation.getId()).thenReturn(Id.adapt("crafting/deconstruction"));
    PlayerRef first = mock(PlayerRef.class);
    PlayerRef second = mock(PlayerRef.class);
    when(first.uuid()).thenReturn(UUID.randomUUID());
    when(second.uuid()).thenReturn(UUID.randomUUID());
    when(first.isOnline()).thenReturn(true);
    when(second.isOnline()).thenReturn(true);
    ItemRef item = mock(ItemRef.class);
    ItemRef snapshot = mock(ItemRef.class);
    ItemRef changed = mock(ItemRef.class);
    when(item.copy()).thenReturn(snapshot);
    when(changed.copy()).thenReturn(changed);
    when(snapshot.isSimilar(snapshot)).thenReturn(true);
    when(changed.isSimilar(changed)).thenReturn(true);
    try (MockedStatic<Tasks> scheduler = SchedulerForwarding.mockTasks();
         MockedStatic<AdaptLanguage> language = mockStatic(AdaptLanguage.class)) {
      scheduler.when(() -> Tasks.isOwnedByCurrentRegion(first)).thenReturn(true);
      scheduler.when(() -> Tasks.isOwnedByCurrentRegion(second)).thenReturn(true);
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
