package art.arcane.adapt.api.item;

import art.arcane.adapt.api.TestStubs;
import art.arcane.adapt.api.TestHost;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.internal.EngineBridge;
import art.arcane.adapt.api.internal.ItemRuntime;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomItemTest {
  private static final Id STONE = Id.of("alchemist", "philosophers_stone");
  private static final Id AMETHYST = Id.minecraft("amethyst_shard");

  private final ItemRef marked = item(AMETHYST, false);
  private final ItemRef plain = item(AMETHYST, false);
  private final ItemRef dormant = item(Id.minecraft("carved_pumpkin"), false);
  private EngineBridge bridge;

  @BeforeEach
  void start() {
    ItemRuntime items = TestStubs.stub(ItemRuntime.class, Map.of("is", (Object[] args) -> args[1] == marked));
    bridge = TestStubs.stub(EngineBridge.class, Map.of("items", (Object[] args) -> items));
    Engine.start(TestHost.key(), bridge);
  }

  @AfterEach
  void stop() {
    Engine.stop(TestHost.key(), bridge);
  }

  @Test
  void fixedUsesApplyToEveryStack() {
    CustomItem.Definition<CustomItem.Plain> stone = CustomItem.builder(STONE, AMETHYST)
        .allow(VanillaUse.USE, VanillaUse.TRADE).build();

    assertThat(stone.allowedUses(marked)).containsExactlyInAnyOrder(VanillaUse.USE, VanillaUse.TRADE);
    assertThat(stone.allows(marked, VanillaUse.PLACE)).isFalse();
  }

  @Test
  void perStackUsesFollowTheStack() {
    CustomItem.Definition<CustomItem.Plain> golem = CustomItem.builder(STONE, AMETHYST)
        .allowPerStack((ItemRef stack) -> stack == dormant ? Set.of(VanillaUse.PLACE, VanillaUse.TRADE)
            : Set.of(VanillaUse.TRADE))
        .build();

    assertThat(golem.allows(dormant, VanillaUse.PLACE)).isTrue();
    assertThat(golem.allows(marked, VanillaUse.PLACE)).isFalse();
  }

  @Test
  void fixedAndPerStackUsesExcludeEachOther() {
    assertThatThrownBy(() -> CustomItem.builder(STONE, AMETHYST).allow(VanillaUse.USE)
        .allowPerStack((ItemRef stack) -> Set.of())).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> CustomItem.builder(STONE, AMETHYST).allowPerStack((ItemRef stack) -> Set.of())
        .allow(VanillaUse.USE)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aRecognitionRuleAddsStacksToThoseCarryingTheId() {
    CustomItem.Definition<CustomItem.Plain> marker = CustomItem.builder(STONE, AMETHYST).build();
    CustomItem.Definition<CustomItem.Plain> ruled = CustomItem.builder(STONE, AMETHYST)
        .recognisedBy((ItemRef stack) -> stack == dormant).build();

    assertThat(marker.is(marked)).isTrue();
    assertThat(marker.is(dormant)).isFalse();
    assertThat(ruled.is(marked)).isTrue();
    assertThat(ruled.is(dormant)).isTrue();
    assertThat(ruled.is(plain)).isFalse();
    assertThat(ruled.is(null)).isFalse();
    assertThat(ruled.is(item(AMETHYST, true))).isFalse();
  }

  @Test
  void recipesOfTheItemsNamespaceConsumeItUnlessARuleIsGiven() {
    CustomItem.Definition<CustomItem.Plain> stone = CustomItem.builder(STONE, AMETHYST).build();
    CustomItem.Definition<CustomItem.Plain> tool = CustomItem.builder(STONE, AMETHYST)
        .consumableBy((Id recipe) -> recipe.path().startsWith("tool/")).build();

    assertThat(stone.consumableBy(Id.of("alchemist", "elixir"))).isTrue();
    assertThat(stone.consumableBy(Id.minecraft("amethyst_block"))).isFalse();
    assertThat(stone.consumableBy(null)).isFalse();
    assertThat(tool.consumableBy(Id.of("alchemist", "elixir"))).isFalse();
    assertThat(tool.consumableBy(Id.minecraft("tool/anything"))).isTrue();
  }

  private static ItemRef item(Id type, boolean empty) {
    return TestStubs.stub(ItemRef.class, Map.of("type", (Object[] args) -> type, "isEmpty", (Object[] args) -> empty));
  }
}
