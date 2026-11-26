package art.arcane.adapt.client.view;

import java.util.Objects;

/** The name an adaptation shows on its own skill's pages, where it differs from its catalog name. */
public record ShortNameView(String adaptationId, String name) {
  public ShortNameView {
    Objects.requireNonNull(adaptationId, "adaptationId");
    Objects.requireNonNull(name, "name");
  }
}
