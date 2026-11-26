package art.arcane.adapt.client.view;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillMutation;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.signature.Signature;
import art.arcane.adapt.api.skill.Skill;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sprites of what a mutation adds: a mutated skill is its lead's emblem under its partner's trim, a mutated adaptation
 * is its recipe's glyph tinted with both parents' colours, and a signature has its own art named after it. Each keeps
 * the item it had as the fallback for clients without the art.
 */
public final class MutationIcons {
  /** Least relative luminance a glyph tint keeps, so dark parents still read on a dark medallion. */
  static final double MIN_TINT_LUMINANCE = 0.45D;
  private static final Map<String, Integer> COLOURS = Map.ofEntries(
      Map.entry("agility", 0x9cd86a),
      Map.entry("architect", 0xf6ecd0),
      Map.entry("axes", 0xe8946a),
      Map.entry("blocking", 0x4a68b0),
      Map.entry("brewing", 0xd8a8e8),
      Map.entry("chronos", 0x7ae0d0),
      Map.entry("crafting", 0xc08450),
      Map.entry("discovery", 0xd8f4ff),
      Map.entry("enchanting", 0xffa8d8),
      Map.entry("excavation", 0xb07048),
      Map.entry("golemancy", 0x78c8a8),
      Map.entry("herbalism", 0x5ee89a),
      Map.entry("hunter", 0xe8604c),
      Map.entry("kinetics", 0xffe68a),
      Map.entry("nether", 0xf08a30),
      Map.entry("pickaxe", 0xb8c2cc),
      Map.entry("ranged", 0x78c048),
      Map.entry("rift", 0xc89af0),
      Map.entry("seaborne", 0x6ab8f0),
      Map.entry("stealth", 0xb0aabc),
      Map.entry("swords", 0xeef2f4),
      Map.entry("taming", 0xcfc4b0),
      Map.entry("tragoul", 0xc8282c),
      Map.entry("unarmed", 0xf0b898));
  private static final Map<String, Integer> TINTS = lifted();
  private static final Map<String, String> SIGNATURES = new ConcurrentHashMap<>();

  private MutationIcons() {
  }

  /** {@code icon} with the mutation's tile when {@code skill} is a mutated skill, else {@code icon} unchanged. */
  public static IconRef skill(Skill<?> skill, IconRef icon) {
    return skill instanceof MutatedSkill mutated ? IconSprites.withSprite(icon, tile(mutated.getMutation())) : icon;
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
      return IconSprites.withSprite(icon, signature != null ? signature(signature) : glyph(mutated.getMutation(), mutated.getRecipe()));
    }
    return icon;
  }

  /** The lead's emblem under the partner's trim. */
  public static String tile(SkillMutation mutation) {
    return IconSprites.stack(IconSprites.texture("emblem", mutation.lead()), IconSprites.texture("trim", mutation.partner()));
  }

  /**
   * The recipe's lead part in the lead's colour and its partner part in the partner's, then its untinted outline; the
   * lead part comes first so the client judges the icon's brightness by it.
   */
  public static String glyph(SkillMutation mutation, Recipe recipe) {
    String name = recipe.name().toLowerCase(Locale.ROOT);
    return IconSprites.stack(IconSprites.tinted(IconSprites.texture("glyph", name + "-lead"), tint(mutation.lead())),
        IconSprites.tinted(IconSprites.texture("glyph", name + "-partner"), tint(mutation.partner())),
        IconSprites.texture("glyph", name));
  }

  /** The signature's art, shared by both lead orders of its pair: {@code signature/<english name>.png}. */
  public static String signature(Signature<?> signature) {
    return SIGNATURES.computeIfAbsent(signature.getRow().name(), (String name) -> IconSprites.texture("signature", slug(name)));
  }

  /**
   * The colour a skill tints mutation glyphs with: its trim colour, or when that is darker than
   * {@link #MIN_TINT_LUMINANCE}, the colour brought to full value and mixed toward white until it is not. White for a
   * skill without one.
   */
  public static int tint(String skillId) {
    return TINTS.getOrDefault(skillId, IconSprites.NO_TINT);
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

  private static Map<String, Integer> lifted() {
    Map<String, Integer> lifted = new HashMap<>();
    COLOURS.forEach((String skill, Integer colour) -> lifted.put(skill, lift(colour)));
    return Map.copyOf(lifted);
  }

  /** {@code name} lower-cased, apostrophes dropped and every other run of non-alphanumerics a single dash. */
  static String slug(String name) {
    return name.toLowerCase(Locale.ROOT).replace("'", "").replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
  }
}
