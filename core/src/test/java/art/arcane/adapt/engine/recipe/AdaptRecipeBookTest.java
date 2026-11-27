package art.arcane.adapt.engine.recipe;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.recipe.RecipeHost;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdaptRecipeBookTest {
  @AfterEach
  void clearCore() {
    TestCores.clear();
  }

  @Test
  void chalkRecipesUnlockAtTheirExactLevels() {
    Adaptation<?> chalk = mock(Adaptation.class);
    List<AdaptRecipeBook.Unlock> recipes = List.of(
        unlock("architect-chalk-straightedge", chalk, 1),
        unlock("architect-chalk-polyline", chalk, 2),
        unlock("architect-chalk-compass", chalk, 3),
        unlock("architect-chalk-arc-bow", chalk, 4)
    );

    AdaptRecipeBook.Plan levelOne = plan(recipes, chalk, 1);
    AdaptRecipeBook.Plan levelFour = plan(recipes, chalk, 4);

    assertThat(levelOne.discover()).containsExactly(key("architect-chalk-straightedge"));
    assertThat(levelOne.undiscover()).containsExactly(
        key("architect-chalk-polyline"),
        key("architect-chalk-compass"),
        key("architect-chalk-arc-bow")
    );
    assertThat(levelFour.discover()).containsExactly(
        key("architect-chalk-straightedge"),
        key("architect-chalk-polyline"),
        key("architect-chalk-compass"),
        key("architect-chalk-arc-bow")
    );
    assertThat(levelFour.undiscover()).isEmpty();
  }

  @Test
  void loweringLevelAndUnlearningRemoveRecipesThatAreNoLongerAvailable() {
    Adaptation<?> chalk = mock(Adaptation.class);
    List<AdaptRecipeBook.Unlock> recipes = List.of(
        unlock("architect-chalk-straightedge", chalk, 1),
        unlock("architect-chalk-polyline", chalk, 2),
        unlock("architect-chalk-compass", chalk, 3),
        unlock("architect-chalk-arc-bow", chalk, 4)
    );

    AdaptRecipeBook.Plan downgraded = plan(recipes, chalk, 2);
    AdaptRecipeBook.Plan unlearned = plan(recipes, chalk, 0);

    assertThat(downgraded.discover()).containsExactly(
        key("architect-chalk-straightedge"),
        key("architect-chalk-polyline")
    );
    assertThat(downgraded.undiscover()).containsExactly(
        key("architect-chalk-compass"),
        key("architect-chalk-arc-bow")
    );
    assertThat(unlearned.discover()).isEmpty();
    assertThat(unlearned.undiscover()).containsExactlyElementsOf(recipes.stream()
        .map(AdaptRecipeBook.Unlock::key)
        .toList());
  }

  @Test
  void phalanxNetheriteRecipeRequiresLevelTwo() {
    Adaptation<?> phalanx = mock(Adaptation.class);
    List<AdaptRecipeBook.Unlock> recipes = List.of(
        unlock("blocking-phalanx-field-shield", phalanx, 1),
        unlock("blocking-phalanx-netherite-shield", phalanx, 2)
    );

    AdaptRecipeBook.Plan levelOne = plan(recipes, phalanx, 1);

    assertThat(levelOne.discover()).containsExactly(key("blocking-phalanx-field-shield"));
    assertThat(levelOne.undiscover()).containsExactly(key("blocking-phalanx-netherite-shield"));
  }

  @Test
  void invalidRequiredLevelsAreNormalizedToOne() {
    Adaptation<?> adaptation = mock(Adaptation.class);

    AdaptRecipeBook.Unlock unlock = unlock("normalized", adaptation, 0);

    assertThat(unlock.requiredLevel()).isEqualTo(1);
    assertThat(plan(List.of(unlock), adaptation, 0).discover()).isEmpty();
    assertThat(plan(List.of(unlock), adaptation, 1).discover()).containsExactly(key("normalized"));
  }

  @Test
  void synchronizationUndiscoversThenDiscoversThroughTheRecipeHost() {
    AdaptCore core = TestCores.install();
    RecipeHost recipes = core.getPlatform().recipes();
    PlayerRef player = mock(PlayerRef.class);
    when(player.isOnline()).thenReturn(true);
    AdaptRecipeBook.Plan plan = new AdaptRecipeBook.Plan(
        List.of(key("discover")),
        List.of(key("remove"))
    );

    AdaptRecipeBook.synchronize(player, plan);

    InOrder order = inOrder(recipes);
    order.verify(recipes).undiscover(player, key("remove"));
    order.verify(recipes).discover(player, key("discover"));
  }

  @Test
  void offlinePlayersAreNotMutated() {
    AdaptCore core = TestCores.install();
    RecipeHost recipes = core.getPlatform().recipes();
    PlayerRef player = mock(PlayerRef.class);
    when(player.isOnline()).thenReturn(false);
    AdaptRecipeBook.Plan plan = new AdaptRecipeBook.Plan(
        List.of(key("discover")),
        List.of(key("remove"))
    );

    AdaptRecipeBook.synchronize(player, plan);

    verify(recipes, never()).discover(any(PlayerRef.class), any(Id.class));
    verify(recipes, never()).undiscover(any(PlayerRef.class), any(Id.class));
  }

  private AdaptRecipeBook.Plan plan(List<AdaptRecipeBook.Unlock> recipes,
                                    Adaptation<?> adaptation, int level) {
    Map<Adaptation<?>, Integer> levels = new IdentityHashMap<>();
    levels.put(adaptation, level);
    return AdaptRecipeBook.plan(recipes, current -> levels.getOrDefault(current, 0));
  }

  private AdaptRecipeBook.Unlock unlock(String value, Adaptation<?> adaptation, int requiredLevel) {
    return new AdaptRecipeBook.Unlock(key(value), adaptation, requiredLevel);
  }

  private Id key(String value) {
    return Id.of("adapt", value);
  }
}
