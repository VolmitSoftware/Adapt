package art.arcane.adapt.engine.advancement;

import art.arcane.adapt.api.text.TextKeys;
import art.arcane.adapt.platform.value.Id;

/** Turns an advancement a component declares through the API into the tree the advancement engine grants. */
public final class AdvancementTrees {
  private AdvancementTrees() {
  }

  /**
   * {@code declared} as the engine registers it for the content with id {@code owner}: its key through
   * {@link AdaptAdvancement#contentKey}, an unset title or description read from {@link TextKeys#advancement}.
   */
  public static AdaptAdvancement of(Id owner, art.arcane.adapt.api.advancement.AdaptAdvancement declared) {
    AdaptAdvancement.AdaptAdvancementBuilder builder = AdaptAdvancement.builder()
        .key(AdaptAdvancement.contentKey(owner, declared.key()))
        .textKey(TextKeys.advancement(owner, declared.key()))
        .icon(declared.icon().item())
        .frame(declared.frame())
        .visibility(declared.visibility())
        .toast(declared.toast())
        .announce(declared.announce());
    declared.title().ifPresent(title -> builder.title(title.resolve()));
    declared.description().ifPresent(description -> builder.description(description.resolve()));
    for (art.arcane.adapt.api.advancement.AdaptAdvancement child : declared.children()) {
      builder.child(of(owner, child));
    }
    return builder.build();
  }
}
