package art.arcane.adapt.api.advancement;

/** When an ungranted advancement shows in its tab. A granted advancement is always visible. */
public enum AdvancementVisibility {
  /** Always visible. */
  ALWAYS(art.arcane.adapt.platform.advancement.AdvancementVisibility.ALWAYS),
  /** Visible once the parent is granted. */
  PARENT_GRANTED(art.arcane.adapt.platform.advancement.AdvancementVisibility.PARENT_GRANTED),
  /** Visible only once granted, like vanilla hidden advancements. */
  HIDDEN(art.arcane.adapt.platform.advancement.AdvancementVisibility.HIDDEN),
  /** Visible once the parent or grandparent is granted, like vanilla advancements. */
  VANILLA(art.arcane.adapt.platform.advancement.AdvancementVisibility.VANILLA);

  private final art.arcane.adapt.platform.advancement.AdvancementVisibility platform;

  AdvancementVisibility(art.arcane.adapt.platform.advancement.AdvancementVisibility platform) {
    this.platform = platform;
  }

  public art.arcane.adapt.platform.advancement.AdvancementVisibility platform() {
    return platform;
  }
}
