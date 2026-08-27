package art.arcane.adapt.content.adaptation.agility;

import io.papermc.paper.event.entity.EntityInsideBlockEvent;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgilityFeatherfootRuntimeTest {
  private static AgilityFeatherfoot.SurfaceRules rules(AgilityFeatherfoot.Config config) {
    config.normalizeForPersistence();
    return AgilityFeatherfoot.SurfaceRules.build(config);
  }

  @Test
  void everyAdvertisedSurfaceUsesItsProgressiveUnlockLevel() {
    AgilityFeatherfoot.SurfaceRules rules = rules(new AgilityFeatherfoot.Config());

    assertThat(AgilityFeatherfoot.minimumLevelForSurface(Material.FARMLAND, false, rules)).isEqualTo(1);
    assertThat(AgilityFeatherfoot.minimumLevelForSurface(Material.STONE_PRESSURE_PLATE, true, rules)).isEqualTo(2);
    assertThat(AgilityFeatherfoot.minimumLevelForSurface(Material.SWEET_BERRY_BUSH, false, rules)).isEqualTo(3);
    assertThat(AgilityFeatherfoot.minimumLevelForSurface(Material.POWDER_SNOW, false, rules)).isEqualTo(4);
    assertThat(AgilityFeatherfoot.minimumLevelForSurface(Material.COBWEB, false, rules)).isEqualTo(-1);
  }

  @Test
  void surfaceProtectionRequiresSprintIntentAndItsUnlockLevel() {
    AgilityFeatherfoot.SurfaceRules rules = rules(new AgilityFeatherfoot.Config());
    int berryBush = AgilityFeatherfoot.minimumLevelForSurface(Material.SWEET_BERRY_BUSH, false, rules);
    int powderSnow = AgilityFeatherfoot.minimumLevelForSurface(Material.POWDER_SNOW, false, rules);

    assertThat(AgilityFeatherfoot.ignoresSurface(1, true, 1, rules)).isTrue();
    assertThat(AgilityFeatherfoot.ignoresSurface(berryBush, true, 3, rules)).isTrue();
    assertThat(AgilityFeatherfoot.ignoresSurface(berryBush, false, 4, rules)).isFalse();
    assertThat(AgilityFeatherfoot.ignoresSurface(powderSnow, true, 3, rules)).isFalse();
    assertThat(AgilityFeatherfoot.ignoresSurface(-1, true, 4, rules)).isFalse();
  }

  @Test
  void walkingProtectionAppliesWhenSprintIsNoLongerRequired() {
    AgilityFeatherfoot.Config config = new AgilityFeatherfoot.Config();
    config.requireSprint = false;
    AgilityFeatherfoot.SurfaceRules rules = rules(config);

    assertThat(AgilityFeatherfoot.ignoresSurface(1, false, 1, rules)).isTrue();
  }

  @Test
  void aDisabledCategoryIsNeverProtectedAtAnyLevel() {
    AgilityFeatherfoot.Config config = new AgilityFeatherfoot.Config();
    config.pressurePlateEnabled = false;
    AgilityFeatherfoot.SurfaceRules rules = rules(config);

    assertThat(AgilityFeatherfoot.minimumLevelForSurface(Material.STONE_PRESSURE_PLATE, true, rules)).isEqualTo(-1);
    assertThat(AgilityFeatherfoot.minimumLevelForSurface(Material.FARMLAND, false, rules)).isEqualTo(1);
    assertThat(AgilityFeatherfoot.surfacesIgnored(4, rules)).isEqualTo(3);
  }

  @Test
  void normalizationPreservesTheConfiguredMaxLevel() {
    AgilityFeatherfoot.Config full = new AgilityFeatherfoot.Config();
    full.maxLevel = 7;
    full.normalizeForPersistence();

    AgilityFeatherfoot.Config trimmed = new AgilityFeatherfoot.Config();
    trimmed.powderSnowEnabled = false;
    trimmed.berryBushEnabled = false;
    trimmed.maxLevel = 2;
    trimmed.normalizeForPersistence();

    assertThat(full.maxLevel).isEqualTo(7);
    assertThat(trimmed.maxLevel).isEqualTo(2);
  }

  @Test
  void customBlockListsRetargetTheProtectionWithoutTouchingTheDefaults() {
    AgilityFeatherfoot.Config config = new AgilityFeatherfoot.Config();
    config.farmlandMaterials = new java.util.ArrayList<>(List.of("FARMLAND", "TURTLE_EGG"));
    config.pressurePlateUseVanillaTag = false;
    config.pressurePlateMaterials = new java.util.ArrayList<>(List.of("TRIPWIRE"));
    AgilityFeatherfoot.SurfaceRules rules = rules(config);

    assertThat(AgilityFeatherfoot.minimumLevelForSurface(Material.TURTLE_EGG, false, rules)).isEqualTo(1);
    assertThat(AgilityFeatherfoot.minimumLevelForSurface(Material.TRIPWIRE, false, rules)).isEqualTo(2);
    assertThat(AgilityFeatherfoot.minimumLevelForSurface(Material.STONE_PRESSURE_PLATE, true, rules)).isEqualTo(-1);
  }

  @Test
  void unparsableBlockIdsAreDroppedInsteadOfBreakingTheAdaptation() {
    AgilityFeatherfoot.Config config = new AgilityFeatherfoot.Config();
    config.berryBushMaterials = new java.util.ArrayList<>(List.of("SWEET_BERRY_BUSH", "NOT_A_BLOCK", " "));
    AgilityFeatherfoot.SurfaceRules rules = rules(config);

    assertThat(AgilityFeatherfoot.minimumLevelForSurface(Material.SWEET_BERRY_BUSH, false, rules)).isEqualTo(3);
    assertThat(rules.berryBushes().materials()).containsExactly(Material.SWEET_BERRY_BUSH);
  }

  @Test
  void surfaceCountRisesWithEachUnlockedProtection() {
    AgilityFeatherfoot.SurfaceRules rules = rules(new AgilityFeatherfoot.Config());

    assertThat(AgilityFeatherfoot.surfacesIgnored(0, rules)).isZero();
    assertThat(AgilityFeatherfoot.surfacesIgnored(1, rules)).isEqualTo(1);
    assertThat(AgilityFeatherfoot.surfacesIgnored(4, rules)).isEqualTo(4);
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
