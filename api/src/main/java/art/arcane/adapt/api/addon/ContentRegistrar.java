package art.arcane.adapt.api.addon;

import art.arcane.adapt.api.asset.AssetBundle;
import art.arcane.adapt.api.command.AdaptCommand;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.PairText;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.player.PlayerSection;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.MessageBundle;
import art.arcane.adapt.api.world.BlockSlice;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What an addon declares during {@link AdaptAddon#register}. Valid only inside that call.
 *
 * <p>Every id an addon registers is in the addon's own namespace. A second registration of an id that already exists
 * is refused with both owners named; nothing is ever overwritten. Any refusal rejects the whole addon.
 */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface ContentRegistrar {
  /** The namespace of the addon being registered. */
  String namespace();

  /** Adds the addon's messages. May be called more than once; a key defined twice is refused. */
  void messages(MessageBundle bundle);

  /** Adds the addon's art. The bundle's namespace must be the addon's. */
  @ApiStatus.Experimental
  void assets(AssetBundle bundle);

  /**
   * Registers a skill. The skill registers its own adaptations, groups, advancements and mutation kit in its
   * constructor.
   *
   * @param id      the id the built skill must report from {@code getId()}
   * @param factory builds a fresh skill; called once per core start and again after a config reload
   */
  void skill(Id id, Supplier<? extends SimpleSkill<?>> factory);

  /**
   * Adds an adaptation to a skill of any addon, built-in skills included. It appears on that skill's pages after
   * the skill's own adaptations and is priced, learned and saved like them. If the skill is not installed the
   * adaptation waits, and appears when the skill does.
   *
   * @param skill the skill to extend
   * @param id    the adaptation's id, in this addon's namespace
   */
  void adaptation(Id skill, Id id, Supplier<? extends SimpleAdaptation<?>> factory);

  /**
   * Registers a custom item that belongs to no adaptation, such as an item any skill can hand out. Items an
   * adaptation or mutation tool uses are registered there instead. The id is in this addon's namespace.
   */
  void item(CustomItem.Definition<?> definition);

  /**
   * Registers the hand-made effect of one mutation slot, replacing the adaptation the recipe would generate. Either
   * parent may belong to any addon, and the slot applies once both are registered. One signature per slot: a second
   * addon claiming a slot another installed addon holds is refused, and the refusal names both addons.
   *
   * <p>The claim is checked when the addon is applied, and it is held whether or not the parents are registered.
   *
   * @param factory builds the signature for the slot; one class may serve several slots
   * @throws IllegalArgumentException when this addon already registered a signature for the slot, or {@code lead}
   *                                  and {@code partner} do not form a valid
   *                                  {@link art.arcane.adapt.api.mutation.SkillPair}
   */
  @ApiStatus.Experimental
  void signature(Id lead, Id partner, Recipe recipe, Function<MutationSlot, ? extends Signature<?>> factory);

  /**
   * Replaces the generated description of one mutation slot, and optionally the blocks it runs. Ignored for a slot
   * that has a signature. One text per slot, refused like a second signature.
   *
   * @throws IllegalArgumentException when this addon already registered text for the slot, or the pair is invalid
   */
  @ApiStatus.Experimental
  void pairText(Id lead, Id partner, Recipe recipe, PairText text);

  /**
   * Names one mutation instead of the partner's adjective and the lead's noun, in this addon's messages. {@code (a, b)}
   * and {@code (b, a)} are named separately. One name per pair, refused like a second signature.
   *
   * @throws IllegalArgumentException when this addon already named the pair, or the pair is invalid
   */
  @ApiStatus.Experimental
  void pairName(Id lead, Id partner, Text name);

  /**
   * Subscribes an object's {@code @Handle} methods for as long as the addon is installed. Use this for listeners
   * that belong to the addon's content; a listener that must outlive it goes through {@code AdaptApi.events()}.
   */
  void listener(Object listener);

  /**
   * Adds a command below {@code /adapt}, on every platform, for as long as the addon is installed. Its words must not
   * be taken by Adapt's own commands or by another addon; a clash rejects the addon with both owners named. A
   * permission the command or one of its subcommands names is registered with its default.
   */
  void command(AdaptCommand command);

  /**
   * Declares this addon's data section {@code <namespace>:<path>} in each player's save. Adapt records this addon as
   * its owner and {@code type} as its value type, and accepts only the returned object when the section is read; see
   * {@link PlayerSection}. Each core start returns the same object for the same path and type, so it can be kept in
   * a field across starts.
   *
   * @param path    the section path, in the content id grammar; it is file content and must never change
   * @param type    the value class
   * @param initial makes the value of a player whose save has none; must not return null
   * @throws IllegalArgumentException for a path outside the content id grammar, or a path declared twice
   */
  @ApiStatus.Experimental
  <T> PlayerSection<T> playerSection(String path, Class<T> type, Supplier<T> initial);

  /**
   * Declares a kind of per-block value {@code <namespace>:<path>} this addon keeps in world data; see
   * {@link BlockSlice}. There is no limit on how many slices an addon or a server declares.
   *
   * @param path  the slice path, in the content id grammar; it is file content and must never change
   * @param type  the class of the stored values
   * @param codec writes and reads one value
   * @throws IllegalArgumentException for a path outside the content id grammar, or a path declared twice
   */
  @ApiStatus.Experimental
  <T> BlockSlice<T> blockSlice(String path, Class<T> type, BlockSlice.Codec<T> codec);
}
