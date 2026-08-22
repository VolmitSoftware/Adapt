package art.arcane.adapt.api.mutation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerMutationDataTest {
  @Test
  void discoveryRetainsOrderAndUnknownIds() {
    PlayerMutationData data = new PlayerMutationData();

    assertThat(data.discover("gale-lung")).isTrue();
    assertThat(data.discover("future-disabled-id")).isTrue();
    assertThat(data.discover("gale-lung")).isFalse();
    assertThat(data.getDiscovered()).containsExactly("gale-lung", "future-disabled-id");
  }

  @Test
  void normalizationBoundsDurableStateWithoutPruningUnknownSelections() {
    PlayerMutationData data = new PlayerMutationData();
    data.setSlotOneId(" FUTURE_ID ");
    data.setSlotTwoId("GALE_LUNG");
    data.setDeepbloodIchor(Double.POSITIVE_INFINITY);
    data.setLivingLatticeRootCharge(999D);
    data.setTemperboundPieceIds(new ArrayList<>(java.util.List.of("a", "b", "c", "d", "e")));
    LinkedHashMap<String, Long> sigils = new LinkedHashMap<>();
    sigils.put("crafting", 1L);
    sigils.put("brewing", 2L);
    sigils.put("enchanting", 3L);
    sigils.put("extra", 4L);
    data.setFormulaSigils(sigils);

    data.normalize();

    assertThat(data.getSlotOneId()).isEqualTo("future-id");
    assertThat(data.getSlotTwoId()).isEqualTo("gale-lung");
    assertThat(data.getDeepbloodIchor()).isZero();
    assertThat(data.getLivingLatticeRootCharge()).isEqualTo(12D);
    assertThat(data.getTemperboundPieceIds()).containsExactly("a", "b", "c", "d");
    assertThat(data.getFormulaSigils()).containsOnlyKeys("crafting", "brewing", "enchanting");
  }

  @Test
  void mutationResetClearsOnlyMutationStateObject() {
    PlayerMutationData data = new PlayerMutationData();
    data.discover("deepblood");
    data.setSlotOneId("deepblood");
    data.setDeepbloodIchor(42D);
    data.setCooperativeOptIn(true);

    data.clearAll();

    assertThat(data.getDiscovered()).isEmpty();
    assertThat(data.getSlotOneId()).isEmpty();
    assertThat(data.getDeepbloodIchor()).isZero();
    assertThat(data.isCooperativeOptIn()).isFalse();
  }
}
