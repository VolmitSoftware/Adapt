package art.arcane.adapt.content.adaptation.brewing;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.content.adaptation.brewing.upgraded.AlchemistsBandolier;
import art.arcane.adapt.content.skill.SkillBrewing;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.BrewingMessages;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EffectCategory;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.item.TooltipPart;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * Quarter-bottle doses drawn from a potion the player carries. The potion is the active one of a carried Alchemist's
 * Bandolier, else the one in the off hand; when the off hand holds a shield or a clock, the first potion in the hotbar.
 * A bottle gives four doses, then empties. The first dose opens the bottle: the bottle as brewed is stored whole in its
 * data and its visible potion is emptied, so it can no longer be drunk, thrown or brewed for its effects. An opened
 * bottle keeps its name and lists its effects and the doses left; the rest of its stack comes back as brewed when the
 * opened bottle runs dry.
 */
public final class BrewingDoses {
  private static final int QUARTERS = 4;
  private static final DataKey<Integer> USED = DataKey.adapt("brewing_quarters_used", DataCodecs.INT);
  private static final DataKey<String> OPENED = DataKey.adapt("brewing_opened_bottle", DataCodecs.STRING);
  private static final Set<Id> BOTTLES = Set.of(Id.minecraft("potion"), Id.minecraft("splash_potion"),
      Id.minecraft("lingering_potion"));
  private static final Set<Id> OFF_HAND_TOOLS = Set.of(Id.minecraft("shield"), Id.minecraft("clock"));
  private static final Id GLASS_BOTTLE = Id.minecraft("glass_bottle");
  private static final int HOTBAR = 9;
  private static final Set<String> UNREADABLE = ConcurrentHashMap.newKeySet();

  private BrewingDoses() {
  }

  /** The potion a dose would come from, or null when the player carries none in the right place. */
  public static Dose find(PlayerRef player) {
    AlchemistsBandolier.Loaded loaded = AlchemistsBandolier.find(player);
    Dose carried = loaded == null ? null
        : dose(loaded.potion(), (ItemRef after) -> loaded.bandolier().replace(player, loaded, after));
    if (carried != null) {
      return carried;
    }
    ItemRef offHand = player.inventory().offHand();
    if (offHand != null && !offHand.isEmpty() && OFF_HAND_TOOLS.contains(offHand.type())) {
      for (int slot = 0; slot < HOTBAR; slot++) {
        int hotbarSlot = slot;
        Dose dose = dose(player.inventory().item(slot), (ItemRef after) -> player.inventory().setItem(hotbarSlot, after));
        if (dose != null) {
          return dose;
        }
      }
      return null;
    }
    return dose(offHand, player.inventory()::setOffHand);
  }

  /** Whether a dose could be drawn from {@code item}. */
  public static boolean dosable(ItemRef item) {
    return dose(item, (ItemRef after) -> {
    }) != null;
  }

  /** Every effect a potion gives: its base type's effects, then its custom effects. */
  public static List<EffectInstance> effects(PotionContents potion) {
    List<EffectInstance> effects = new ArrayList<>();
    if (potion.potion() != null) {
      effects.addAll(AdaptCore.platform().host().registries().potionEffects(potion.potion()));
    }
    effects.addAll(potion.customEffects());
    return effects;
  }

  /** Uses up a quarter of the dose's bottle; the fourth quarter leaves an empty glass bottle. */
  public static void take(PlayerRef player, Dose dose) {
    ItemRef bottle = dose.bottle();
    ItemRef brewed = brewed(bottle);
    if (brewed == null) {
      return;
    }
    int used = bottle.data().getOrDefault(USED, 0) + 1;
    ItemRef after;
    if (used < QUARTERS) {
      if (!bottle.data().has(OPENED)) {
        open(bottle, brewed);
      }
      bottle.data().set(USED, used);
      label(bottle, brewed, used);
      after = bottle;
    } else if (bottle.amount() > 1) {
      after = brewed.copyWithAmount(bottle.amount() - 1);
      if (dose.drinkable()) {
        giveBack(player, AdaptCore.platform().host().items().create(GLASS_BOTTLE, 1));
      }
    } else {
      after = dose.drinkable() ? AdaptCore.platform().host().items().create(GLASS_BOTTLE, 1)
          : AdaptCore.platform().host().items().empty();
    }
    dose.put().accept(after);
  }

