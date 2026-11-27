package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.value.Id;
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
    assertThat(settings.bodyScale(GolemBody.BONE)).isEqualTo(0.49D);
  }

  @Test
  void modifierIsTargetMinusOnePerBody() {
    GolemSettings settings = GolemSettings.defaults();

    assertThat(GolemScale.modifierAmount(settings.bodyScale(GolemBody.FROST))).isCloseTo(-0.50D, offset(1e-9));
    assertThat(GolemScale.modifierAmount(settings.bodyScale(GolemBody.COPPER))).isCloseTo(0D, offset(1e-9));
    assertThat(GolemScale.modifierAmount(settings.bodyScale(GolemBody.IRON))).isCloseTo(-0.64D, offset(1e-9));
    assertThat(GolemScale.modifierAmount(settings.bodyScale(GolemBody.BONE))).isCloseTo(-0.51D, offset(1e-9));
  }

  @Test
  void aBoundSkeletonShrinksSmoothlyToFitInsideOneBlock() {
    double bone = GolemSettings.defaults().bodyScale(GolemBody.BONE);

    assertThat(1.99D * bone).isCloseTo(0.9751D, offset(1e-9)).isLessThan(1D);
    assertThat(0.6D * bone).isLessThan(1D);
    assertThat(GolemScale.stepAmount(bone, 0D)).isCloseTo(0D, offset(1e-9));
    assertThat(GolemScale.stepAmount(bone, 0.5D)).isCloseTo(-0.255D, offset(1e-9));
    assertThat(GolemScale.stepAmount(bone, 1D)).isCloseTo(-0.51D, offset(1e-9));
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
    List<AttributeMod> modifiers = new ArrayList<>();
    AttributeMod foreign = new AttributeMod(Id.of("other", "shrink"), -0.1D, AttributeOperation.ADD_VALUE);
    modifiers.add(foreign);
    GolemAttribute instance = trackedInstance(modifiers);

    GolemScale.setModifier(instance, -0.5D);
    GolemScale.setModifier(instance, -0.64D);

    assertThat(modifiers).hasSize(2).contains(foreign);
    AttributeMod ours = modifiers.stream().filter(modifier -> GolemScale.MODIFIER_KEY.equals(modifier.key())).findFirst().orElseThrow();
    assertThat(ours.amount()).isCloseTo(-0.64D, offset(1e-9));
    assertThat(ours.operation()).isEqualTo(AttributeOperation.ADD_VALUE);
    assertThat(GolemScale.MODIFIER_KEY.toString()).isEqualTo("adapt:golem_scale");
  }

  @Test
  void zeroAmountAndRestoreLeaveNoModifierBehind() {
    List<AttributeMod> modifiers = new ArrayList<>();
    GolemAttribute instance = trackedInstance(modifiers);

    GolemScale.setModifier(instance, -0.5D);
    GolemScale.setModifier(instance, 0D);
    assertThat(modifiers).isEmpty();

    GolemScale.setModifier(instance, -0.5D);
    GolemScale.clearModifier(instance);
    assertThat(modifiers).isEmpty();
  }

  @Test
  void persistentModifiersAreUsed() {
    GolemAttribute instance = mock(GolemAttribute.class);
    when(instance.modifiers()).thenReturn(List.of());

    GolemScale.setModifier(instance, -0.5D);

    ArgumentCaptor<AttributeMod> added = ArgumentCaptor.forClass(AttributeMod.class);
    verify(instance).add(added.capture());
    verify(instance, never()).addTransient(any());
    assertThat(added.getValue().amount()).isCloseTo(-0.5D, offset(1e-9));
  }

  @Test
  void ritualStepsUseATransientModifierUnderTheirOwnKeyAndLeaveTheBoundScaleAlone() {
    List<AttributeMod> modifiers = new ArrayList<>();
    List<AttributeMod> transientAdds = new ArrayList<>();
    GolemAttribute instance = trackedInstance(modifiers);
    doAnswer(invocation -> {
      transientAdds.add(invocation.getArgument(0));
      return modifiers.add(invocation.getArgument(0));
    }).when(instance).addTransient(any(AttributeMod.class));
    GolemScale.setModifier(instance, -0.5D);

    GolemScale.setStepModifier(instance, -0.1D);
    GolemScale.setStepModifier(instance, -0.25D);

    assertThat(GolemScale.RITUAL_KEY.toString()).isEqualTo("adapt:golem_ritual_scale");
    assertThat(modifiers).hasSize(2);
    assertThat(transientAdds).hasSize(2).allMatch(modifier -> GolemScale.RITUAL_KEY.equals(modifier.key()));
    AttributeMod step = modifiers.stream().filter(modifier -> GolemScale.RITUAL_KEY.equals(modifier.key())).findFirst().orElseThrow();
    assertThat(step.amount()).isCloseTo(-0.25D, offset(1e-9));

    GolemScale.clearStepModifier(instance);

    assertThat(modifiers).singleElement().satisfies(modifier -> assertThat(modifier.key()).isEqualTo(GolemScale.MODIFIER_KEY));
  }

  @Test
  void transitionStepsStartAtTheOldScaleAndFadeToTheNewOne() {
    assertThat(GolemScale.transitionStep(0.36D, 1D, 0D)).isCloseTo(-0.64D, offset(1e-9));
    assertThat(GolemScale.transitionStep(0.36D, 1D, 0.5D)).isCloseTo(-0.32D, offset(1e-9));
    assertThat(GolemScale.transitionStep(0.36D, 1D, 1D)).isCloseTo(0D, offset(1e-9));
    assertThat(GolemScale.transitionStep(1D, 0.36D, 0.25D)).isCloseTo(0.48D, offset(1e-9));
    assertThat(GolemScale.transitionStep(1D, 0.36D, 7D)).isCloseTo(0D, offset(1e-9));
    assertThat(GolemScale.transitionStep(1D, 0.36D, Double.NaN)).isCloseTo(0.64D, offset(1e-9));
    assertThat(GolemScale.transitionStep(0.36D, 99D, 0D)).isCloseTo(0.36D - GolemScale.MAX_SCALE, offset(1e-9));
  }

  @Test
  void transitionsSettleTheNewScaleAtOnceAndAnimateWithATransientStep() {
    List<AttributeMod> modifiers = new ArrayList<>();
    List<AttributeMod> persistentAdds = new ArrayList<>();
    GolemAttribute instance = trackedInstance(modifiers);
    doAnswer(invocation -> {
      persistentAdds.add(invocation.getArgument(0));
      return modifiers.add(invocation.getArgument(0));
    }).when(instance).add(any(AttributeMod.class));
    doAnswer(invocation -> modifiers.add(invocation.getArgument(0))).when(instance).addTransient(any(AttributeMod.class));
    GolemScale.setModifier(instance, -0.64D);

    GolemScale.stepTo(instance, 0.36D, 1D, 0D);
    assertThat(amount(modifiers, GolemScale.MODIFIER_KEY)).isNull();
    assertThat(amount(modifiers, GolemScale.RITUAL_KEY)).isCloseTo(-0.64D, offset(1e-9));

    GolemScale.stepTo(instance, 0.36D, 1D, 0.5D);
    assertThat(amount(modifiers, GolemScale.RITUAL_KEY)).isCloseTo(-0.32D, offset(1e-9));

    GolemScale.stepTo(instance, 0.36D, 1D, 1D);
    assertThat(modifiers).isEmpty();

    persistentAdds.clear();
    GolemScale.stepTo(instance, 1D, 0.36D, 0D);
    GolemScale.stepTo(instance, 1D, 0.36D, 0.5D);
    assertThat(amount(modifiers, GolemScale.MODIFIER_KEY)).isCloseTo(-0.64D, offset(1e-9));
    assertThat(amount(modifiers, GolemScale.RITUAL_KEY)).isCloseTo(0.32D, offset(1e-9));
    assertThat(persistentAdds).singleElement().satisfies(modifier -> assertThat(modifier.key()).isEqualTo(GolemScale.MODIFIER_KEY));

    GolemScale.stepTo(instance, 1D, 0.36D, 1D);
    assertThat(modifiers).singleElement().satisfies(modifier -> assertThat(modifier.amount()).isCloseTo(-0.64D, offset(1e-9)));
  }

  private static Double amount(List<AttributeMod> modifiers, Id key) {
    return modifiers.stream().filter(modifier -> key.equals(modifier.key())).map(AttributeMod::amount).findFirst().orElse(null);
  }

  private static GolemAttribute trackedInstance(List<AttributeMod> modifiers) {
    GolemAttribute instance = mock(GolemAttribute.class);
    when(instance.modifiers()).thenAnswer(invocation -> List.copyOf(modifiers));
    doAnswer(invocation -> modifiers.add(invocation.getArgument(0))).when(instance).add(any(AttributeMod.class));
    doAnswer(invocation -> modifiers.removeIf(modifier -> modifier.key().equals(invocation.<Id>getArgument(0)))).when(instance).remove(any(Id.class));
    return instance;
  }
}
