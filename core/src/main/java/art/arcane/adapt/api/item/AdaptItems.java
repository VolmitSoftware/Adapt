package art.arcane.adapt.api.item;

import art.arcane.adapt.api.golem.item.GolemItemGuard;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.MutationToolGuard;
import art.arcane.adapt.api.mutation.tool.MutationTools;
import art.arcane.adapt.content.adaptation.hunter.HunterSnares;
import art.arcane.adapt.content.item.BackpackItem;
import art.arcane.adapt.content.item.BoundEnderPearl;
import art.arcane.adapt.content.item.BoundEyeOfEnder;
import art.arcane.adapt.content.item.BoundRedstoneTorch;
import art.arcane.adapt.content.item.BoundSnowBall;
import art.arcane.adapt.content.item.ChalkWandItem;
import art.arcane.adapt.content.item.ChronoTimeBombItem;
import art.arcane.adapt.content.item.ChronoTimeBottle;
import art.arcane.adapt.content.item.ExperienceOrb;
import art.arcane.adapt.content.item.KnowledgeOrb;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Every item Adapt makes, recognised by the marker on the stack: mutation tools, Golemancy items, the data-bound items
 * (bound pearls, eyes, torches and snowballs, orbs, backpacks, chrono bottles and bombs), chalk wands and snare lines.
 * Each one declares the vanilla interactions it is built for and the recipes that may take it; {@link AdaptItemGuard}
 * refuses every other one.
 */
public final class AdaptItems {
  private static final Set<VanillaUse> NONE = Set.of();
  private static final Set<VanillaUse> USE = Set.of(VanillaUse.USE);
  private static final Set<VanillaUse> GOLEM = Set.of(VanillaUse.TRADE);
  private static final Set<VanillaUse> DORMANT_GOLEM = Set.of(VanillaUse.USE, VanillaUse.PLACE, VanillaUse.TRADE);
  private static final Predicate<Id> ADAPT_RECIPES = (Id recipe) -> recipe != null && "adapt".equals(recipe.namespace());
  private static final List<DataBoundItem<?>> BOUND = List.of(BoundEnderPearl.io, BoundEyeOfEnder.io,
      BoundRedstoneTorch.io, BoundSnowBall.io, BackpackItem.io, ChronoTimeBottle.io, ChronoTimeBombItem.io,
      ExperienceOrb.io, KnowledgeOrb.io);

  private AdaptItems() {
  }

  /** One kind of Adapt item: the vanilla interactions it is built for and the recipes that may take it. */
  public record AdaptItem(String id, Set<VanillaUse> designed, Predicate<Id> recipes) {
    public boolean allows(VanillaUse use) {
      return designed.contains(use);
    }

    public boolean takenBy(@Nullable Id recipe) {
      return recipes.test(recipe);
    }
  }

  /** The Adapt item this stack is, or null for anything else. */
  public static @Nullable AdaptItem of(@Nullable ItemRef item) {
    if (item == null || item.isEmpty()) {
      return null;
    }
    String tool = MutationTool.idOf(item);
    if (tool != null) {
      MutationTool<?> registered = MutationTools.find(tool);
      return new AdaptItem(tool, registered == null ? NONE : registered.vanillaUses(), MutationToolGuard::isToolRecipe);
    }
    Optional<GolemItemKind> golem = GolemItems.kindOf(item);
    if (golem.isPresent()) {
      return new AdaptItem("golemancy-" + golem.get().id(),
          golem.get() == GolemItemKind.DORMANT_GOLEM ? DORMANT_GOLEM : GOLEM, GolemItemGuard::isGolemancyRecipe);
    }
    for (DataBoundItem<?> bound : BOUND) {
      if (bound.hasData(item)) {
        return new AdaptItem(bound.getClass().getSimpleName(), USE, ADAPT_RECIPES);
      }
    }
    if (ChalkWandItem.read(item) != null) {
      return new AdaptItem("chalk", USE, ADAPT_RECIPES);
    }
    if (HunterSnares.isSnareItem(item)) {
      return new AdaptItem("snare-line", NONE, ADAPT_RECIPES);
    }
    return null;
  }

  /** Whether {@code item} is an Adapt item that the vanilla interaction {@code use} would spend or misuse. */
  public static boolean refuses(@Nullable ItemRef item, VanillaUse use) {
    AdaptItem adapt = of(item);
    return adapt != null && !adapt.allows(use);
  }

  /** Whether any of {@code items} is an Adapt item that {@code recipe} may not take. */
  public static boolean refusedBy(@Nullable Id recipe, @Nullable List<ItemRef> items) {
    if (items == null) {
      return false;
    }
    for (ItemRef item : items) {
      AdaptItem adapt = of(item);
      if (adapt != null && !adapt.takenBy(recipe)) {
        return true;
      }
    }
    return false;
  }

  /** Whether any of {@code items} is an Adapt item that the vanilla interaction {@code use} would spend. */
  public static boolean anyRefuses(@Nullable List<ItemRef> items, VanillaUse use) {
    if (items == null) {
      return false;
    }
    for (ItemRef item : items) {
      if (refuses(item, use)) {
        return true;
      }
    }
    return false;
  }
}
