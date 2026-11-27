package art.arcane.adapt.api.config;

import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents one config field. The text becomes the comment above the field in the generated TOML file, which is
 * always English, and the English tooltip in the in-game config menu.
 *
 * <p>The menu is translated by convention keys, with no list of config classes anywhere: a field of the skill
 * {@code alchemist:alchemy} at the dotted path {@code brewXp} is labelled by the message
 * {@code alchemist.config.fields.skills.alchemy.brewXp.label} and described by the multi-line message
 * {@code ...brewXp.description}; an adaptation's fields use {@code config.fields.adaptations.<path>} in the same way
 * (see {@link art.arcane.adapt.api.text.TextKeys#skillConfigField} and
 * {@link art.arcane.adapt.api.text.TextKeys#adaptationConfigField}). Adapt declares both keys for every field of
 * registered content, in English: the label is the field name split into words ({@code brewXp} is "Brew Xp"), the
 * description this annotation's text, then {@code "Effect: "} and {@link #impact()}. So editing the annotation edits
 * the English, and the addon's bundles translate the keys in any locale without restating the English; an
 * {@code en_US} entry for a key replaces the derived English.
 */
@ApiStatus.AvailableSince("1.0.0")
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigDoc {
  /** What the setting controls. */
  String value();

  /** What raising, lowering or toggling it does. */
  String impact() default "";
}
