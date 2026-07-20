package art.arcane.adapt.content.adaptation.agility;

import io.papermc.paper.event.entity.EntityInsideBlockEvent;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AgilityFeatherfootRuntimeTest {
  @Test
  void berryBushProtectionRequiresTheSurfaceSprintAndUnlockLevel() {
    assertThat(AgilityFeatherfoot.ignoresBerryBush(Material.SWEET_BERRY_BUSH, true, 3, 3)).isTrue();
    assertThat(AgilityFeatherfoot.ignoresBerryBush(Material.SWEET_BERRY_BUSH, false, 3, 3)).isFalse();
    assertThat(AgilityFeatherfoot.ignoresBerryBush(Material.SWEET_BERRY_BUSH, true, 2, 3)).isFalse();
    assertThat(AgilityFeatherfoot.ignoresBerryBush(Material.COBWEB, true, 3, 3)).isFalse();
  }

  @Test
  void berryBushInteractionIsInterceptedBeforeVanillaAppliesItsEffects() throws NoSuchMethodException {
    Method handler = AgilityFeatherfoot.class.getDeclaredMethod("on", EntityInsideBlockEvent.class);
    EventHandler eventHandler = handler.getAnnotation(EventHandler.class);

    assertThat(eventHandler).isNotNull();
    assertThat(eventHandler.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(eventHandler.ignoreCancelled()).isTrue();
  }
}
