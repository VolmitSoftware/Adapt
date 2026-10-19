package art.arcane.adapt.content.skill;

import art.arcane.adapt.content.skill.golemancy.CopperCare;
import io.papermc.paper.world.WeatheringCopperState;
import org.bukkit.entity.CopperGolem;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
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
    CopperGolem golem = mock(CopperGolem.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    when(golem.getPersistentDataContainer()).thenReturn(data);
    when(golem.getWeatheringState()).thenReturn(WeatheringCopperState.WEATHERED);

    skill.on(new CreatureSpawnEvent(golem, SpawnReason.BUILD_COPPERGOLEM));

    verify(data).set(CopperCare.STAGE_FLOOR_KEY, PersistentDataType.INTEGER, 2);
  }
}
