package art.arcane.adapt.content.adaptation.brewing;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.potion.BrewingRecipe;
import art.arcane.adapt.api.potion.BrewingTask;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.entity.EffectCategory;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.inventory.BrewEvent;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BrewingStandRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;

/**
 * Mixing: two or three finished potions of one kind and a glowstone dust in a brewing stand brew into one potion that
 * carries every effect, each at a fraction of its duration. Instant effects, an effect twice, opened bottles and more
 * effects than the brewer's level allows are refused, so a mix always costs every potion that went in.
 */
public final class BrewingMixer {
  public static final Id GLOWSTONE = Id.minecraft("glowstone_dust");
  private static final Id BREWING_STAND = Id.minecraft("brewing_stand");
  private static final Id BLAZE_POWDER = Id.minecraft("blaze_powder");
  private static final int FUEL_PER_POWDER = 20;
  private static final int VANILLA_BREW_TICKS = 400;

  private final Adaptation<?> owner;
  private final IntFunction<Rules> rules;
  private final BiConsumer<PlayerRef, BlockRef> mixed;
  private final Map<BlockRef, Mix> active = new ConcurrentHashMap<>();

  /** {@code rules} gives the mix rules at a level; {@code mixed} runs once a mix is poured into the stand. */
  public BrewingMixer(Adaptation<?> owner, IntFunction<Rules> rules, BiConsumer<PlayerRef, BlockRef> mixed) {
    this.owner = owner;
    this.rules = rules;
    this.mixed = mixed;
  }

  /**
   * What a mix takes and makes: the bottle material every input shares, the most effects one bottle may carry, the
   * share of each effect's duration it keeps, the brewing time, the result's name and colour, and whether every effect
   * must be harmful.
   */
  public record Rules(Id material, int maxEffects, double durationFraction, int brewTicks, String name, Rgb color,
                      boolean harmfulOnly) {
  }

  /** An adaptation whose brewers mix potions, by the rules of the level they hold. */
  public interface Mixes {
    Rules mixRules(int level);
  }

  /** The mixed potion the bottles make under {@code rules}, or null when they cannot be mixed. */
  public static @Nullable ItemRef mix(List<ItemRef> bottles, Rules rules) {
    List<ItemRef> inputs = inputs(bottles);
    if (inputs.size() < 2) {
      return null;
    }
    RegistryLookup registries = AdaptCore.platform().host().registries();
    Set<Id> types = new HashSet<>();
    List<EffectInstance> effects = new ArrayList<>();
    for (ItemRef input : inputs) {
      Optional<PotionContents> contents = input.potionContents();
      if (!input.type().equals(rules.material()) || input.amount() != 1 || contents.isEmpty()) {
        return null;
      }
      List<EffectInstance> carried = BrewingDoses.effects(contents.get());
      if (carried.isEmpty()) {
        return null;
      }
      for (EffectInstance effect : carried) {
        if (registries.isInstantEffect(effect.type()) || !types.add(effect.type())
            || (rules.harmfulOnly() && registries.effectCategory(effect.type()) != EffectCategory.HARMFUL)) {
          return null;
        }
        effects.add(shortened(effect, rules.durationFraction()));
      }
    }
    if (effects.size() > rules.maxEffects()) {
      return null;
    }
    ItemRef result = AdaptCore.platform().host().items().create(rules.material(), 1);
    result.setPotionContents(new PotionContents(null, rules.color(), effects, rules.name()));
    return result;
  }

  /** Starts a mix one tick after a holder changes a brewing stand they have open. */
  public void watch(InventoryClickEvent e) {
    if (e.isCancelled() || e.viewKind() != InventoryKind.BREWING) {
      return;
    }
    PlayerRef player = e.player();
    Optional<BlockRef> open = player.openContainerBlock();
    if (open.isEmpty() || owner.getActiveLevel(player) <= 0) {
      return;
    }
    BlockRef block = open.get();
    Tasks.runAt(block.location(), () -> start(player, block), 1);
  }

  /**
   * A vanilla brew finishing on a stand that is mixing would strengthen the inputs instead; it is cancelled and the
   * mix is poured at once.
   */
  public void intercept(BrewEvent e) {
    Mix running = active.get(e.block());
    if (running == null) {
      return;
    }
    e.setCancelled(true);
    running.pour();
  }

  void start(PlayerRef player, BlockRef block) {
    int level = owner.getActiveLevel(player);
    if (level <= 0 || active.containsKey(block) || !block.type().equals(BREWING_STAND)) {
      return;
    }
    Optional<BrewingStandRef> found = block.brewingStand();
    if (found.isEmpty()) {
      return;
    }
    BrewingStandRef stand = found.get();
    Rules levelRules = rules.apply(level);
    List<ItemRef> bottles = bottles(stand);
    if (!stand.ingredient().type().equals(GLOWSTONE) || matchesAdaptRecipe(stand, bottles)
        || mix(bottles, levelRules) == null || !burnFuel(stand)) {
      return;
    }
    Mix mix = new Mix(player, block, stand, bottles, stand.ingredient().copy(), levelRules.brewTicks());
    active.put(block, mix);
    stand.setBrewingTime(VANILLA_BREW_TICKS);
    if (!Tasks.runAt(block.location(), mix::tick, 1)) {
      active.remove(block, mix);
    }
  }

