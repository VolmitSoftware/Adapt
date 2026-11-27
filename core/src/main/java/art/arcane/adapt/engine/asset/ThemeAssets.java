package art.arcane.adapt.engine.asset;

import art.arcane.adapt.platform.value.Id;

import java.util.Arrays;
import java.util.Objects;

/**
 * A skill's page theme as files: the kit JSON, its atlas PNG, whether the catalog generated it, and the SHA-1 hex hash
 * of the kit bytes followed by the atlas bytes.
 */
public record ThemeAssets(Id skill, byte[] kitJson, byte[] atlasPng, boolean generated, String hash) {
  public ThemeAssets {
    Objects.requireNonNull(skill, "skill");
    Objects.requireNonNull(kitJson, "kitJson");
    Objects.requireNonNull(atlasPng, "atlasPng");
    Objects.requireNonNull(hash, "hash");
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ThemeAssets that && skill.equals(that.skill) && generated == that.generated
        && hash.equals(that.hash) && Arrays.equals(kitJson, that.kitJson) && Arrays.equals(atlasPng, that.atlasPng);
  }

  @Override
  public int hashCode() {
    return Objects.hash(skill, generated, hash);
  }

  @Override
  public String toString() {
    return "ThemeAssets[" + skill + (generated ? ", generated" : "") + ", " + hash + "]";
  }
}
