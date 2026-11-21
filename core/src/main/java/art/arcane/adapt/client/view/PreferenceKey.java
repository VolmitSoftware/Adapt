package art.arcane.adapt.client.view;

import java.util.Comparator;
import java.util.Objects;

public record PreferenceKey(PreferenceOwner owner, String ownerId, String preferenceId) implements Comparable<PreferenceKey> {
  private static final Comparator<PreferenceKey> ORDER = Comparator.comparing(PreferenceKey::owner)
      .thenComparing(PreferenceKey::ownerId)
      .thenComparing(PreferenceKey::preferenceId);

  public PreferenceKey {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(ownerId, "ownerId");
    Objects.requireNonNull(preferenceId, "preferenceId");
  }

  @Override
  public int compareTo(PreferenceKey other) {
    return ORDER.compare(this, other);
  }
}
