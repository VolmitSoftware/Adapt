package art.arcane.adapt.platform;

/**
 * Minecraft release version, ordered by release: {@code 1.20.1 < 1.21.1 < 26.1.2 < 26.3}. Parsed from the version
 * name the server reports; anything after the numeric part ({@code -pre1}, {@code -R0.1-SNAPSHOT}) is ignored.
 */
public record GameVersion(int major, int minor, int patch) implements Comparable<GameVersion> {
  public static final GameVersion V1_20_1 = new GameVersion(1, 20, 1);
  public static final GameVersion V1_21_1 = new GameVersion(1, 21, 1);

  public GameVersion {
    if (major < 0 || minor < 0 || patch < 0) {
      throw new IllegalArgumentException("Negative version part: " + major + "." + minor + "." + patch);
    }
  }

  /** Parses {@code 1.20.1}, {@code 26.3} or {@code 26.1.2-rc1}; throws when the name has no leading number. */
  public static GameVersion parse(String name) {
    if (name == null) {
      throw new IllegalArgumentException("Version name is null");
    }
    int[] parts = new int[3];
    int part = 0;
    int digits = 0;
    for (int index = 0; index < name.length() && part < parts.length; index++) {
      char character = name.charAt(index);
      if (character >= '0' && character <= '9') {
        parts[part] = Math.addExact(Math.multiplyExact(parts[part], 10), character - '0');
        digits++;
      } else if (character == '.' && digits > 0) {
        part++;
        digits = 0;
      } else {
        break;
      }
    }
    if (part == 0 && digits == 0) {
      throw new IllegalArgumentException("Not a Minecraft version: " + name);
    }
    return new GameVersion(parts[0], parts[1], parts[2]);
  }

  public boolean isAtLeast(GameVersion other) {
    return compareTo(other) >= 0;
  }

  @Override
  public int compareTo(GameVersion other) {
    int byMajor = Integer.compare(major, other.major);
    if (byMajor != 0) {
      return byMajor;
    }
    int byMinor = Integer.compare(minor, other.minor);
    return byMinor != 0 ? byMinor : Integer.compare(patch, other.patch);
  }

  @Override
  public String toString() {
    return patch == 0 ? major + "." + minor : major + "." + minor + "." + patch;
  }
}
