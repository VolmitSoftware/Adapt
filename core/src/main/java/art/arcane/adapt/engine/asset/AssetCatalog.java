package art.arcane.adapt.engine.asset;

import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Optional;

/**
 * Every installed provider's art: built-in Adapt art as the {@code adapt} bundle, then each addon's bundle in install
 * order. Reads are safe from any thread; the bundles change after enable as addons come and go, and every change
 * raises {@link #revision()} and tells the subscribed listeners.
 */
public interface AssetCatalog {
  long revision();

  /** The installed bundles, {@code adapt} first, then in install order. */
  List<InstalledBundle> bundles();

  /** The PNG of a texture such as {@code alchemist:gui/skill/alchemy}, from the bundle of its namespace. */
  Optional<byte[]> sprite(Id sprite);

  /**
   * The theme of a skill: its authored kit and atlas, or one generated from the skill's colour and icon when the skill
   * is registered and has none. Empty for an id that is neither.
   */
  Optional<ThemeAssets> theme(Id skill);

  /**
   * The emblem a skill gives the mutations it leads: its authored art, or for a registered addon skill without any,
   * one generated from the skill's look. Empty for an Adapt skill without art, whose art ships with the client, and
   * for an id that is no registered skill.
   */
  Optional<byte[]> emblem(Id skill);

  /** The trim a skill gives the mutations it joins as partner, found or generated like {@link #emblem(Id)}. */
  Optional<byte[]> trim(Id skill);

  /** The colour and icon generated art of a registered skill is drawn from; empty for an id that is no registered skill. */
  Optional<SkillLook> look(Id skill);

  /**
   * Every skill {@link #theme(Id)} answers for: each registered skill in registry order, then skills that only have an
   * authored kit, by id.
   */
  List<Id> themes();

  AssetSubscription subscribe(AssetListener listener);
}
