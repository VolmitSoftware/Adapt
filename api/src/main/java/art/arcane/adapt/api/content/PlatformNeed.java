package art.arcane.adapt.api.content;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

/**
 * Something a piece of content needs from the running Minecraft version. Content declares its needs once; when one
 * is missing the content is left out on that version.
 */
@ApiStatus.AvailableSince("1.0.0")
public sealed interface PlatformNeed {
  /** Short reason shown in the startup log and the per-version catalog, for example {@code item minecraft:mace}. */
  String describe();

  /**
   * Whether the running server meets every need well enough to use: present, or emulated by Adapt. Partial needs
   * count as met.
   */
  @ApiStatus.Experimental
  static boolean usable(Collection<PlatformNeed> needs) {
    return Engine.bridge().progress().usable(Objects.requireNonNull(needs, "needs"));
  }

  /** The vanilla item {@code minecraft:<vanillaPath>}, such as {@code item("mace")}. */
  static PlatformNeed item(String vanillaPath) {
    return new Content(RegistryKind.ITEM, Id.minecraft(vanillaPath));
  }

  /** The vanilla block {@code minecraft:<vanillaPath>}. */
  static PlatformNeed block(String vanillaPath) {
    return new Content(RegistryKind.BLOCK, Id.minecraft(vanillaPath));
  }

  /** The vanilla entity type {@code minecraft:<vanillaPath>}. */
  static PlatformNeed entity(String vanillaPath) {
    return new Content(RegistryKind.ENTITY_TYPE, Id.minecraft(vanillaPath));
  }

  /** The vanilla enchantment {@code minecraft:<vanillaPath>}. */
  static PlatformNeed enchantment(String vanillaPath) {
    return new Content(RegistryKind.ENCHANTMENT, Id.minecraft(vanillaPath));
  }

  /** The vanilla status effect {@code minecraft:<vanillaPath>}. */
  static PlatformNeed effect(String vanillaPath) {
    return new Content(RegistryKind.EFFECT, Id.minecraft(vanillaPath));
  }

  /** A player attribute. Met natively, or emulated by Adapt on versions without it. */
  static PlatformNeed attribute(Id attribute) {
    return new Attribute(attribute);
  }

  /** An attribute that must be real: for content whose point is an effect {@link #attribute} cannot stand in for. */
  static PlatformNeed nativeAttribute(Id attribute) {
    return new Content(RegistryKind.ATTRIBUTE, attribute);
  }

  /** An engine feature the content cannot run without. */
  static PlatformNeed feature(EngineFeature feature) {
    return new Feature(feature);
  }

  /** An engine feature Adapt stands in for on versions without it: the content stays, listed as emulated. */
  static PlatformNeed emulatedFeature(EngineFeature feature) {
    return new EmulatedFeature(feature);
  }

  /**
   * An engine feature only part of the content relies on: without it the content stays, minus {@code lost} (for
   * example {@code no cooldown sweep on the item}), listed as partial.
   */
  static PlatformNeed partialFeature(EngineFeature feature, String lost) {
    return new PartialFeature(feature, lost);
  }

  /**
   * An attribute only part of the content relies on and Adapt cannot
   * stand in for: without it the content stays, minus {@code lost}, listed as partial.
   */
  static PlatformNeed partialAttribute(Id attribute, String lost) {
    return new PartialAttribute(attribute, lost);
  }

  /** A registry entry: item, block, entity type, enchantment, effect and the rest. */
  record Content(RegistryKind kind, Id id) implements PlatformNeed {
    public Content {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(id, "id");
    }

    @Override
    public String describe() {
      return kind.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " " + id;
    }
  }

  /** A player attribute that Adapt emulates where the version lacks it; built by {@link #attribute}. */
  record Attribute(Id attribute) implements PlatformNeed {
    public Attribute {
      Objects.requireNonNull(attribute, "attribute");
    }

    @Override
    public String describe() {
      return "attribute " + attribute;
    }
  }

  /** A required engine feature; built by {@link #feature}. */
  record Feature(EngineFeature feature) implements PlatformNeed {
    public Feature {
      Objects.requireNonNull(feature, "feature");
    }

    @Override
    public String describe() {
      return featureName(feature);
    }
  }

  /** An engine feature Adapt stands in for; built by {@link #emulatedFeature}. */
  record EmulatedFeature(EngineFeature feature) implements PlatformNeed {
    public EmulatedFeature {
      Objects.requireNonNull(feature, "feature");
    }

    @Override
    public String describe() {
      return featureName(feature);
    }
  }

  /**
   * An engine feature part of the content relies on; built by {@link #partialFeature}.
   *
   * @param lost what the content does without, as shown in the log and catalog
   */
  record PartialFeature(EngineFeature feature, String lost) implements PlatformNeed {
    public PartialFeature {
      Objects.requireNonNull(feature, "feature");
      Objects.requireNonNull(lost, "lost");
    }

    @Override
    public String describe() {
      return featureName(feature) + " (" + lost + ")";
    }
  }

  /**
   * An attribute part of the content relies on; built by {@link #partialAttribute}.
   *
   * @param lost what the content does without, as shown in the log and catalog
   */
  record PartialAttribute(Id attribute, String lost) implements PlatformNeed {
    public PartialAttribute {
      Objects.requireNonNull(attribute, "attribute");
      Objects.requireNonNull(lost, "lost");
    }

    @Override
    public String describe() {
      return "attribute " + attribute + " (" + lost + ")";
    }
  }

  private static String featureName(EngineFeature feature) {
    return "engine feature " + feature.name().toLowerCase(Locale.ROOT).replace('_', ' ');
  }
}
