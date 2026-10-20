package art.arcane.adapt.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

public final class NodeEditorMessages {
  public static final TextKey HOLD_CROOK = TextKey.of("golemancy.node.editor.hold_crook", "Hold your Artificer's Crook to shift this area.");
  public static final TextKey SHIFT_STARTED = TextKey.of("golemancy.node.editor.shift_started", "Area shift mode: sneak and scroll to move along your look direction. Double-sneak to finish.");
  public static final TextKey SHIFT_STOPPED = TextKey.of("golemancy.node.editor.shift_stopped", "Area shift mode finished.");
  public static final TextKey SHIFT_STATUS = TextKey.of("golemancy.node.editor.shift_status", "Shift area [{axis}] | Sneak + scroll | Double-sneak to finish");
  public static final TextKey SHIFT_BLOCKED = TextKey.of("golemancy.node.editor.shift_blocked", "The node must stay inside the area, within your unlocked reach.");
  public static final TextKey WORLD_LIMIT = TextKey.of("golemancy.node.editor.world_limit", "The area must stay within the world's height limits.");
  public static final TextKey SHIFT_TITLE = TextKey.of("golemancy.node.editor.shift_title", "Shift work area");
  public static final TextKey SHIFT_LORE = TextKey.of("golemancy.node.editor.shift_lore", "Double-sneak inside this area with your Crook, then sneak + scroll.");
  public static final TextKey SIZE = TextKey.of("golemancy.node.editor.size", "Size: {x} x {y} x {z} blocks");
  public static final TextKey SIZE_LORE = TextKey.of("golemancy.node.editor.size_lore", "Left: add 1 block. Right: remove 1 block. Shift: 4 blocks.");
  public static final TextKey SIZE_CAP = TextKey.of("golemancy.node.editor.size_cap", "Maximum: 16 x 16 x 16. Face reach: {radius} blocks.");
  public static final TextKey RESIZE_PREVIEW = TextKey.of("golemancy.node.editor.resize_preview", "Left: {grow} blocks | Right: {shrink} blocks on {axis}");
  public static final TextKey SIZE_LIMIT = TextKey.of("golemancy.node.editor.size_limit", "{size} (size or reach limit)");
  public static final TextKey SIZE_WORLD_LIMIT = TextKey.of("golemancy.node.editor.size_world_limit", "{size} (world height limit)");
  public static final TextKey CHANNEL_PICK = TextKey.of("golemancy.node.editor.channel_pick", "Click to choose a channel.");
  public static final TextKey CHANNEL_TITLE = TextKey.of("golemancy.node.editor.channel_title", "Choose node channel");
  public static final TextKey CHANNEL_CLEAR = TextKey.of("golemancy.node.editor.channel_clear", "No channel");
  public static final TextKey COPY = TextKey.of("golemancy.node.editor.copy", "Copy node settings");
  public static final TextKey COPY_LORE = TextKey.of("golemancy.node.editor.copy_lore", "Copies channel, priority, redstone, size and filters for another node of this type.");
  public static final TextKey COPIED = TextKey.of("golemancy.node.editor.copied", "Node settings copied for this session.");
  public static final TextKey PASTE = TextKey.of("golemancy.node.editor.paste", "Paste node settings");
  public static final TextKey PASTE_LORE = TextKey.of("golemancy.node.editor.paste_lore", "Applies copied common settings. Role options stay with this node.");
  public static final TextKey PASTED = TextKey.of("golemancy.node.editor.pasted", "Node settings pasted.");
  public static final TextKey PASTE_INVALID = TextKey.of("golemancy.node.editor.paste_invalid", "Copy settings from one of your nodes of this type. All settings must fit your current unlocks and world limits.");
  public static final TextKey FILTER_SAMPLE = TextKey.of("golemancy.node.editor.filter_sample", "Click with a cursor item to copy a sample. Your item is kept.");
  public static final TextKey FILTER_COPIED = TextKey.of("golemancy.node.editor.filter_copied", "Filter sample copied. Your item was kept.");
  public static final TextKey FILTER_EMPTY_CURSOR = TextKey.of("golemancy.node.editor.filter_empty_cursor", "Pick up an item on your cursor, then click a filter slot to copy its sample.");

  private NodeEditorMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.add(HOLD_CROOK).add(SHIFT_STARTED).add(SHIFT_STOPPED).add(SHIFT_STATUS).add(SHIFT_BLOCKED).add(WORLD_LIMIT)
        .add(SHIFT_TITLE).add(SHIFT_LORE).add(SIZE).add(SIZE_LORE).add(SIZE_CAP).add(RESIZE_PREVIEW).add(CHANNEL_PICK)
        .add(SIZE_LIMIT).add(SIZE_WORLD_LIMIT).add(CHANNEL_TITLE).add(CHANNEL_CLEAR).add(COPY).add(COPY_LORE).add(COPIED).add(PASTE).add(PASTE_LORE).add(PASTED)
        .add(PASTE_INVALID).add(FILTER_SAMPLE).add(FILTER_COPIED).add(FILTER_EMPTY_CURSOR);
  }
}
