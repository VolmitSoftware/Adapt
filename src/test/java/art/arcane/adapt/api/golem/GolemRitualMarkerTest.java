package art.arcane.adapt.api.golem;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Snowman;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemRitualMarkerTest {
  private static final AttributeModifier BOUND = new AttributeModifier(GolemScale.MODIFIER_KEY, -0.5D, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY);
  private static final AttributeModifier STEP = new AttributeModifier(GolemScale.RITUAL_KEY, -0.3D, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY);

  private final List<AttributeModifier> modifiers = new ArrayList<>(List.of(BOUND, STEP));
  private final AttributeInstance scale = scale(modifiers);

  @Test
  void theMarkerStoresTheGolemsAwarenessUnderItsOwnKey() {
    Snowman golem = valid(Snowman.class);

    GolemRitualMarker.start(golem, false);
    GolemRitualMarker.start(golem, true);

    assertThat(GolemRitualMarker.KEY).isEqualTo(new NamespacedKey("adapt", "golem_ritual"));
    assertThat(GolemRitualMarker.has(golem)).isTrue();
    assertThat(GolemRitualMarker.wasAware(golem)).contains(false);
    GolemRitualMarker.finish(golem, scale);
  }

  @Test
  void recoveringALoadedGolemRestoresAwarenessClearsTheRitualScaleAndRemovesTheMarker() {
    Snowman golem = valid(Snowman.class);
    golem.getPersistentDataContainer().set(GolemRitualMarker.KEY, PersistentDataType.BYTE, (byte) 1);

    assertThat(GolemRitualMarker.recover(golem, scale)).isTrue();

    verify(golem).setAware(true);
    assertThat(modifiers).containsExactly(BOUND);
    assertThat(GolemRitualMarker.has(golem)).isFalse();
  }

  @Test
  void recoveryLeavesALiveRitualAloneUntilItFinishes() {
    Snowman golem = valid(Snowman.class);
    GolemRitualMarker.start(golem, true);

    assertThat(GolemRitualMarker.recover(golem, scale)).isFalse();
    verify(golem, never()).setAware(anyBoolean());
    assertThat(modifiers).contains(STEP);

    assertThat(GolemRitualMarker.finish(golem, scale)).isTrue();
    verify(golem).setAware(true);
    assertThat(modifiers).containsExactly(BOUND);
    assertThat(GolemRitualMarker.has(golem)).isFalse();
  }

  @Test
  void aBoundGolemKeepsItsAwarenessButLosesTheMarkerAndRitualScale() {
    IronGolem golem = valid(IronGolem.class);
    GolemState.create(new GolemState.Seed(UUID.randomUUID(), UUID.randomUUID(), GolemBody.IRON, GolemCoreType.BLANK,
        GolemState.Anchor.of(UUID.randomUUID(), 0, 64, 0))).write(golem);
    golem.getPersistentDataContainer().set(GolemRitualMarker.KEY, PersistentDataType.BYTE, (byte) 0);

    assertThat(GolemRitualMarker.recover(golem, scale)).isTrue();

    verify(golem, never()).setAware(anyBoolean());
    assertThat(modifiers).containsExactly(BOUND);
    assertThat(GolemRitualMarker.has(golem)).isFalse();
  }

  @Test
  void aGolemWithoutAMarkerIsUntouched() {
    Snowman golem = valid(Snowman.class);

    assertThat(GolemRitualMarker.recover(golem, scale)).isFalse();
    assertThat(GolemRitualMarker.finish(golem, scale)).isFalse();

    verify(golem, never()).setAware(anyBoolean());
    assertThat(modifiers).containsExactly(BOUND, STEP);
  }

  @Test
  void anUnloadedGolemIsOnlyForgottenSoItsMarkerSurvivesForTheNextLoad() {
    Snowman golem = GolemTestEntities.entity(Snowman.class);
    GolemRitualMarker.start(golem, true);

    assertThat(GolemRitualMarker.finish(golem, scale)).isFalse();

    verify(golem, never()).setAware(anyBoolean());
    assertThat(GolemRitualMarker.has(golem)).isTrue();
    assertThat(GolemRitualMarker.recover(golem, scale)).isTrue();
  }

  private static <T extends Mob> T valid(Class<T> type) {
    T golem = GolemTestEntities.entity(type);
    when(golem.isValid()).thenReturn(true);
    return golem;
  }

  private static AttributeInstance scale(List<AttributeModifier> modifiers) {
    AttributeInstance instance = mock(AttributeInstance.class);
    when(instance.getModifiers()).thenAnswer(invocation -> List.copyOf(modifiers));
    doAnswer(invocation -> modifiers.remove(invocation.<AttributeModifier>getArgument(0))).when(instance).removeModifier(any(AttributeModifier.class));
    return instance;
  }
}
