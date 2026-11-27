package art.arcane.adapt.api;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Set;

/** The server-wide settings content needs to respect. A read-only view of {@code adapt.toml}. */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface AdaptSettings {
  /** Whether advancements are registered and granted at all. */
  boolean advancements();

  /** Whether adaptations act for players in creative mode. */
  boolean adaptationsInCreative();

  /** Whether world effects may show particles. Each component narrows this with its own switch. */
  boolean particles();

  /** Whether world effects may play sounds, the counterpart of {@link #particles()}. */
  boolean sounds();

  /** Whether level-up and learn cues play their sounds. */
  boolean progressionSounds();

  /** Whether the admin excluded the block from every growth effect. */
  boolean isGrowthBlacklisted(Id block);

  /** Every block the admin excluded from growth effects, as {@link #isGrowthBlacklisted(Id)} tests them. */
  @ApiStatus.Experimental
  Set<Id> growthBlacklist();

  /** The multiplier an upgrade's overcharge puts on its effect. */
  @ApiStatus.Experimental
  double overchargeStrength();

  /** The name of the block players use to open the Adapt menu, for help text. */
  String activatorBlockName();

  /** Whether hunter-style adaptations, which act on hunger and damage, stay off while the player has the Hunger effect. */
  boolean hungerEffectPausesHunterAdaptations();

  /** Whether a player's death wipes their Adapt progress. */
  boolean hardcoreResetOnDeath();

  /** Whether unlearning refunds nothing. */
  boolean hardcoreNoRefunds();

  /** Whether Adapt gives items and menu icons the custom models of {@code models.toml}. */
  boolean customModels();

  /** Whether menus that have a parent menu show a Back button. */
  boolean menuBackButton();

  /** Whether Escape closes every open Adapt menu instead of returning to the parent one. */
  boolean escapeClosesAllMenus();

  /** Whether the admin turned Adapt off in the world with this key. */
  @ApiStatus.Experimental
  boolean isWorldBlacklisted(Id world);

  /** Whether Adapt remembers crops grown with bone meal, so they pay no harvest XP. */
  @ApiStatus.Experimental
  boolean bonemealTracking();
}
