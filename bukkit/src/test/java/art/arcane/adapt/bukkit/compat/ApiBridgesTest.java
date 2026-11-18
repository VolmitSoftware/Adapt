package art.arcane.adapt.bukkit.compat;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.ability.AbilityCostProvider;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import art.arcane.adapt.api.ability.internal.AbilityApiRuntime;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.potion.AdaptPotionRegistry;
import art.arcane.adapt.api.potion.BrewingRecipe;
import art.arcane.adapt.api.potion.BrewingTask;
import art.arcane.adapt.api.potion.PotionBuilder;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.recipe.MaterialChar;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.XpAward;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeHost;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiBridgesTest extends AdaptTestBase {
  @AfterEach
  void removeAbilityRuntime() {
    AbilityBridge.uninstall();
    AdaptPotionRegistry.reset();
  }

  @Test
  void potionBuilderBridgesKeepThePrePortColourNameLoreAndBaseItem() {
    PotionBuilder builder = PotionBuilder.of(PotionBuilder.Type.REGULAR);
    ItemStack base = mock(ItemStack.class);
    Component name = Component.text("Elixir", NamedTextColor.GOLD);

    PotionBuilderBridge.setColor(builder, Color.fromRGB(1, 2, 3));
    PotionBuilderBridge.setName(builder, name);
    PotionBuilderBridge.addLore(builder, Component.text("first"));
    PotionBuilderBridge.addLore(builder, Component.text("second"));
    PotionBuilderBridge.setBaseItem(builder, base);

    assertThat(PotionBuilderBridge.getColor(builder)).isEqualTo(Color.fromRGB(1, 2, 3));
    assertThat(PotionBuilderBridge.getName(builder)).isSameAs(name);
    assertThat(builder.getName()).isEqualTo("Elixir");
    assertThat(PotionBuilderBridge.getLore(builder)).containsExactly(Component.text("first"), Component.text("second"));
    assertThat(PotionBuilderBridge.getBaseItem(builder)).isSameAs(base);

    PotionBuilderBridge.setColor(builder, null);
    PotionBuilderBridge.setLore(builder, null);
    assertThat(PotionBuilderBridge.getColor(builder)).isNull();
    assertThat(PotionBuilderBridge.getLore(builder)).isNull();
  }

  @Test
  void aStringNameReadsBackAsTheNonItalicComponentThePrePortBuilderMade() {
    PotionBuilder builder = PotionBuilder.of(PotionBuilder.Type.SPLASH);
    PotionBuilderBridge.setName(builder, Component.text("Old"));
    builder.setName("Plain");

    assertThat(PotionBuilderBridge.getName(builder))
        .isEqualTo(Component.text("Plain").decoration(TextDecoration.ITALIC, false));
    assertThat(PotionBuilderBridge.getName(PotionBuilder.of(PotionBuilder.Type.SPLASH))).isNull();
    assertThat(PotionBuilderBridge.getLore(PotionBuilder.of(PotionBuilder.Type.SPLASH))).isNull();
  }

  @Test
  void potionTypesMapToTheirBukkitMaterials() {
    assertThat(PotionBuilderBridge.material(PotionBuilder.Type.REGULAR)).isEqualTo(Material.POTION);
    assertThat(PotionBuilderBridge.material(PotionBuilder.Type.SPLASH)).isEqualTo(Material.SPLASH_POTION);
    assertThat(PotionBuilderBridge.material(PotionBuilder.Type.LINGERING)).isEqualTo(Material.LINGERING_POTION);
  }

  @Test
  void editingANonPotionStackFailsLikeThePrePortBuilder() {
    ItemStack stone = mock(ItemStack.class);
    when(stone.getType()).thenReturn(Material.STONE);

    assertThatThrownBy(() -> PotionBuilderBridge.of(stone))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid potion type!");
  }

  @Test
  void brewingRecipeBridgesStoreAndReturnTheSameBukkitObjects() {
    ItemStack base = mock(ItemStack.class);
    ItemStack result = mock(ItemStack.class);
    BrewingRecipe.BrewingRecipeBuilder builder = BrewingRecipe.builder().id("glow").brewingTime(100).fuelCost(1);
    BrewingBridge.ingredient(builder, Material.GLOWSTONE_DUST);
    BrewingBridge.basePotion(builder, base);
    BrewingBridge.result(builder, result);
    BrewingRecipe recipe = builder.build();

    assertThat(recipe.getIngredient()).isEqualTo(Id.minecraft("glowstone_dust"));
    assertThat(BrewingBridge.ingredient(recipe)).isEqualTo(Material.GLOWSTONE_DUST);
    assertThat(BrewingBridge.basePotion(recipe)).isSameAs(base);
    assertThat(BrewingBridge.result(recipe)).isSameAs(result);

    BrewingRecipe empty = BrewingBridge.result(BrewingBridge.basePotion(
        BrewingBridge.ingredient(BrewingRecipe.builder().id("empty"), null), null), null).build();
    assertThat(empty.getIngredient()).isNull();
    assertThat(BrewingBridge.ingredient(empty)).isNull();
    assertThat(BrewingBridge.basePotion(empty)).isNull();
    assertThat(BrewingBridge.result(empty)).isNull();
    assertThat(BrewingBridge.isValid(recipe, null)).isFalse();
    assertThat(BrewingTask.isValid(recipe, null)).isFalse();
  }

  @Test
  void potionRegistryBridgesIgnoreMissingPlayersAndTypesLikeThePrePortRegistry() {
    UUID player = UUID.randomUUID();

    BrewingBridge.record(player, null);

    assertThat(AdaptPotionRegistry.applied(player)).isEmpty();
    assertThat(BrewingBridge.strip(null)).isZero();
    BrewingBridge.retainActive(null);
  }

  @Test
  void xpAwardKeepsItsValidationAndANullLocation() {
    XpAward award = XpAwardBridge.create("quest", 5, null, true, XpAward.Kind.GAMEPLAY);

    assertThat(award.source()).isEqualTo("quest");
    assertThat(award.amount()).isEqualTo(5D);
    assertThat(award.location()).isNull();
    assertThat(XpAwardBridge.location(award)).isNull();
    assertThatThrownBy(() -> XpAwardBridge.create("quest", 0, null, true, XpAward.Kind.GAMEPLAY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void materialCharKeepsTheRecipeChoiceItWasGiven() {
    RecipeChoice.MaterialChoice stone = mock(RecipeChoice.MaterialChoice.class);
    when(stone.getChoices()).thenReturn(List.of(Material.STONE, Material.COBBLESTONE));

    MaterialChar character = new MaterialChar('s', stone);

    assertThat(character.getCharacter()).isEqualTo('s');
    assertThat(character.getChoice()).isSameAs(stone);
    assertThat(character.getIngredient())
        .isEqualTo(new Ingredient.AnyOf(Set.of(Id.minecraft("stone"), Id.minecraft("cobblestone"))));

    RecipeChoice.MaterialChoice dirt = mock(RecipeChoice.MaterialChoice.class);
    when(dirt.getChoices()).thenReturn(List.of(Material.DIRT));
    character.setChoice(dirt);
    assertThat(character.getChoice()).isSameAs(dirt);
    assertThat(character.getIngredient()).isEqualTo(Ingredient.of(Id.minecraft("dirt")));
  }

  @Test
  void materialCharWrapsAnyOtherRecipeChoiceAsAMatchingIngredient() {
    ItemStack example = mock(ItemStack.class);
    when(example.getType()).thenReturn(Material.DIAMOND);
    RecipeChoice custom = mock(RecipeChoice.class);
    when(custom.getItemStack()).thenReturn(example);
    when(custom.test(example)).thenReturn(true);

    MaterialChar character = new MaterialChar('d', custom);

    assertThat(character.getIngredient()).isInstanceOf(Ingredient.Matching.class);
    Ingredient.Matching matching = (Ingredient.Matching) character.getIngredient();
    assertThat(matching.base()).isEqualTo(Id.minecraft("diamond"));
    assertThat(matching.matches().test(BukkitRefs.item(example))).isTrue();
    assertThat(character.getChoice()).isSameAs(custom);
  }

  @Test
  void adaptRecipeRegisterAndUnregisterGoThroughTheRecipeHost() {
    RecipeHost recipes = core.getPlatform().recipes();
    ItemStack result = mock(ItemStack.class);
    when(result.getType()).thenReturn(Material.TORCH);
    when(result.getAmount()).thenReturn(1);
    AdaptRecipe recipe = AdaptRecipe.smoker().key("addon-torch").result(result).ingredient(Material.STICK)
        .experience(1F).cookTime(100).build();

    recipe.register();
    recipe.unregister();

    ArgumentCaptor<RecipeSpec> spec = ArgumentCaptor.forClass(RecipeSpec.class);
    verify(recipes).register(spec.capture());
    assertThat(spec.getValue().key()).isEqualTo(Id.adapt("addon-torch"));
    verify(recipes).unregister(Id.adapt("addon-torch"));
  }

  @Test
  void requiredRecipeLookupsUseTheBukkitRecipeKey() {
    SkillRegistry registry = mock(SkillRegistry.class);
    Adaptation<?> adaptation = mock(Adaptation.class);
    ShapedRecipe keyed = mock(ShapedRecipe.class);
    when(keyed.getKey()).thenReturn(new NamespacedKey("adapt", "glow-torch"));
    when(registry.getRequiredAdaptation(Id.adapt("glow-torch"))).thenAnswer(ignored -> adaptation);
    when(registry.getRequiredRecipeLevel(Id.adapt("glow-torch"))).thenReturn(3);

    assertThat(WorldBridge.getRequiredAdaptation(registry, keyed)).isSameAs(adaptation);
    assertThat(WorldBridge.getRequiredRecipeLevel(registry, keyed)).isEqualTo(3);
    assertThat(WorldBridge.getRequiredAdaptation(registry, mock(Recipe.class))).isNull();
    assertThat(WorldBridge.getRequiredRecipeLevel(registry, mock(Recipe.class))).isZero();
  }

  @Test
  void adaptPlayerHandsBackTheBukkitPlayer() {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    PlayerRef ref = BukkitRefs.player(player);
    AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    when(adaptPlayer.getPlayer()).thenReturn(ref);

    assertThat(WorldBridge.player(adaptPlayer)).isSameAs(player);
  }

  @Test
  void abilityApiInstallStartsAndUninstallStopsTheRuntime() {
    ServicesManager services = mock(ServicesManager.class);
    when(services.getRegistrations(AbilityCostProvider.class)).thenReturn(List.of());
    when(services.getRegistrations(AbilityUsePolicy.class)).thenReturn(List.of());
    when(plugin.getLogger()).thenReturn(Logger.getLogger("ApiBridgesTest"));

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getServicesManager).thenReturn(services);
      bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
      AbilityBridge.install(plugin);
      assertThat(AbilityApiRuntime.instance()).isNotNull();
      AbilityBridge.uninstall();
    }
    assertThat(AbilityApiRuntime.instance()).isNull();
  }
}
