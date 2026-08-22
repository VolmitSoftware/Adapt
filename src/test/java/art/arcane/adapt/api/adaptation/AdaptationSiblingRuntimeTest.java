package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.api.skill.Skill;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdaptationSiblingRuntimeTest {
  @Test
  void siblingSynergiesUseTheRegisteredAdaptationsRuntimeGate() {
    TestAdaptation source = new TestAdaptation();
    Skill<?> skill = mock(Skill.class);
    Adaptation<?> sibling = mock(Adaptation.class);
    Player player = mock(Player.class);
    Location location = mock(Location.class);
    KList<Adaptation<?>> adaptations = new KList<>();
    adaptations.add(sibling);

    source.setSkill(skill);
    when(skill.getAdaptations()).thenReturn(adaptations);
    when(sibling.getName()).thenReturn("drop-to-inventory");
    when(sibling.getActiveLevel(player)).thenReturn(3);
    when(sibling.getActiveBlockBreakLevel(player, location)).thenReturn(2);

    assertThat(source.getActiveSiblingLevel(player, "drop-to-inventory")).isEqualTo(3);
    assertThat(source.getActiveSiblingBlockBreakLevel(player, "drop-to-inventory", location)).isEqualTo(2);
    assertThat(source.getActiveSiblingLevel(player, "missing")).isZero();
  }

  private static final class TestAdaptation extends SimpleAdaptation<AdaptationConfig> {
    private TestAdaptation() {
      super("sibling-runtime-test");
      registerConfiguration(AdaptationConfig.class);
    }

    @Override
    public void addStats(int level, Element element) {
    }
  }
}
