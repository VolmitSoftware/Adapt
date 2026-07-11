package art.arcane.adapt.content.gui;

import art.arcane.adapt.api.mutation.MutationSnapshot;
import art.arcane.adapt.api.mutation.MutationState;
import art.arcane.adapt.api.mutation.MutationType;
import art.arcane.adapt.api.mutation.PlayerMutationData;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MutationGuiModelTest {
  @Test
  void everyCatalogMutationRemainsVisibleAcrossQualificationStates() {
    MutationType[] types = MutationType.values();
    MutationSnapshot snapshot = baseSnapshot();
    when(snapshot.state(any(MutationType.class))).thenReturn(MutationState.LOCKED);
    when(snapshot.state(types[0])).thenReturn(MutationState.EXPRESSED);
    when(snapshot.state(types[1])).thenReturn(MutationState.AVAILABLE);
    when(snapshot.state(types[2])).thenReturn(MutationState.DORMANT);
    when(snapshot.expressed()).thenReturn(Set.of(types[0]));
    when(snapshot.discovered()).thenReturn(Set.of(types[0], types[1]));
    when(snapshot.slotOneId()).thenReturn(types[0].id());
    when(snapshot.slotOneUnlocked()).thenReturn(true);

    MutationGuiModel.View view = MutationGuiModel.build(50, true, snapshot);

    assertThat(view.cards()).hasSize(types.length);
    assertThat(view.cards()).extracting(card -> card.type()).containsExactly(types);
    assertThat(view.cards().get(0).expressed()).isTrue();
    assertThat(view.cards().get(0).selectedSlot()).isEqualTo(1);
    assertThat(view.cards().get(0).discovered()).isTrue();
    assertThat(view.cards().get(2).discovered()).isFalse();
    assertThat(view.cards().get(1).selectable()).isTrue();
    assertThat(view.cards().get(2).selectable()).isFalse();
  }

  @Test
  void viewOnlyMenusNeverOfferSelectionOrClearing() {
    MutationType[] types = MutationType.values();
    MutationSnapshot snapshot = baseSnapshot();
    when(snapshot.state(any(MutationType.class))).thenReturn(MutationState.AVAILABLE);
    when(snapshot.slotOneId()).thenReturn(types[0].id());
    when(snapshot.slotOneUnlocked()).thenReturn(true);

    MutationGuiModel.View view = MutationGuiModel.build(25, false, snapshot);

    assertThat(view.bookshelfAuthorized()).isFalse();
    assertThat(view.cards()).allMatch(card -> !card.selectable());
    assertThat(view.slotOne().canClear()).isFalse();
  }

  @Test
  void unknownSelectedIdsStayVisibleInsteadOfBeingPrunedFromSlots() {
    MutationSnapshot snapshot = baseSnapshot();
    when(snapshot.state(any(MutationType.class))).thenReturn(MutationState.LOCKED);
    when(snapshot.slotOneId()).thenReturn("future-lineage");
    when(snapshot.discoveredIds()).thenReturn(Set.of("future-lineage"));
    when(snapshot.slotOneUnlocked()).thenReturn(true);

    MutationGuiModel.View view = MutationGuiModel.build(25, true, snapshot);

    assertThat(view.slotOne().mutationId()).isEqualTo("future-lineage");
    assertThat(view.slotOne().displayName()).isEqualTo("future-lineage");
    assertThat(view.slotOne().state()).isEqualTo(MutationState.DORMANT);
    assertThat(view.slotOne().canClear()).isTrue();
    assertThat(view.unavailableDiscoveries()).containsExactly("future-lineage");
  }

  @Test
  void perfectAndCooperativeStateComeDirectlyFromSnapshot() {
    MutationSnapshot snapshot = baseSnapshot();
    when(snapshot.state(any(MutationType.class))).thenReturn(MutationState.LOCKED);
    when(snapshot.perfect()).thenReturn(true);
    when(snapshot.cooperativeOptIn()).thenReturn(true);

    MutationGuiModel.View view = MutationGuiModel.build(200, false, snapshot);

    assertThat(view.playerLevel()).isEqualTo(200);
    assertThat(view.perfect()).isTrue();
    assertThat(view.cooperativeOptIn()).isTrue();
  }

  @Test
  void equipmentControlsRequireSelectionExpressionBookshelfAndBasePermission() {
    PlayerMutationData data = new PlayerMutationData();

    assertThat(MutationGui.equipmentControl(
        MutationType.TEMPERBOUND, false, true, true, true, data, 1_000L
    )).isNull();
    assertThat(MutationGui.equipmentControl(
        MutationType.TEMPERBOUND, true, false, true, true, data, 1_000L
    )).isNull();
    assertThat(MutationGui.equipmentControl(
        MutationType.TEMPERBOUND, true, true, false, true, data, 1_000L
    )).isNull();
    assertThat(MutationGui.equipmentControl(
        MutationType.TEMPERBOUND, true, true, true, false, data, 1_000L
    )).isNull();
  }

  @Test
  void temperboundControlChangesFromAttuneToConfirmedDissolve() {
    PlayerMutationData data = new PlayerMutationData();

    MutationGui.EquipmentControl attune = MutationGui.equipmentControl(
        MutationType.TEMPERBOUND, true, true, true, true, data, 1_000L
    );

    assertThat(attune.action()).isEqualTo(MutationGui.EquipmentAction.ATTUNE_TEMPERBOUND);

    data.setTemperboundBondId("armor-bond");
    MutationGui.EquipmentControl dissolve = MutationGui.equipmentControl(
        MutationType.TEMPERBOUND, true, true, true, true, data, 1_000L
    );

    assertThat(dissolve.action()).isEqualTo(MutationGui.EquipmentAction.DISSOLVE_TEMPERBOUND);
  }

  @Test
  void masterworkControlSurfacesReplacementCooldownAndExistingBond() {
    PlayerMutationData data = new PlayerMutationData();
    data.setMasterworkAbandonReadyAt(6_000L);

    MutationGui.EquipmentControl coolingDown = MutationGui.equipmentControl(
        MutationType.MASTERWORK_BOND, true, true, true, true, data, 1_000L
    );

    assertThat(coolingDown.action()).isEqualTo(MutationGui.EquipmentAction.BIND_MASTERWORK);
    assertThat(coolingDown.cooldownRemainingMillis()).isEqualTo(5_000L);

    data.setMasterworkItemId("masterwork-tool");
    MutationGui.EquipmentControl abandon = MutationGui.equipmentControl(
        MutationType.MASTERWORK_BOND, true, true, true, true, data, 1_000L
    );

    assertThat(abandon.action()).isEqualTo(MutationGui.EquipmentAction.ABANDON_MASTERWORK);
    assertThat(abandon.cooldownRemainingMillis()).isZero();
  }

  @Test
  void deepbloodIsTheOnlyOtherMutationWithAnEquipmentControl() {
    PlayerMutationData data = new PlayerMutationData();

    MutationGui.EquipmentControl deepblood = MutationGui.equipmentControl(
        MutationType.DEEPBLOOD, true, true, true, true, data, 1_000L
    );

    assertThat(deepblood.action()).isEqualTo(MutationGui.EquipmentAction.BIND_DEEPBLOOD);
    assertThat(MutationGui.equipmentControl(
        MutationType.GALE_LUNG, true, true, true, true, data, 1_000L
    )).isNull();
  }

  private MutationSnapshot baseSnapshot() {
    MutationSnapshot snapshot = mock(MutationSnapshot.class);
    when(snapshot.slotOneId()).thenReturn("");
    when(snapshot.slotTwoId()).thenReturn("");
    when(snapshot.expressed()).thenReturn(Set.of());
    when(snapshot.discovered()).thenReturn(Set.of());
    when(snapshot.discoveredIds()).thenReturn(Set.of());
    when(snapshot.reason(any(MutationType.class))).thenReturn("");
    return snapshot;
  }
}
