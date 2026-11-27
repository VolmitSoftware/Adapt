package art.arcane.adapt.engine.asset;

import java.util.Map;

/**
 * The emblem and trim of a skill whose addon ships none, drawn in its look's colour: a leaf emblem for a skill whose
 * icon is a plant, a shield for any other, and corner brackets for the trim. Both are 16x16 PNGs like authored art.
 */
final class MutationArtGenerator {
  private static final int SIZE = AdaptAssetCatalog.ICON_SIZE;
  private static final String[] SHIELD = {
      "................",
      "................",
      "...kkkkkkkkkk...",
      "..kqqqqqqqqqqk..",
      "..kqpppppppp1k..",
      "..kqpppppppp1k..",
      "..kqpppppppp1k..",
      "..kqpppppppp1k..",
      "..kqppppppp11k..",
      "...kqpppppp1k...",
      "...kqpppppp1k...",
      "....kqpppp1k....",
      ".....kqpp1k.....",
      "......kp1k......",
      ".......kk.......",
      "................"};
  private static final String[] LEAF = {
      "................",
      "..........kkkk..",
      "........kkqqqk..",
      "......kkqqppp1k.",
      ".....kqqppppp1k.",
      "....kqpppppp11k.",
      "...kqpppppp11k..",
      "...kqppppp11k...",
      "..kqppppp11k....",
      "..kpppp111k.....",
      ".kpp1111kk......",
      ".k11kkkk........",
      "kk.k............",
      "k...............",
      "................",
      "................"};
  private static final String[] TRIM = {
      "kkkkk......kkkkk",
      "kqqpk......kpqqk",
      "kqkkk......kkkqk",
      "kpk..........kpk",
      "kkk..........kkk",
      "................",
      "................",
      "................",
      "................",
      "................",
      "................",
      "kkk..........kkk",
      "kpk..........kpk",
      "kqkkk......kkkqk",
      "kqqpk......kpqqk",
      "kkkkk......kkkkk"};

  private MutationArtGenerator() {
  }

  static byte[] emblem(SkillLook look) {
    return draw(ThemeGenerator.organic(look.icon()) ? LEAF : SHIELD, look.rgb());
  }

  static byte[] trim(SkillLook look) {
    return draw(TRIM, look.rgb());
  }

  private static byte[] draw(String[] rows, int rgb) {
    Map<Character, Integer> palette = ThemeGenerator.palette(rgb);
    int[] pixels = new int[SIZE * SIZE];
    for (int y = 0; y < SIZE; y++) {
      for (int x = 0; x < SIZE; x++) {
        char role = rows[y].charAt(x);
        pixels[y * SIZE + x] = role == '.' ? 0 : 0xFF000000 | palette.get(role);
      }
    }
    return PngImages.encode(SIZE, SIZE, pixels);
  }
}
