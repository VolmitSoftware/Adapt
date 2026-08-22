package fr.skytasul.reflection;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public record Version(int major, int minor, int patch) implements Comparable<Version> {
  private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?.*$");
  public static final Version ZERO = new Version(0, 0, 0);

  public boolean is(int major, int minor, int patch) {
    return major() == major && minor() == minor && patch() == patch;
  }

  public boolean is(@NotNull Version version) {
    return this.equals(version);
  }

  public boolean isAfter(int major, int minor, int patch) {
    if (major() > major) {
      return true;
    }
    if (major() < major) {
      return false;
    }
    if (minor() > minor) {
      return true;
    }
    if (minor() < minor) {
      return false;
    }
    return patch() >= patch;
  }

  public boolean isAfter(@NotNull Version version) {
    return isAfter(version.major(), version.minor(), version.patch());
  }

  public boolean isBefore(int major, int minor, int patch) {
    return !isAfter(major, minor, patch);
  }

  public boolean isBefore(@NotNull Version version) {
    return isBefore(version.major(), version.minor(), version.patch());
  }

  @Override
  public int compareTo(Version o) {
    if (o.equals(this)) {
      return 0;
    }
    return isAfter(o) ? 1 : -1;
  }

  @Override
  public @NotNull String toString() {
    return toString(false);
  }

  public @NotNull String toString(boolean omitPatch) {
    if (omitPatch && patch == 0) {
      return "%d.%d".formatted(major, minor);
    }
    return "%d.%d.%d".formatted(major, minor, patch);
  }

  public static @NotNull Version parse(@NotNull String string) throws IllegalArgumentException {
    Matcher matcher = VERSION_PATTERN.matcher(string.trim());
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Malformed version: " + string);
    }

    int major = Integer.parseInt(matcher.group(1));
    int minor = Integer.parseInt(matcher.group(2));
    String patchGroup = matcher.group(3);
    int patch = patchGroup != null ? Integer.parseInt(patchGroup) : 0;
    return new Version(major, minor, patch);
  }

  public static @NotNull Version @NotNull [] parseArray(String... versions) {
    return Stream.of(versions).map(Version::parse).toArray(Version[]::new);
  }
}
