package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.recipe.ExactIngredient;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class InfusionBukkitResolverTest extends AdaptTestBase {
  private static final Set<String> KNOWN_ITEMS = knownItems();
  private final ItemRef heart = mock(ItemRef.class);
  private final ItemRef rune = mock(ItemRef.class);
  private final ItemRef golemHeart = mock(ItemRef.class);
  private final ItemRef plainHeart = mock(ItemRef.class);
  private MockedStatic<GolemItems> items;

  @BeforeEach
  void setUp() {
    RegistryLookup registries = mock(RegistryLookup.class);
    lenient().when(core.getPlatform().host().registries()).thenReturn(registries);
    lenient().when(registries.exists(eq(RegistryKind.ITEM), any(Id.class))).thenAnswer(call -> KNOWN_ITEMS.contains(call.<Id>getArgument(1).path()));
    lenient().when(golemHeart.type()).thenReturn(Id.minecraft("heart_of_the_sea"));
    lenient().when(plainHeart.type()).thenReturn(Id.minecraft("heart_of_the_sea"));
    items = mockStatic(GolemItems.class);
    items.when(() -> GolemItems.ingredient(argThat(id -> id != null && (id.path().startsWith("rune/") || id.path().equals("blank_rune")))))
        .thenReturn(new ExactIngredient(Id.minecraft("guster_pottery_sherd"), item -> item == rune));
    items.when(() -> GolemItems.ingredient(argThat(id -> id != null && id.path().equals("crook"))))
        .thenReturn(new ExactIngredient(Id.minecraft("iron_hoe"), item -> false));
    items.when(() -> GolemItems.isGolemItem(any())).thenAnswer(call -> call.getArgument(0) == golemHeart || call.getArgument(0) == rune);
    items.when(GolemItems::masterworkHeart).thenReturn(heart);
    items.when(() -> GolemItems.resolve(any(Id.class))).thenReturn(rune);
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
    assertThat(recipes.match(plainHeart, List.of(plain(Id.minecraft("diamond_block")), plain(Id.minecraft("iron_block")), plain(Id.minecraft("copper_block")),
        plain(Id.minecraft("snow_block"))))).map(Infusion::id).contains("masterwork_heart");
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
    InfusionRecipes.Resolver resolver = InfusionRecipes.bukkitResolver(material -> !material.equals(Id.minecraft("water")));
    List<String> warnings = new ArrayList<>();

    assertThat(resolver.material("WATER")).isNull();
    assertThat(resolver.material("SUGAR")).isNotNull();
    InfusionRecipes.parse(Map.of("wet", new InfusionRecipe().result("golemancy:rune/tide/1").center("golemancy:blank_rune")
        .pedestals("WATER", "SUGAR", "SUGAR", "SUGAR").unlock(InfusionRecipe.RUNIC_AUGMENTS, 1)), resolver, warnings::add);
    assertThat(warnings).singleElement().asString().contains("wet").contains("WATER");
  }

  @Test
  void exactSpecsMatchByTheGolemItemPayload() {
    InfusionIngredient exact = InfusionRecipes.bukkitResolver(any -> true).exact(Id.parse("golemancy:rune/tide/2"));

    assertThat(exact.matches(rune)).isTrue();
    assertThat(exact.matches(plainHeart)).isFalse();
    assertThat(InfusionRecipes.bukkitResolver(any -> true).exact(Id.parse("golemancy:nothing"))).isNull();
  }

  @Test
  void onlyRunesAndTheMasterworkHeartAreInfusionResults() {
    InfusionRecipes.Resolver resolver = InfusionRecipes.bukkitResolver(any -> true);

    assertThat(resolver.result(Id.parse("golemancy:masterwork_heart")).get()).isSameAs(heart);
    assertThat(resolver.result(Id.parse("golemancy:rune/tide/2")).get()).isSameAs(rune);
    assertThat(resolver.result(Id.parse("golemancy:crook"))).isNull();
    assertThat(resolver.result(Id.parse("golemancy:blank_rune"))).isNull();
    assertThat(resolver.result(Id.parse("minecraft:diamond"))).isNull();
  }

  private static Set<String> knownItems() {
    Set<String> known = new HashSet<>(Set.of("air", "water", "sugar"));
    for (InfusionRecipe recipe : InfusionRecipe.defaults().values()) {
      List<String> names = new ArrayList<>(recipe.getPedestals());
      names.add(recipe.getCenter());
      for (String name : names) {
        if (name.indexOf(':') < 0) {
          known.add(name.toLowerCase(Locale.ROOT));
        }
      }
    }
    return known;
  }

  private static ItemRef plain(Id material) {
    ItemRef item = mock(ItemRef.class);
    lenient().when(item.type()).thenReturn(material);
    return item;
  }
}
