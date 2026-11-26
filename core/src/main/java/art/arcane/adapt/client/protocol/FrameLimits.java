package art.arcane.adapt.client.protocol;

/** Bounds every frame is checked against on encode and decode. */
public final class FrameLimits {
  public static final int MAX_C2S_BYTES = 32767;
  public static final int MAX_S2C_BYTES = 1048576;
  /** Largest single serverbound payload every server accepts (old Bukkit's plugin message limit). */
  public static final int MAX_C2S_PAYLOAD_BYTES = 32766;
  public static final int MAX_FRAGMENTS = 64;
  public static final int MAX_ID_BYTES = 256;
  public static final int MAX_TEXT_BYTES = 8192;
  public static final int MAX_INPUT_BYTES = 1024;
  public static final int MAX_SKILLS = 256;
  public static final int MAX_ADAPTATIONS = 512;
  public static final int MAX_LEVELS = 256;
  public static final int MAX_LINES = 128;
  public static final int MAX_ROW_GROUPS = 64;
  public static final int MAX_REQUIREMENTS = 64;
  public static final int MAX_TOOL_INGREDIENTS = 9;
  public static final int MAX_RECIPES = 2048;
  public static final int MAX_RECIPE_INPUTS = 9;
  public static final int MAX_RECIPE_EFFECTS = 32;
  public static final int MAX_PREFERENCES = 128;
  public static final int MAX_CHOICES = 64;
  public static final int MAX_PREFERENCE_GROUPS = 32;
  public static final int MAX_GROUP_ENTRIES = 64;
  public static final int MAX_PLAYER_ENTRIES = 8192;
  public static final int MAX_TILES = 54;
  public static final int MAX_PANEL_ROWS = 6;
  public static final int MAX_TILE_ACTIONS = 16;
  public static final int MAX_BREADCRUMB = 16;
  public static final int MAX_HUD_SLOTS = 3;
  public static final int MAX_ITEMS = 4096;
  public static final int MAX_ITEM_PAYLOAD_BYTES = 65536;
  public static final int MAX_FRAMES_PER_SECOND = 60;
  public static final int VIOLATION_LIMIT = 3;
  public static final long VIOLATION_WINDOW_MILLIS = 10_000L;
  public static final int MAX_INSIGHT_STATS = 16;
  public static final int MAX_INSIGHT_EFFECTS = 32;
  public static final int MAX_INSIGHT_MARKS = 16;

  private FrameLimits() {
  }
}
