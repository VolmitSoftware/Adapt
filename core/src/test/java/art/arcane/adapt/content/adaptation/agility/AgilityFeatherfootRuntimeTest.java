package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityInsideBlockEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AgilityFeatherfootRuntimeTest {
  private static final Id FARMLAND = Id.minecraft("farmland");
  private static final Id STONE_PRESSURE_PLATE = Id.minecraft("stone_pressure_plate");
  private static final Id SWEET_BERRY_BUSH = Id.minecraft("sweet_berry_bush");
  private static final Id POWDER_SNOW = Id.minecraft("powder_snow");
  private static final Id COBWEB = Id.minecraft("cobweb");
  private static final Id TURTLE_EGG = Id.minecraft("turtle_egg");
  private static final Id TRIPWIRE = Id.minecraft("tripwire");
  private static final Set<Id> KNOWN_BLOCKS = Set.of(FARMLAND, STONE_PRESSURE_PLATE, SWEET_BERRY_BUSH, POWDER_SNOW, COBWEB,
      TURTLE_EGG, TRIPWIRE);

  private static AgilityFeatherfoot.SurfaceRules rules(AgilityFeatherfoot.Config config) {
    config.normalizeForPersistence();
    return AgilityFeatherfoot.SurfaceRules.build(config, KNOWN_BLOCKS::contains);
  }

  @Test
  void everyAdvertisedSurfaceUsesItsProgressiveUnlockLevel() {
    AgilityFeatherfoot.SurfaceRules rules = rules(new AgilityFeatherfoot.Config());

    assertThat(AgilityFeatherfoot.minimumLevelForSurface(FARMLAND, false, rules)).isEqualTo(1);
    assertThat(AgilityFeatherfoot.minimumLevelForSurface(STONE_PRESSURE_PLATE, true, rules)).isEqualTo(2);
    assertThat(AgilityFeatherfoot.minimumLevelForSurface(SWEET_BERRY_BUSH, false, rules)).isEqualTo(3);
    assertThat(AgilityFeatherfoot.minimumLevelForSurface(POWDER_SNOW, false, rules)).isEqualTo(4);
    assertThat(AgilityFeatherfoot.minimumLevelForSurface(COBWEB, false, rules)).isEqualTo(-1);
  }

  @Test
  void surfaceProtectionRequiresSprintIntentAndItsUnlockLevel() {
    AgilityFeatherfoot.SurfaceRules rules = rules(new AgilityFeatherfoot.Config());
    int berryBush = AgilityFeatherfoot.minimumLevelForSurface(SWEET_BERRY_BUSH, false, rules);
    int powderSnow = AgilityFeatherfoot.minimumLevelForSurface(POWDER_SNOW, false, rules);

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

    assertThat(AgilityFeatherfoot.minimumLevelForSurface(STONE_PRESSURE_PLATE, true, rules)).isEqualTo(-1);
    assertThat(AgilityFeatherfoot.minimumLevelForSurface(FARMLAND, false, rules)).isEqualTo(1);
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

    assertThat(AgilityFeatherfoot.minimumLevelForSurface(TURTLE_EGG, false, rules)).isEqualTo(1);
    assertThat(AgilityFeatherfoot.minimumLevelForSurface(TRIPWIRE, false, rules)).isEqualTo(2);
    assertThat(AgilityFeatherfoot.minimumLevelForSurface(STONE_PRESSURE_PLATE, true, rules)).isEqualTo(-1);
  }

  @Test
  void unparsableBlockIdsAreDroppedInsteadOfBreakingTheAdaptation() {
    AgilityFeatherfoot.Config config = new AgilityFeatherfoot.Config();
    config.berryBushMaterials = new java.util.ArrayList<>(List.of("SWEET_BERRY_BUSH", "NOT_A_BLOCK", " "));
    AgilityFeatherfoot.SurfaceRules rules = rules(config);

    assertThat(AgilityFeatherfoot.minimumLevelForSurface(SWEET_BERRY_BUSH, false, rules)).isEqualTo(3);
    assertThat(rules.berryBushes().materials()).containsExactly(SWEET_BERRY_BUSH);
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
    Method handler = AgilityFeatherfoot.class.getDeclaredMethod("on", EntityInsideBlockEvent.class);
    Handle eventHandler = handler.getAnnotation(Handle.class);

    assertThat(eventHandler).isNotNull();
    assertThat(eventHandler.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(eventHandler.ignoreCancelled()).isTrue();
  }

  @Test
  void farmlandAndPressurePlateInteractionUsesTheModernPhysicalEvent() throws NoSuchMethodException {
    Method handler = AgilityFeatherfoot.class.getDeclaredMethod("on", PlayerInteractEvent.class);
    Handle eventHandler = handler.getAnnotation(Handle.class);

    assertThat(eventHandler).isNotNull();
    assertThat(eventHandler.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(eventHandler.ignoreCancelled()).isTrue();
  }
}
