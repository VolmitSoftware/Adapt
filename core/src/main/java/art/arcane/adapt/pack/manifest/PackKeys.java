package art.arcane.adapt.pack.manifest;

import art.arcane.adapt.client.view.IconSprites;
import art.arcane.adapt.engine.mutation.PairKeys;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.platform.value.Id;

/**
 * The one place a skill's {@link Id} becomes the pack's keys and resource paths, used by the bake that writes them and the
 * screens that look them up. A skill's art lives in its own namespace; an id path already follows resource location
 * rules, so it is used as it is.
 */
public final class PackKeys {
  private static final String PAGE = "page/";
  private static final String TOAST = "adapt_ui/toast/";

  private PackKeys() {
  }

  /** The manifest key of {@code skill}'s kit: its pages, ribbon, tooltip style and toast icon. */
  public static String kit(Id skill) {
    return skill.toString();
  }

  /**
   * The skill whose kit dresses leaf {@code leaf} of a page for {@code key}, a skill id or line key as views carry it,
   * or a kit id: the skill itself, or for a mutated skill's line key the lead on even leaves and the partner on odd
   * ones. Null when the key names nothing.
   */
  public static Id kitSkill(String key, int leaf) {
    SkillPair mutation = PairKeys.parse(key);
    if (mutation != null) {
      return leaf % 2 == 0 ? mutation.lead() : mutation.partner();
    }
    return IconSprites.contentId(key);
  }

  /** The font and model path, in {@code skill}'s namespace, of its page layout {@code layout}. */
  public static Id page(Id skill, String layout) {
    return Id.of(skill.namespace(), PAGE + skill.path() + "/" + layout);
  }

  /** The item model path, in {@code skill}'s namespace, of its toast icon. */
  public static Id toast(Id skill) {
    return Id.of(skill.namespace(), TOAST + skill.path());
  }

  /** The pack sprite id ({@code ns:gui/skill/<file>}) of {@code skill}'s icon, the one its toast icon frames. */
  public static String skillSprite(Id skill) {
    return PackSprites.packId(IconSprites.skill(skill));
  }
}
