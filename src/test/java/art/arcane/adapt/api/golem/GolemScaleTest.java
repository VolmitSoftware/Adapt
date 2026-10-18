package art.arcane.adapt.api.golem;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemScaleTest {
  @Test
  void defaultBodyTargetsMatchThePlan() {
    GolemSettings settings = GolemSettings.defaults();

    assertThat(settings.bodyScale(GolemBody.FROST)).isEqualTo(0.50D);
    assertThat(settings.bodyScale(GolemBody.COPPER)).isEqualTo(1.00D);
    assertThat(settings.bodyScale(GolemBody.IRON)).isEqualTo(0.36D);
    assertThat(settings.bodyScale(GolemBody.BONE)).isEqualTo(1.00D);
  }

  @Test
  void modifierIsTargetMinusOnePerBody() {
    GolemSettings settings = GolemSettings.defaults();

    assertThat(GolemScale.modifierAmount(settings.bodyScale(GolemBody.FROST))).isCloseTo(-0.50D, offset(1e-9));
    assertThat(GolemScale.modifierAmount(settings.bodyScale(GolemBody.COPPER))).isCloseTo(0D, offset(1e-9));
    assertThat(GolemScale.modifierAmount(settings.bodyScale(GolemBody.IRON))).isCloseTo(-0.64D, offset(1e-9));
    assertThat(GolemScale.modifierAmount(settings.bodyScale(GolemBody.BONE))).isCloseTo(0D, offset(1e-9));
  }

  @Test
  void steppingInterpolatesFromVanillaToTheTarget() {
    assertThat(GolemScale.stepAmount(0.36D, 0D)).isCloseTo(0D, offset(1e-9));
    assertThat(GolemScale.stepAmount(0.36D, 0.5D)).isCloseTo(-0.32D, offset(1e-9));
    assertThat(GolemScale.stepAmount(0.36D, 1D)).isCloseTo(-0.64D, offset(1e-9));
    assertThat(GolemScale.stepAmount(0.36D, 2D)).isCloseTo(-0.64D, offset(1e-9));
    assertThat(GolemScale.stepAmount(0.36D, -1D)).isCloseTo(0D, offset(1e-9));
    assertThat(GolemScale.stepAmount(0.36D, Double.NaN)).isCloseTo(0D, offset(1e-9));
    assertThat(GolemScale.stepAmount(0.50D, 0.25D)).isCloseTo(-0.125D, offset(1e-9));
  }

  @Test
  void targetsClampToTheScaleAttributeRange() {
    assertThat(GolemScale.clampScale(0.01D)).isEqualTo(GolemScale.MIN_SCALE);
    assertThat(GolemScale.clampScale(99D)).isEqualTo(GolemScale.MAX_SCALE);
    assertThat(GolemScale.clampScale(Double.NaN)).isEqualTo(1D);
    assertThat(GolemScale.modifierAmount(0D)).isCloseTo(GolemScale.MIN_SCALE - 1D, offset(1e-9));
  }

  @Test
  void settingTheModifierReplacesOursAndKeepsOthers() {
    List<AttributeModifier> modifiers = new ArrayList<>();
    AttributeModifier foreign = new AttributeModifier(new NamespacedKey("other", "shrink"), -0.1D, AttributeModifier.Operation.ADD_NUMBER);
    modifiers.add(foreign);
    AttributeInstance instance = trackedInstance(modifiers);

    GolemScale.setModifier(instance, -0.5D);
    GolemScale.setModifier(instance, -0.64D);

    assertThat(modifiers).hasSize(2).contains(foreign);
    AttributeModifier ours = modifiers.stream().filter(modifier -> GolemScale.MODIFIER_KEY.equals(modifier.getKey())).findFirst().orElseThrow();
    assertThat(ours.getAmount()).isCloseTo(-0.64D, offset(1e-9));
    assertThat(ours.getOperation()).isEqualTo(AttributeModifier.Operation.ADD_NUMBER);
    assertThat(GolemScale.MODIFIER_KEY.toString()).isEqualTo("adapt:golem_scale");
  }

  @Test
  void zeroAmountAndRestoreLeaveNoModifierBehind() {
    List<AttributeModifier> modifiers = new ArrayList<>();
    AttributeInstance instance = trackedInstance(modifiers);

    GolemScale.setModifier(instance, -0.5D);
    GolemScale.setModifier(instance, 0D);
    assertThat(modifiers).isEmpty();

    GolemScale.setModifier(instance, -0.5D);
    GolemScale.clearModifier(instance);
    assertThat(modifiers).isEmpty();
  }

  @Test
  void persistentModifiersAreUsed() {
    AttributeInstance instance = mock(AttributeInstance.class);
    when(instance.getModifiers()).thenReturn(List.of());

    GolemScale.setModifier(instance, -0.5D);

    ArgumentCaptor<AttributeModifier> added = ArgumentCaptor.forClass(AttributeModifier.class);
    verify(instance).addModifier(added.capture());
    verify(instance, never()).addTransientModifier(any());
    assertThat(added.getValue().getAmount()).isCloseTo(-0.5D, offset(1e-9));
  }

  @Test
  void ritualStepsUseATransientModifierUnderTheirOwnKeyAndLeaveTheBoundScaleAlone() {
    List<AttributeModifier> modifiers = new ArrayList<>();
    List<AttributeModifier> transientAdds = new ArrayList<>();
    AttributeInstance instance = trackedInstance(modifiers);
    doAnswer(invocation -> {
      transientAdds.add(invocation.getArgument(0));
      return modifiers.add(invocation.getArgument(0));
    }).when(instance).addTransientModifier(any(AttributeModifier.class));
    GolemScale.setModifier(instance, -0.5D);

    GolemScale.setStepModifier(instance, -0.1D);
    GolemScale.setStepModifier(instance, -0.25D);

    assertThat(GolemScale.RITUAL_KEY.toString()).isEqualTo("adapt:golem_ritual_scale");
    assertThat(modifiers).hasSize(2);
    assertThat(transientAdds).hasSize(2).allMatch(modifier -> GolemScale.RITUAL_KEY.equals(modifier.getKey()));
    AttributeModifier step = modifiers.stream().filter(modifier -> GolemScale.RITUAL_KEY.equals(modifier.getKey())).findFirst().orElseThrow();
    assertThat(step.getAmount()).isCloseTo(-0.25D, offset(1e-9));

    GolemScale.clearStepModifier(instance);

    assertThat(modifiers).singleElement().satisfies(modifier -> assertThat(modifier.getKey()).isEqualTo(GolemScale.MODIFIER_KEY));
  }

  private static AttributeInstance trackedInstance(List<AttributeModifier> modifiers) {
    AttributeInstance instance = mock(AttributeInstance.class);
    when(instance.getModifiers()).thenAnswer(invocation -> List.copyOf(modifiers));
    doAnswer(invocation -> modifiers.add(invocation.getArgument(0))).when(instance).addModifier(any(AttributeModifier.class));
    doAnswer(invocation -> modifiers.remove(invocation.<AttributeModifier>getArgument(0))).when(instance).removeModifier(any(AttributeModifier.class));
    return instance;
  }
}
