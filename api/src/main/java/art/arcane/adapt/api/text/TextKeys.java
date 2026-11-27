package art.arcane.adapt.api.text;

import art.arcane.adapt.api.content.ContentIds;
import art.arcane.adapt.api.mutation.BlockSlot;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Locale;

/**
 * The message keys content gets by convention, so that an author who names nothing still gets every string.
 *
 * <p>Every key starts with the namespace of the addon that owns the string, which is also the bundle the string is
 * looked up in. A path's {@code /} becomes a dot. For {@code alchemist:alchemy} and
 * {@code alchemist:alchemy/transmute}:
 *
 * <pre>
 * alchemist.skill.alchemy.name
 * alchemist.skill.alchemy.description
 * alchemist.skill.alchemy.emoji               one glyph shown before the name, optional
 * alchemist.skill.alchemy.quip                toast line when the skill is discovered
 * alchemist.skill.alchemy.unlock_quip.1       toast lines for unlocked adaptations, numbered from 1, optional
 * alchemist.skill.alchemy.xp.transmuting      "Earning XP" guide line of a declared source
 * alchemist.skill.alchemy.mutated.noun        mutation name when the skill leads
 * alchemist.skill.alchemy.mutated.adjective   mutation name when the skill is the partner
 * alchemist.skill.alchemy.block.trigger       what each mutation block does, one per {@link BlockSlot}
 * alchemist.adaptation.alchemy.transmute.name
 * alchemist.adaptation.alchemy.transmute.description
 * alchemist.adaptation.alchemy.transmute.short_name    optional
 * alchemist.adaptation.alchemy.transmute.lore1         stat labels, numbered from 1
 * alchemist.advancement.brew_100.title
 * alchemist.advancement.brew_100.description
 * alchemist.item.philosophers_stone.name
 * alchemist.config.fields.skills.alchemy.brewXp.label                    optional
 * alchemist.config.fields.skills.alchemy.brewXp.description              optional
 * alchemist.config.fields.adaptations.alchemy.transmute.chance.label     optional
 * alchemist.config.fields.adaptations.alchemy.transmute.chance.description   optional
 * </pre>
 */
@ApiStatus.AvailableSince("1.0.0")
public final class TextKeys {
  private TextKeys() {
  }

  /** {@code <namespace>.skill.<path>} */
  public static String skill(Id skill) {
    return skill.namespace() + ".skill." + skill.path().replace('/', '.');
  }

  /** {@code <namespace>.adaptation.<path>} */
  public static String adaptation(Id adaptation) {
    return adaptation.namespace() + ".adaptation." + adaptation.path().replace('/', '.');
  }

  /** {@code <namespace>.advancement.<key>} for an advancement the content with id {@code owner} registers. */
  public static String advancement(Id owner, String key) {
    return owner.namespace() + ".advancement." + key;
  }

  /** {@code <namespace>.item.<path>} */
  public static String item(Id item) {
    return item.namespace() + ".item." + item.path().replace('/', '.');
  }

  /** {@code <skill key>.xp.<source>} */
  public static String xpSource(Id skill, String source) {
    return skill(skill) + ".xp." + source;
  }

  /**
   * {@code <namespace>.config.fields.skills.<path>.<field>}: the key a skill's config field is labelled and described
   * by in the in-game config menu. The label is the message {@code <key>.label}, the description the multi-line
   * message {@code <key>.description}. Both are optional, see {@link art.arcane.adapt.api.config.ConfigDoc}.
   *
   * @param field the field's dotted path in the config file, such as {@code brewXp} or {@code rewards.base}
   */
  public static String skillConfigField(Id skill, String field) {
    return skill.namespace() + ".config.fields.skills." + skill.path().replace('/', '.') + "." + requireField(field);
  }

  /**
   * {@code <namespace>.config.fields.adaptations.<path>.<field>}, as {@link #skillConfigField} for the config of an
   * adaptation, mutation block, upgrade or signature.
   */
  public static String adaptationConfigField(Id adaptation, String field) {
    return adaptation.namespace() + ".config.fields.adaptations." + adaptation.path().replace('/', '.') + "."
        + requireField(field);
  }

  /** {@code <skill key>.block.<slot>}, the phrase a mutation's generated description is built from. */
  public static String block(Id skill, BlockSlot slot) {
    return skill(skill) + ".block." + slot.name().toLowerCase(Locale.ROOT);
  }

  /**
   * {@code <namespace>.pair.<lead>.<partner>.<recipe>}, in the namespace of the addon that registered the signature
   * or text for the pair. Lead and partner are written with dots: {@code adapt.swords}.
   */
  public static String pair(String namespace, MutationSlot slot) {
    return namespace + ".pair." + ContentIds.dotted(slot.pair().lead()) + "." + ContentIds.dotted(slot.pair().partner())
        + "." + slot.recipe().name().toLowerCase(Locale.ROOT);
  }

  private static String requireField(String field) {
    if (field == null || field.isEmpty() || field.charAt(0) == '.' || field.charAt(field.length() - 1) == '.'
        || field.contains("..")) {
      throw new IllegalArgumentException("Invalid config field path: '" + field + "'");
    }
    return field;
  }
}
