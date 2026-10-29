package art.arcane.adapt.api.claims;

import java.util.Objects;

public record ClaimWorldAccess(String name, boolean overworld) {
  public ClaimWorldAccess {
    Objects.requireNonNull(name);
  }
}
