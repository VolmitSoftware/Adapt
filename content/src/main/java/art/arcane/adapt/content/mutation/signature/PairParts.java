package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.Nullable;

/** What the pair signatures read from their parent skills: a parent's upgrade, found by its id. */
final class PairParts {
  private PairParts() {
  }

  /** The registered adaptation {@code id} when it is a {@code type}, or null. */
  static <T> @Nullable T owned(Id id, Class<T> type) {
    return AdaptApi.content().adaptation(id).filter(type::isInstance).map(type::cast).orElse(null);
  }
}
