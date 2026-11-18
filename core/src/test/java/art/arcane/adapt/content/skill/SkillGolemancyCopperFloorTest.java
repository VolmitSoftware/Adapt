package art.arcane.adapt.content.skill;

import art.arcane.adapt.content.skill.golemancy.CopperCare;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillGolemancyCopperFloorTest {
  private SkillGolemancy skill;

  @BeforeEach
  void setUp() {
    skill = mock(SkillGolemancy.class, invocation -> switch (invocation.getMethod().getName()) {
      case "shouldReturnForWorld" -> true;
      case "isEnabled" -> true;
      default -> invocation.callRealMethod();
    });
  }

  @Test
  void aCopperGolemBuiltWithoutAnyAttributedBuilderStillRecordsItsOxidationFloor() {
    MobRef golem = mock(MobRef.class);
    DataHolder data = mock(DataHolder.class);
    when(golem.type()).thenReturn(CopperCare.COPPER_GOLEM);
    when(golem.data()).thenReturn(data);
    when(golem.weatheringStage()).thenReturn(2);

    skill.on(new CreatureSpawnEvent(golem, new Loc(mock(WorldRef.class), 0D, 64D, 0D), CreatureSpawnEvent.Reason.BUILD_COPPERGOLEM));

    verify(data).set(CopperCare.STAGE_FLOOR_KEY, 2);
  }
}
