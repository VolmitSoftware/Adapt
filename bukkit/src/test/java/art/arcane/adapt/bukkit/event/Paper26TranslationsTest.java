package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.entity.EntityLungeEvent;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class Paper26TranslationsTest {
  private final List<Throwable> failures = new ArrayList<>();
  private final AdaptEventBus bus = new AdaptEventBus((owner, event, error) -> failures.add(error));
  private GapRefs refs;

  @BeforeEach
  void setUp() {
    refs = new GapRefs();
  }

  @AfterEach
  void tearDown() {
    refs.close();
    assertThat(failures).isEmpty();
  }

  @Test
  void lungePowerIsWrittenBack() {
    Player player = mock(Player.class);
    io.papermc.paper.event.entity.EntityLungeEvent event = new io.papermc.paper.event.entity.EntityLungeEvent(player, 2);
    bus.register(this, EntityLungeEvent.class, EventPriority.HIGHEST, core -> core.setPower(core.power() + 3));

    Paper26Translations.LUNGE.bridge(event, core -> bus.post(core, EventPriority.HIGHEST));

    assertThat(event.getLungePower()).isEqualTo(5);
  }
}
