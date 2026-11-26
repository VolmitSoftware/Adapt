package art.arcane.adapt.client.protocol;

/** Channel, version and capability bits of the optional Adapt client protocol. */
public final class ClientProtocol {
  public static final String NAMESPACE = "adapt";
  public static final String PATH = "ui";
  public static final String CHANNEL = NAMESPACE + ":" + PATH;
  public static final int VERSION = 1;

  public static final int SCREENS = 1;
  public static final int PANEL = 1 << 1;
  public static final int HUD = 1 << 2;
  public static final int TOASTS = 1 << 3;
  public static final int ITEMS = 1 << 4;
  public static final int INSIGHT = 1 << 5;
  /** The client reports its jump key ({@link ClientFrame.JumpKey}); only served where the server sees no jump input. */
  public static final int JUMPS = 1 << 6;
  /**
   * The server says which skill pages offer Mutate ({@link ServerFrame.MutateOffers}) and the client asks to start one
   * ({@link ClientFrame.Mutate}). Needs {@link #PANEL}, since the server answers with its menus.
   */
  public static final int MUTATE = 1 << 7;
  /**
   * The server lists the originals each mutated skill keeps ({@link ServerFrame.MutationKept}) instead of appending
   * them to the skill's description.
   */
  public static final int KEPT = 1 << 8;
  /** The server lists the mutation tools each adaptation hands out ({@link ServerFrame.MutationTools}). */
  public static final int TOOLS = 1 << 9;
  /**
   * Mutate opens the client's own spread ({@link Route.Mutate}), which asks for previews and confirms with
   * {@link ClientFrame.MutationRequest} and is answered with {@link ServerFrame.MutationSpread}. Needs {@link #MUTATE};
   * without it the server answers Mutate with its menus.
   */
  public static final int MUTATE_SPREAD = 1 << 10;
  /** The server says how each skill the player sees earns XP ({@link ServerFrame.XpGuides}). */
  public static final int XP_GUIDE = 1 << 11;
  /**
   * The server lists what the player spends knowledge and power on and whether inactive skills may show
   * ({@link ServerFrame.Spending}).
   */
  public static final int SPENDING = 1 << 12;
  /**
   * The client may ask to undo a mutation ({@link ClientFrame.Unmutate}); the server accepts it only from a player in
   * debug mode or with the admin permission.
   */
  public static final int UNMUTATE = 1 << 13;
  /** Toasts may name their skill and be signatures ({@link ServerFrame.Toast}); needs {@link #TOASTS}. */
  public static final int SKILL_TOASTS = 1 << 14;
  /** The server names each skill's adaptation groups ({@link ServerFrame.GroupLabels}). */
  public static final int GROUP_LABELS = 1 << 15;
  /** The server names adaptations as their own skill's pages show them ({@link ServerFrame.ShortNames}). */
  public static final int SHORT_NAMES = 1 << 16;
  /** The server lists the recipes each adaptation unlocks ({@link ServerFrame.AdaptationRecipes}). */
  public static final int RECIPES = 1 << 17;
  public static final int ALL = SCREENS | PANEL | HUD | TOASTS | ITEMS | INSIGHT | JUMPS | MUTATE | KEPT | TOOLS | MUTATE_SPREAD
      | XP_GUIDE | SPENDING | UNMUTATE | SKILL_TOASTS | GROUP_LABELS | SHORT_NAMES | RECIPES;

  private ClientProtocol() {
  }
}
