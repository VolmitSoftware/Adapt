package art.arcane.adapt.pack.layout;

/**
 * Where a screen shows its crumbs on the kit's ribbon: on every page, or only on a mutated skill's pages, whose long name
 * the first row may not show in full. Elsewhere the ribbon would hide the left half of the kit's top band for a name row
 * 0 already shows.
 */
public enum CrumbRibbon {
  ALWAYS,
  MUTATED;

  public boolean shows(boolean mutated) {
    return this == ALWAYS || mutated;
  }
}
