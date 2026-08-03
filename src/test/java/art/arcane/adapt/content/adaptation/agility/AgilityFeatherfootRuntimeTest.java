package art.arcane.adapt.content.adaptation.agility;

import io.papermc.paper.event.entity.EntityInsideBlockEvent;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AgilityFeatherfootRuntimeTest {
  @Test
  void everyAdvertisedSurfaceUsesItsProgressiveUnlockLevel() {
    AgilityFeatherfoot.Config config = new AgilityFeatherfoot.Config();

    assertThat(AgilityFeatherfoot.minimumLevelForSurface(Material.FARMLAND, false, config)).isEqualTo(1);
    assertThat(AgilityFeatherfoot.minimumLevelForSurface(Material.STONE_PRESSURE_PLATE, true, config)).isEqualTo(2);
    assertThat(AgilityFeatherfoot.minimumLevelForSurface(Material.SWEET_BERRY_BUSH, false, config)).isEqualTo(3);
    assertThat(AgilityFeatherfoot.minimumLevelForSurface(Material.POWDER_SNOW, false, config)).isEqualTo(4);
    assertThat(AgilityFeatherfoot.minimumLevelForSurface(Material.COBWEB, false, config)).isEqualTo(-1);
  }

  @Test
  void surfaceProtectionRequiresSprintIntentAndItsUnlockLevel() {
    AgilityFeatherfoot.Config config = new AgilityFeatherfoot.Config();

    assertThat(AgilityFeatherfoot.ignoresSurface(Material.FARMLAND, false, true, 1, config)).isTrue();
    assertThat(AgilityFeatherfoot.ignoresSurface(Material.STONE_PRESSURE_PLATE, true, true, 2, config)).isTrue();
    assertThat(AgilityFeatherfoot.ignoresSurface(Material.SWEET_BERRY_BUSH, false, true, 3, config)).isTrue();
    assertThat(AgilityFeatherfoot.ignoresSurface(Material.POWDER_SNOW, false, true, 4, config)).isTrue();
    assertThat(AgilityFeatherfoot.ignoresSurface(Material.SWEET_BERRY_BUSH, false, false, 4, config)).isFalse();
    assertThat(AgilityFeatherfoot.ignoresSurface(Material.POWDER_SNOW, false, true, 3, config)).isFalse();
  }

  @Test
  void recentSprintLeaseBridgesCollisionTimeStateLoss() {
    assertThat(AgilityFeatherfoot.sprintIntentActive(true, 0L)).isTrue();
    assertThat(AgilityFeatherfoot.sprintIntentActive(false, 1L)).isTrue();
    assertThat(AgilityFeatherfoot.sprintIntentActive(false, 0L)).isFalse();
  }

  @Test
  void unusedFeatherfootDoesNotRetainSprintInputState() {
    assertThat(AgilityFeatherfoot.shouldTrackSprintIntent(true, 0)).isFalse();
    assertThat(AgilityFeatherfoot.shouldTrackSprintIntent(false, 4)).isFalse();
    assertThat(AgilityFeatherfoot.shouldTrackSprintIntent(true, 1)).isTrue();
  }

  @Test
  void berryBushInteractionIsInterceptedBeforeVanillaAppliesItsEffects() throws ReflectiveOperationException {
    Method handler = Class.forName("art.arcane.adapt.content.adaptation.agility.AgilityFeatherfoot$InsideBlockListener")
        .getDeclaredMethod("on", EntityInsideBlockEvent.class);
    EventHandler eventHandler = handler.getAnnotation(EventHandler.class);

    assertThat(eventHandler).isNotNull();
    assertThat(eventHandler.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(eventHandler.ignoreCancelled()).isTrue();
  }

  @Test
  void farmlandAndPressurePlateInteractionUsesTheModernPhysicalEvent() throws NoSuchMethodException {
    Method handler = AgilityFeatherfoot.class.getDeclaredMethod("on", PlayerInteractEvent.class);
    EventHandler eventHandler = handler.getAnnotation(EventHandler.class);

    assertThat(eventHandler).isNotNull();
    assertThat(eventHandler.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(eventHandler.ignoreCancelled()).isTrue();
  }
}
