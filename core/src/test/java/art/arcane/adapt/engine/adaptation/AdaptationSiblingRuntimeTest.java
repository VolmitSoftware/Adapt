package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdaptationSiblingRuntimeTest {
  @Test
  void siblingSynergiesUseTheRegisteredAdaptationsRuntimeGate() {
    Adaptation<?> source = mock(Adaptation.class, CALLS_REAL_METHODS);
    Skill<?> skill = mock(Skill.class);
    Adaptation<?> sibling = mock(Adaptation.class);
    PlayerRef player = mock(PlayerRef.class);

    doReturn(skill).when(source).getSkill();
    doReturn(List.of(sibling)).when(skill).getAdaptations();
    lenient().when(sibling.getId()).thenReturn(Id.adapt("drop-to-inventory"));
    when(sibling.getActiveLevel(player)).thenReturn(3);

    assertThat(source.getActiveSiblingLevel(player, Id.adapt("drop-to-inventory"))).isEqualTo(3);
    assertThat(source.getActiveSiblingLevel(player, Id.adapt("missing"))).isZero();
  }
}