  private boolean matchesAdaptRecipe(BrewingStandRef stand, List<ItemRef> bottles) {
    if (owner.getSkill() == null) {
      return false;
    }
    List<Adaptation<?>> adaptations = new ArrayList<>(owner.getSkill().getAdaptations());
    adaptations.addAll(owner.getSkill().getUpgrades());
    for (Adaptation<?> adaptation : adaptations) {
      for (BrewingRecipe recipe : adaptation.getBrewingRecipes()) {
        if (!recipe.getIngredient().equals(stand.ingredient().type())) {
          continue;
        }
        for (ItemRef bottle : bottles) {
          if (!bottle.isEmpty() && recipe.getBasePotion().isSimilar(bottle)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean burnFuel(BrewingStandRef stand) {
    if (stand.fuelLevel() > 0) {
      stand.setFuelLevel(stand.fuelLevel() - 1);
      return true;
    }
    if (!stand.fuel().type().equals(BLAZE_POWDER)) {
      return false;
    }
    stand.setFuel(BrewingTask.decrease(stand.fuel(), 1));
    stand.setFuelLevel(FUEL_PER_POWDER - 1);
    return true;
  }

  private static EffectInstance shortened(EffectInstance effect, double fraction) {
    if (effect.isInfinite()) {
      return effect;
    }
    return new EffectInstance(effect.type(), Math.max(1, (int) Math.round(effect.durationTicks() * fraction)),
        effect.amplifier(), effect.ambient(), effect.particles(), effect.icon());
  }

  private static List<ItemRef> inputs(List<ItemRef> bottles) {
    List<ItemRef> inputs = new ArrayList<>(bottles.size());
    for (ItemRef bottle : bottles) {
      if (bottle != null && !bottle.isEmpty()) {
        inputs.add(bottle);
      }
    }
    return inputs;
  }

  private static List<ItemRef> bottles(BrewingStandRef stand) {
    List<ItemRef> bottles = new ArrayList<>(BrewingStandRef.BOTTLE_SLOTS);
    for (int slot = 0; slot < BrewingStandRef.BOTTLE_SLOTS; slot++) {
      bottles.add(stand.bottle(slot).copy());
    }
    return bottles;
  }

  private static boolean same(ItemRef now, ItemRef then) {
    if (now.isEmpty() || then.isEmpty()) {
      return now.isEmpty() && then.isEmpty();
    }
    return now.amount() == then.amount() && now.isSimilar(then);
  }

  private final class Mix {
    private final PlayerRef brewer;
    private final BlockRef block;
    private final BrewingStandRef stand;
    private final List<ItemRef> bottles;
    private final ItemRef ingredient;
    private final int totalTicks;
    private int remaining;

    private Mix(PlayerRef brewer, BlockRef block, BrewingStandRef stand, List<ItemRef> bottles, ItemRef ingredient,
                int totalTicks) {
      this.brewer = brewer;
      this.block = block;
      this.stand = stand;
      this.bottles = bottles;
      this.ingredient = ingredient;
      this.totalTicks = Math.max(1, totalTicks);
      this.remaining = this.totalTicks;
    }

    private void tick() {
      if (active.get(block) != this) {
        return;
      }
      if (!unchanged()) {
        stop();
        return;
      }
      remaining--;
      if (remaining <= 0) {
        pour();
        return;
      }
      stand.setBrewingTime(Math.max(1, (int) (VANILLA_BREW_TICKS * (remaining / (double) totalTicks))));
      if (!Tasks.runAt(block.location(), this::tick, 1)) {
        stop();
      }
    }

    private void pour() {
      if (!active.remove(block, this)) {
        return;
      }
      int level = owner.getActiveLevel(brewer);
      ItemRef result = level > 0 && unchanged() ? mix(bottles(stand), rules.apply(level)) : null;
      stand.setBrewingTime(0);
      if (result == null) {
        return;
      }
      boolean placed = false;
      for (int slot = 0; slot < BrewingStandRef.BOTTLE_SLOTS; slot++) {
        if (stand.bottle(slot).isEmpty()) {
          continue;
        }
        stand.setBottle(slot, placed ? AdaptCore.platform().host().items().empty() : result);
        placed = true;
      }
      stand.setIngredient(BrewingTask.decrease(stand.ingredient(), 1));
      mixed.accept(brewer, block);
    }

    private void stop() {
      if (active.remove(block, this)) {
        stand.setBrewingTime(0);
      }
    }

    private boolean unchanged() {
      if (!block.type().equals(BREWING_STAND) || !same(stand.ingredient(), ingredient)) {
        return false;
      }
      for (int slot = 0; slot < BrewingStandRef.BOTTLE_SLOTS; slot++) {
        if (!same(stand.bottle(slot), bottles.get(slot))) {
          return false;
        }
      }
      return true;
    }
  }
}
