package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.recipe.ExactIngredient;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class InfusionBukkitResolverTest extends AdaptTestBase {
  private final ItemStack heart = mock(ItemStack.class);
  private final ItemStack rune = mock(ItemStack.class);
  private final ItemStack golemHeart = mock(ItemStack.class);
  private final ItemStack plainHeart = mock(ItemStack.class);
  private MockedStatic<GolemItems> items;

  @BeforeEach
  void setUp() {
    lenient().when(golemHeart.getType()).thenReturn(Material.HEART_OF_THE_SEA);
    lenient().when(plainHeart.getType()).thenReturn(Material.HEART_OF_THE_SEA);
    items = mockStatic(GolemItems.class);
    items.when(() -> GolemItems.ingredient(argThat(id -> id != null && (id.getKey().startsWith("rune/") || id.getKey().equals("blank_rune")))))
        .thenReturn(new ExactIngredient(Material.DISC_FRAGMENT_5, item -> item == rune));
    items.when(() -> GolemItems.ingredient(argThat(id -> id != null && id.getKey().equals("crook"))))
        .thenReturn(new ExactIngredient(Material.IRON_HOE, item -> false));
    items.when(() -> GolemItems.isGolemItem(any())).thenAnswer(call -> call.getArgument(0) == golemHeart || call.getArgument(0) == rune);
    items.when(GolemItems::masterworkHeart).thenReturn(heart);
    items.when(() -> GolemItems.resolve(any(NamespacedKey.class))).thenReturn(rune);
  }

  @AfterEach
  void tearDown() {
    items.close();
  }

  @Test
  void everyDefaultResolvesAgainstTheGolemItemFactory() {
    List<String> warnings = new ArrayList<>();

    InfusionRecipes recipes = InfusionRecipes.parse(InfusionRecipe.defaults(), InfusionRecipes.bukkitResolver(any -> true), warnings::add);

    assertThat(warnings).isEmpty();
    assertThat(recipes.infusions()).hasSize(22);
  }

  @Test
  void materialSpecsRefuseGolemItemsOfTheSameBase() {
    InfusionIngredient material = InfusionRecipes.bukkitResolver(any -> true).material("heart_of_the_sea");

    assertThat(material.matches(plainHeart)).isTrue();
    assertThat(material.matches(golemHeart)).isFalse();
    assertThat(material.matches(null)).isFalse();
    assertThat(InfusionRecipes.bukkitResolver(any -> true).material("NOT_A_THING")).isNull();
    assertThat(InfusionRecipes.bukkitResolver(any -> true).material("AIR")).isNull();
  }

  @Test
  void materialsThatAreNotItemsAreRejected() {
    InfusionRecipes.Resolver resolver = InfusionRecipes.bukkitResolver(material -> material != Material.WATER);
    List<String> warnings = new ArrayList<>();

    assertThat(resolver.material("WATER")).isNull();
    assertThat(resolver.material("SUGAR")).isNotNull();
    InfusionRecipes.parse(Map.of("wet", new InfusionRecipe().result("golemancy:rune/tide/1").center("golemancy:blank_rune")
        .pedestals("WATER", "SUGAR", "SUGAR", "SUGAR").unlock(InfusionRecipe.RUNIC_AUGMENTS, 1)), resolver, warnings::add);
    assertThat(warnings).singleElement().asString().contains("wet").contains("WATER");
  }

  @Test
  void exactSpecsMatchByTheGolemItemPayload() {
    InfusionIngredient exact = InfusionRecipes.bukkitResolver(any -> true).exact(NamespacedKey.fromString("golemancy:rune/tide/2"));

    assertThat(exact.matches(rune)).isTrue();
    assertThat(exact.matches(plainHeart)).isFalse();
    assertThat(InfusionRecipes.bukkitResolver(any -> true).exact(NamespacedKey.fromString("golemancy:nothing"))).isNull();
  }

  @Test
  void onlyRunesAndTheMasterworkHeartAreInfusionResults() {
    InfusionRecipes.Resolver resolver = InfusionRecipes.bukkitResolver(any -> true);

    assertThat(resolver.result(NamespacedKey.fromString("golemancy:masterwork_heart")).get()).isSameAs(heart);
    assertThat(resolver.result(NamespacedKey.fromString("golemancy:rune/tide/2")).get()).isSameAs(rune);
    assertThat(resolver.result(NamespacedKey.fromString("golemancy:crook"))).isNull();
    assertThat(resolver.result(NamespacedKey.fromString("golemancy:blank_rune"))).isNull();
    assertThat(resolver.result(NamespacedKey.fromString("minecraft:diamond"))).isNull();
  }
}