  /** The name a potion goes by: its custom name, else its vanilla potion name, else its first effect; null if none. */
  public static @Nullable String name(ItemRef potion) {
    ItemRef brewed = brewed(potion);
    PotionContents contents = brewed == null ? null : brewed.potionContents().orElse(null);
    if (contents == null) {
      return null;
    }
    if (contents.name() != null) {
      return contents.name();
    }
    Id base = contents.potion();
    if (base != null && base.namespace().equals("minecraft")) {
      return "<lang:item.minecraft." + brewed.type().path() + ".effect."
          + base.path().replaceFirst("^(long|strong)_", "") + ">";
    }
    List<EffectInstance> effects = effects(contents);
    if (effects.isEmpty()) {
      return null;
    }
    Id effect = effects.getFirst().type();
    return "<lang:effect." + effect.namespace() + "." + effect.path() + ">";
  }

  private static Dose dose(ItemRef item, Consumer<ItemRef> put) {
    if (item == null || item.isEmpty() || !BOTTLES.contains(item.type())) {
      return null;
    }
    if (item.potionContents().isEmpty()) {
      return null;
    }
    PotionContents contents = contents(item);
    if (!SkillBrewing.isBrewed(contents)) {
      return null;
    }
    List<EffectInstance> effects = effects(contents);
    return effects.isEmpty() ? null : new Dose(item, effects, put);
  }

  /** The bottle's potion as brewed, read back from stored data once the bottle is opened. */
  private static PotionContents contents(ItemRef bottle) {
    ItemRef brewed = brewed(bottle);
    return brewed == null ? PotionContents.EMPTY : brewed.potionContents().orElse(PotionContents.EMPTY);
  }

  /** A single bottle as brewed: a copy of it until opened, else the stored one; null when that cannot be read. */
  private static @Nullable ItemRef brewed(ItemRef bottle) {
    String stored = bottle.data().get(OPENED);
    if (stored != null) {
      try {
        List<ItemRef> items = AdaptCore.platform().host().items().deserializeItems(Base64.getDecoder().decode(stored));
        if (items.size() == 1 && !items.getFirst().isEmpty()) {
          return items.getFirst();
        }
      } catch (IllegalArgumentException | IllegalStateException unreadable) {
        if (UNREADABLE.add(stored)) {
          AdaptLog.warn("An opened potion's stored bottle could not be read, leaving it as is: " + stored);
        }
      }
      return null;
    }
    return bottle.copyWithAmount(1);
  }

  private static void open(ItemRef bottle, ItemRef brewed) {
    PotionContents contents = brewed.potionContents().orElse(PotionContents.EMPTY);
    bottle.data().set(OPENED, Base64.getEncoder().encodeToString(
        AdaptCore.platform().host().items().serializeItems(List.of(brewed.copyWithAmount(1)))));
    bottle.setPotionContents(new PotionContents(null, contents.color(), List.of(), name(brewed)));
    bottle.hideTooltip(TooltipPart.POTION_EFFECTS);
  }

  private static void label(ItemRef bottle, ItemRef brewed, int used) {
    RegistryLookup registries = AdaptCore.platform().host().registries();
    List<String> lore = new ArrayList<>();
    for (EffectInstance effect : effects(brewed.potionContents().orElse(PotionContents.EMPTY))) {
      Id type = effect.type();
      StringBuilder line = new StringBuilder(registries.effectCategory(type) == EffectCategory.HARMFUL ? "<red>" : "<blue>")
          .append("<lang:effect.").append(type.namespace()).append('.').append(type.path()).append('>');
      if (effect.amplifier() > 0) {
        line.append(" <lang:potion.potency.").append(effect.amplifier()).append('>');
      }
      if (effect.isInfinite()) {
        line.append(" (<lang:effect.duration.infinite>)");
      } else if (effect.durationTicks() > 20) {
        line.append(" (").append(duration(effect.durationTicks() / 20)).append(')');
      }
      lore.add(line.toString());
    }
    lore.add("<gray>" + AdaptLanguage.text(BrewingMessages.DOSES_LEFT,
        trusted("left", Integer.toString(QUARTERS - used)), trusted("total", Integer.toString(QUARTERS))));
    bottle.setLore(lore);
  }

  private static String duration(int seconds) {
    int hours = seconds / 3600;
    return hours > 0 ? "%d:%02d:%02d".formatted(hours, seconds / 60 % 60, seconds % 60)
        : "%02d:%02d".formatted(seconds / 60, seconds % 60);
  }

  private static void giveBack(PlayerRef player, ItemRef item) {
    List<ItemRef> overflow = player.inventory().addItem(item);
    if (overflow == null) {
      return;
    }
    for (ItemRef rest : overflow) {
      player.world().dropItem(player.location().position(), rest);
    }
  }

  /** A potion to draw from: the bottle, the effects one dose gives, and where the bottle goes back after a dose. */
  public record Dose(ItemRef bottle, List<EffectInstance> effects, Consumer<ItemRef> put) {
    boolean drinkable() {
      return bottle.type().equals(Id.minecraft("potion"));
    }
  }
}
