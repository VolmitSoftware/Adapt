package art.arcane.adapt.api.content;

import art.arcane.adapt.api.asset.Icon;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * A registered skill line, as the rest of the server sees it. Authors extend {@link SimpleSkill}; this is the view
 * the catalog hands out.
 *
 * <p>Names and descriptions are returned in the language of the player Adapt is rendering for.
 *
 * @param <T> the skill's config class
 */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface Skill<T extends SkillConfig> extends ContentComponent {
  /**
   * The live config. A reload replaces the object, so read it through this call each time instead of keeping it.
   */
  T getConfig();

  /** The colour in effect: the config's {@code skillColor} when it is a valid {@code #RRGGBB}, else the declared one. */
  Rgb getColor();

  /** The icon the skill declared, drawn on its tile and pages. */
  Icon getIcon();

  /** XP below which an award is not shown to the player. */
  double getMinXp();

  /** The name from the message {@code <skill key>.name}, or the capitalised id path when no bundle declares it. */
  String getLocalizedName();

  /** The glyph shown before the name; empty when the skill has none. */
  String getEmojiName();

  /** The text of the message {@code <skill key>.description}; empty when no bundle declares it. */
  String getDescription();

  /** The coloured name with its glyph; a disabled skill shows its name alone, greyed out. */
  String getDisplayName();

  /** The display name followed by {@code level}; a level of 0 or less adds nothing. */
  String getDisplayName(int level);

  /** The coloured glyph alone, for tight spaces; a disabled skill shows its greyed-out name instead. */
  String getShortName();

  /**
   * The learnable adaptations under this skill that the running server supports: its own first, then those other
   * addons added, each in registration order.
   */
  List<Adaptation<?>> getAdaptations();

  /** The labelled rows the skill's pages show its adaptations in; empty for one plain list. */
  List<AdaptationGroup> getAdaptationGroups();

  /** The ways to earn XP the skill declared, in declaration order. */
  List<XpSource> getXpSources();

  /**
   * The lines of the skill's "Earning XP" guide in declaration order: each distinct source guide and each note, once
   * apiece. Empty when the skill declares neither.
   */
  List<Text> getXpGuide();

  /**
   * The kit the skill registered for mutations; empty when it registered none. A skill without one can still be
   * mutated: Adapt generates a plain kit for it.
   */
  @ApiStatus.Experimental
  Optional<MutationKit> getMutationKit();

  /** The upgrades of this skill's mutation kit built on this server, in kit order; empty for a mutated skill. */
  @ApiStatus.Experimental
  List<Adaptation<?>> getUpgrades();

  /** The mutation blocks of this skill's kit built on this server, in kit order; empty for a mutated skill. */
  @ApiStatus.Experimental
  List<MutationBlock<?>> getBlocks();

  /** Recipes the skill itself registers, available to everyone. */
  List<RecipeSpec> getRecipes();

  /**
   * Whether the player may use the skill: true for operators, and when the node {@code adapt.use.<namespace>.<path>}
   * is not set for them; otherwise the node's value.
   */
  boolean hasUsePermission(PlayerRef player);

  /**
   * Grants XP at the player's position, with the floating number and ticker line.
   *
   * <p>Every {@code xp} variant does nothing while the skill is disabled, for a player whose data has not loaded, and
   * for an amount that is not a finite number above 0.
   */
  default void xp(PlayerRef player, double amount) {
    xp(player, null, amount, null);
  }

  /**
   * @param rewardKey names what was rewarded, such as {@code alchemist:alchemy:brewing}; anti-farm bookkeeping and
   *                  the XP event's source use it. {@link XpSource} builds it for its awards. Keys longer than 160
   *                  characters are cut to 160.
   */
  default void xp(PlayerRef player, double amount, @Nullable String rewardKey) {
    xp(player, null, amount, rewardKey);
  }

  /** Grants XP earned at {@code at}; null means the player's position. */
  default void xp(PlayerRef player, @Nullable Loc at, double amount) {
    xp(player, at, amount, null);
  }

  /** Grants XP earned at {@code at}; null means the player's position. */
  void xp(PlayerRef player, @Nullable Loc at, double amount, @Nullable String rewardKey);

  /** Grants XP without the ticker line, keeping the floating number at {@code at}. */
  default void xpS(PlayerRef player, @Nullable Loc at, double amount) {
    xpS(player, at, amount, null);
  }

  /** As {@link #xpS(PlayerRef, Loc, double)}, naming what was rewarded as {@link #xp(PlayerRef, double, String)} does. */
  void xpS(PlayerRef player, @Nullable Loc at, double amount, @Nullable String rewardKey);

  /** Grants XP with nothing shown. */
  default void xpSilent(PlayerRef player, double amount) {
    xpSilent(player, amount, null);
  }

  /** Grants XP with nothing shown, naming what was rewarded as {@link #xp(PlayerRef, double, String)} does. */
  void xpSilent(PlayerRef player, double amount, @Nullable String rewardKey);

  /** Leaves XP in the world at {@code at}: players within {@code radius} share it for {@code durationMillis}. */
  void xp(Loc at, double amount, int radius, long durationMillis);
}
