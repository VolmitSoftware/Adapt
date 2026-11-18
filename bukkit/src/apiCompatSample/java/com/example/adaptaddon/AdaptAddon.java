package com.example.adaptaddon;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.ability.AbilityCostProvider;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import art.arcane.adapt.api.adaptation.AbilityApiBridge;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.potion.AdaptPotionRegistry;
import art.arcane.adapt.api.potion.BrewingManager;
import art.arcane.adapt.api.potion.BrewingRecipe;
import art.arcane.adapt.api.potion.BrewingTask;
import art.arcane.adapt.api.potion.PotionBuilder;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.api.xp.XpAward;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

/**
 * A third-party plugin written against the pre-port Adapt API. It is compiled against that API only and run against
 * the current plugin jar by ApiCompatibilityLinkTest; it is never shipped.
 */
public final class AdaptAddon {
  private AdaptAddon() {
  }

  public static BrewingRecipe glowRecipe(ItemStack base, ItemStack result) {
    return BrewingRecipe.builder()
        .id("addon-glow")
        .ingredient(Material.GLOWSTONE_DUST)
        .basePotion(base)
        .result(result)
        .brewingTime(400)
        .fuelCost(2)
        .build();
  }

  public static void registerBrewing(BrewingRecipe recipe) {
    BrewingManager.registerRecipe("addon", recipe);
  }

  public static Material ingredient(BrewingRecipe recipe) {
    return recipe.getIngredient();
  }

  public static ItemStack result(BrewingRecipe recipe) {
    return recipe.getResult();
  }

  public static ItemStack basePotion(BrewingRecipe recipe) {
    return recipe.getBasePotion();
  }

  public static boolean canBrewAt(BrewingRecipe recipe, Location location) {
    return BrewingTask.isValid(recipe, location);
  }

  public static PotionBuilder splash() {
    return PotionBuilder.of(PotionBuilder.Type.SPLASH)
        .setColor(Color.fromRGB(10, 20, 30))
        .setName(Component.text("Swift"))
        .addLore(Component.text("Brewed by an addon"));
  }

  public static PotionBuilder swiftness(PotionBuilder builder) {
    return builder.addEffect(PotionEffectType.SPEED, 200, 1, false, true, true)
        .setBaseType(PotionType.SWIFTNESS);
  }

  public static Color color(PotionBuilder builder) {
    return builder.getColor();
  }

  public static PotionType baseType(PotionBuilder builder) {
    return builder.getBaseType();
  }

  public static Component name(PotionBuilder builder) {
    return builder.getName();
  }

  public static int loreLines(PotionBuilder builder) {
    return builder.getLore().size();
  }

  public static Material material(PotionBuilder.Type type) {
    return type.getMaterial();
  }

  public static ItemStack build(PotionBuilder builder) {
    return builder.build();
  }

  public static void recordEffect(Player player) {
    AdaptPotionRegistry.record(player.getUniqueId(), PotionEffectType.SPEED);
  }

  public static void registerAbilityProviders(Plugin plugin, AbilityUsePolicy policy, AbilityCostProvider costs) {
    Bukkit.getServicesManager().register(AbilityUsePolicy.class, policy, plugin, ServicePriority.Normal);
    Bukkit.getServicesManager().register(AbilityCostProvider.class, costs, plugin, ServicePriority.Normal);
  }

  public static void installAbilityApi(Plugin plugin) {
    AbilityApiBridge.install(plugin);
  }

  public static Skill<?> skill(String id) {
    return Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(id);
  }

  public static void awardXp(Player player, Skill<?> skill, double amount) {
    XP.xp(player, skill, amount);
    skill.xp(player, amount);
  }

  public static XpAward questAward(Location location) {
    return new XpAward("addon:quest", 25, location, true, XpAward.Kind.GAMEPLAY);
  }

  public static Location awardLocation(XpAward award) {
    return award.location();
  }

  public static void award(Player player, Skill<?> skill, XpAward award) {
    XP.award(player, skill, award);
  }

  public static AdaptPlayer adaptPlayer(Player player) {
    return Adapt.instance.getAdaptServer().getPlayer(player);
  }

  public static Player bukkitPlayer(AdaptPlayer player) {
    return player.getPlayer();
  }

  public static int level(Adaptation<?> adaptation, Player player) {
    return adaptation.getLevel(player);
  }

  public static Material icon(Skill<?> skill) {
    return skill.getIcon();
  }
}
