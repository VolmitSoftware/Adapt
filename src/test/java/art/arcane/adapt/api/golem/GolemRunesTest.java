package art.arcane.adapt.api.golem;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptTestBase;
import org.bukkit.DyeColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class GolemRunesTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000301");

  private GolemRunes.Augments registered;

  @AfterEach
  void tearDown() {
    if (registered != null) {
      GolemRunes.unregister(registered);
    }
  }

  @Test
  void withoutRunicAugmentsEveryGolemHasNoRuneEffectsAndNoSlots() {
    GolemHandle handle = handle(ChannelMask.of(DyeColor.WHITE, DyeColor.RED));

    assertThat(GolemRunes.effects(handle)).isEqualTo(GolemRuneEffects.NONE);
    assertThat(GolemRunes.layout(GolemBody.IRON, OWNER, false)).isEqualTo(GolemRuneLayout.NONE);
    assertThat(GolemRunes.channels(handle)).isEqualTo(ChannelMask.of(DyeColor.WHITE));
  }

  @Test
  void theRegisteredAugmentsDecideEffectsLayoutsAndChannels() {
    GolemRuneLayout iron = new GolemRuneLayout(3, List.of(1, 1, 1, 3, 5), 1, List.of(1, 3, 5), Set.of());
    register(new FixedAugments(new GolemRuneEffects(4D, 1, 0.5D, 0.05D), iron));
    GolemHandle handle = handle(ChannelMask.of(DyeColor.WHITE, DyeColor.LIME, DyeColor.RED));

    assertThat(GolemRunes.effects(handle).radiusBonus()).isEqualTo(4D);
    assertThat(GolemRunes.layout(GolemBody.IRON, OWNER, false)).isSameAs(iron);
    assertThat(GolemRunes.channels(handle)).isEqualTo(ChannelMask.of(DyeColor.WHITE, DyeColor.LIME));
  }

  @Test
  void onlyTheRegisteredAugmentsCanUnregisterThemselves() {
    GolemRuneLayout iron = new GolemRuneLayout(3, List.of(1, 1, 1, 3, 5), 1, List.of(1, 3, 5), Set.of());
    FixedAugments first = new FixedAugments(GolemRuneEffects.NONE, iron);
    register(first);

    assertThat(GolemRunes.unregister(new FixedAugments(GolemRuneEffects.NONE, GolemRuneLayout.NONE))).isFalse();
    assertThat(GolemRunes.layout(GolemBody.IRON, OWNER, false)).isSameAs(iron);
    assertThat(GolemRunes.unregister(first)).isTrue();
    assertThat(GolemRunes.layout(GolemBody.IRON, OWNER, false)).isEqualTo(GolemRuneLayout.NONE);
  }

  @Test
  void failingAugmentsAreLoggedOnceAndTreatedAsInert() {
    register(new FixedAugments(null, GolemRuneLayout.NONE) {
      @Override
      public GolemRuneEffects effects(GolemHandle handle) {
        throw new IllegalStateException("broken rune table");
      }
    });
    GolemHandle handle = handle(ChannelMask.NONE);

    try (MockedStatic<Adapt> logs = mockStatic(Adapt.class)) {
      assertThat(GolemRunes.effects(handle)).isEqualTo(GolemRuneEffects.NONE);
      assertThat(GolemRunes.effects(handle)).isEqualTo(GolemRuneEffects.NONE);

      logs.verify(() -> Adapt.warn(anyString(), any(IllegalStateException.class)), times(1));
    }
  }

  private void register(GolemRunes.Augments augments) {
    registered = augments;
    GolemRunes.register(augments);
  }

  private static GolemHandle handle(int channels) {
    GolemState state = mock(GolemState.class);
    lenient().when(state.channels()).thenReturn(channels);
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.id()).thenReturn(UUID.randomUUID());
    lenient().when(handle.owner()).thenReturn(OWNER);
    lenient().when(handle.body()).thenReturn(GolemBody.IRON);
    lenient().when(handle.state()).thenReturn(state);
    return handle;
  }

  private static class FixedAugments implements GolemRunes.Augments {
    private final GolemRuneEffects effects;
    private final GolemRuneLayout layout;

    private FixedAugments(GolemRuneEffects effects, GolemRuneLayout layout) {
      this.effects = effects;
      this.layout = layout;
    }

    @Override
    public List<String> describe(GolemRune rune, GolemMastery.Perks perks) {
      return List.of();
    }

    @Override
    public GolemRuneLayout layout(GolemBody body, UUID owner, boolean masterwork) {
      return layout;
    }

    @Override
    public GolemRuneEffects effects(GolemHandle handle) {
      return effects;
    }
  }
}
