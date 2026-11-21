package art.arcane.adapt.client.view;

import java.util.List;
import java.util.Objects;

/** Every skill and adaptation, with text in {@code locale}. Valid while the registry revision and locale hold. */
public record CatalogView(long revision, String locale, List<SkillView> skills) {
  public CatalogView {
    Objects.requireNonNull(locale, "locale");
    skills = List.copyOf(skills);
  }
}
