package art.arcane.adapt.api.mutation.blocks;

/**
 * The five fixed recipes that turn two skills' blocks into a mutation's own adaptations.
 */
public enum Recipe {
  GRAFT("Graft", MutationPrice.GRAFT),
  ECHO("Echo", MutationPrice.GRAFT),
  BARTER("Barter", MutationPrice.BARTER),
  ATTUNEMENT("Attunement", MutationPrice.ATTUNEMENT),
  CONVERGENCE("Convergence", MutationPrice.CONVERGENCE);

  private final String displayName;
  private final MutationPrice price;

  Recipe(String displayName, MutationPrice price) {
    this.displayName = displayName;
    this.price = price;
  }

  public String displayName() {
    return displayName;
  }

  public MutationPrice price() {
    return price;
  }
}
