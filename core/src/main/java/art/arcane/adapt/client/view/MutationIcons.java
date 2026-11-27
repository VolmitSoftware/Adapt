package art.arcane.adapt.client.view;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.asset.AssetCatalog;
import art.arcane.adapt.engine.asset.SkillLook;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.mutation.blocks.MutatedText;
import art.arcane.adapt.engine.mutation.engine.MutationAdaptation;
import art.arcane.adapt.engine.mutation.kit.Kits;
import art.arcane.adapt.platform.value.Id;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sprites of what a mutation adds: a mutated skill is its lead's emblem under its partner's trim, a mutated adaptation
 * is its recipe's glyph tinted with both parents' colours, and a signature has its own art named after it. Each keeps
 * the item it had as the fallback for clients without the art.
 */
public final class MutationIcons {
  /** Least relative luminance a glyph tint keeps, so dark parents still read on a dark medallion. */
  static final double MIN_TINT_LUMINANCE = 0.45D;
  private static final Map<String, String> SIGNATURES = new ConcurrentHashMap<>();

  private MutationIcons() {
  }

  /** {@code icon} with the mutation's tile when {@code skill} is a mutated skill, else {@code icon} unchanged. */
  public static IconRef skill(Skill<?> skill, IconRef icon) {
    return skill instanceof MutatedSkill mutated ? IconSprites.withSprite(icon, tile(mutated.getPair())) : icon;
  }

  /**
   * {@code icon} with the art of a mutated adaptation or a signature: the signature's own when the adaptation is one or
   * is replaced by one, else the recipe's tinted glyph. Any other adaptation keeps {@code icon}.
   */
  public static IconRef adaptation(Adaptation<?> adaptation, IconRef icon) {
    if (adaptation instanceof Signature<?> signature) {
      return IconSprites.withSprite(icon, signature(signature));
    }
    if (adaptation instanceof MutationAdaptation mutated) {
      Signature<?> signature = mutated.getSignature();
      if (signature != null) {
        return IconSprites.withSprite(icon, signature(signature));
      }
      if (mutated.getSkill() instanceof MutatedSkill skill) {
        MutatedText.Parents parents = skill.parents();
        return IconSprites.withSprite(icon, glyph(parents.lead(), parents.partner(), mutated.getRecipe()));
      }
    }
    return icon;
  }

  /** The lead's emblem under the partner's trim. */
  public static String tile(SkillPair pair) {
    return IconSprites.stack(IconSprites.emblem(pair.lead()), IconSprites.trim(pair.partner()));
  }

  /**
   * The recipe's lead part in the lead's colour and its partner part in the partner's, then its untinted outline; the
   * lead part comes first so the client judges the icon's brightness by it.
   */
  public static String glyph(Skill<?> lead, Skill<?> partner, Recipe recipe) {
    String name = recipe.name().toLowerCase(Locale.ROOT);
    return IconSprites.stack(IconSprites.tinted(IconSprites.texture("glyph", name + "-lead"), tint(lead)),
        IconSprites.tinted(IconSprites.texture("glyph", name + "-partner"), tint(partner)),
        IconSprites.texture("glyph", name));
  }

  /** The signature's art, shared by both lead orders of its pair: {@code signature/<english name>.png}. */
  public static String signature(Signature<?> signature) {
    return SIGNATURES.computeIfAbsent(signature.getLocalizationKey(), (String key) -> IconSprites.texture("signature",
        slug(MutatedText.message(MutatedText.ENGLISH, key + ".name"))));
  }

  /**
   * The colour a skill tints mutation glyphs with: its kit's tint, else the skill's colour, or when that is darker
   * than {@link #MIN_TINT_LUMINANCE}, the colour brought to full value and mixed toward white until it is not.
   */
  public static int tint(Skill<?> skill) {
    return lift(Kits.of(skill).tint().orElse(skill.getColor()).packed() & IconSprites.NO_TINT);
  }

  /** Every colour {@link #tint} returns for a skill registered with {@code catalog}, and white. */
  public static Set<Integer> tints(AssetCatalog catalog) {
    Set<Integer> all = new TreeSet<>();
    for (Id skill : catalog.themes()) {
      catalog.look(skill).ifPresent((SkillLook look) -> all.add(lift(look.rgb())));
    }
    all.add(IconSprites.NO_TINT);
    return all;
  }

  static double luminance(int rgb) {
    return (0.2126D * ((rgb >> 16) & 0xFF) + 0.7152D * ((rgb >> 8) & 0xFF) + 0.0722D * (rgb & 0xFF)) / 255.0D;
  }

  static int lift(int rgb) {
    if (luminance(rgb) >= MIN_TINT_LUMINANCE) {
      return rgb;
    }
    int[] channels = {(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
    int top = Math.max(1, Math.max(channels[0], Math.max(channels[1], channels[2])));
    for (int index = 0; index < 3; index++) {
      channels[index] = Math.min(255, Math.round(channels[index] * 255.0F / top));
    }
    int bright = (channels[0] << 16) | (channels[1] << 8) | channels[2];
    double light = luminance(bright);
    if (light >= MIN_TINT_LUMINANCE) {
      return bright;
    }
    double toWhite = (MIN_TINT_LUMINANCE - light) / (1.0D - light);
    for (int index = 0; index < 3; index++) {
      channels[index] = (int) Math.ceil(channels[index] + (255 - channels[index]) * toWhite);
    }
    return (channels[0] << 16) | (channels[1] << 8) | channels[2];
  }

  /** {@code name} lower-cased, apostrophes dropped and every other run of non-alphanumerics a single dash. */
  static String slug(String name) {
    return name.toLowerCase(Locale.ROOT).replace("'", "").replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
  }
}
