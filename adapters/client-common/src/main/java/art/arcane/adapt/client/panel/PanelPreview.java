package art.arcane.adapt.client.panel;

import art.arcane.adapt.client.AdaptClient;
import art.arcane.adapt.client.AdaptKey;
import art.arcane.adapt.client.protocol.HintKind;
import art.arcane.adapt.client.protocol.PanelLayout;
import art.arcane.adapt.client.protocol.PanelPaging;
import art.arcane.adapt.client.protocol.PanelTile;
import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.protocol.TileAction;
import art.arcane.adapt.client.protocol.TileHint;
import art.arcane.adapt.client.protocol.TileRole;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.platform.menu.ClickKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Development preview of the native panel with no server: with {@code -Dadapt.client.preview=panel} the "Open skills"
 * key opens a sample panel holding every tile kind and hint, and the sample reacts to clicks and input like a server
 * menu would. Without the property nothing is installed.
 */
public final class PanelPreview implements PanelActions {
    public static final String PROPERTY = "adapt.client.preview";
    public static final String PANEL = "panel";
    public static final int SESSION = 1;
    public static final int PAGES = 3;
    public static final int TOGGLE_SLOT = 11;
    public static final int NUMBER_SLOT = 12;
    public static final int TEXT_SLOT = 13;
    public static final int RESET_SLOT = 14;
    public static final int CHOICE_SLOT = 17;
    public static final int LAYOUT_SLOT = 18;
    public static final int LAST_ACTION_SLOT = 40;
    public static final int BACK_SLOT = 45;
    public static final int FIRST_SLOT = 47;
    public static final int PREVIOUS_SLOT = 48;
    public static final int PAGE_SLOT = 49;
    public static final int NEXT_SLOT = 50;
    public static final int LAST_SLOT = 51;
    private static final String PANE = "minecraft:gray_stained_glass_pane";
    private static final int MAX_RANGE = 64;
    private static final List<String> MODES = List.of("NEAREST", "RANDOM", "ALL");
    private static final List<String> LAYOUTS = List.of("FORM", "LIST", "GRID");
    private static final String[] SAMPLE_ITEMS = {"minecraft:iron_pickaxe", "minecraft:golden_apple", "minecraft:oak_sapling",
            "minecraft:compass", "minecraft:bow", "minecraft:feather", "minecraft:redstone", "minecraft:emerald", "minecraft:bread",
            "minecraft:shield", "minecraft:fishing_rod", "minecraft:ender_pearl", "minecraft:cake"};

    private PanelScreens screens;
    private int revision;
    private int page;
    private boolean toggle = true;
    private int range = 8;
    private String nickname = "Steve";
    private String mode = "NEAREST";
    private String layout = "FORM";
    private String lastAction = "none";

    /** Binds the preview to the "Open skills" key when the {@value #PROPERTY} system property is {@value #PANEL}. */
    public static void installIfRequested(AdaptClient client) {
        if (!PANEL.equals(System.getProperty(PROPERTY))) {
            return;
        }
        PanelPreview preview = new PanelPreview();
        preview.screens = new PanelScreens(client.host(), preview);
        client.bindKey(AdaptKey.OPEN_SKILLS, preview::open);
    }

    public void open() {
        screens.open(view());
    }

    /** The sample as it stands now. */
    public PanelView view() {
        List<PanelTile> tiles = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            if (slot != 4) {
                tiles.add(tile(slot, IconRef.item(PANE), 1, " ", List.of(), false, null, TileRole.DECORATION, "", "", List.of()));
            }
        }
        tiles.add(tile(4, IconRef.item("minecraft:enchanted_book"), 1, "§b§lPanel preview",
                List.of("§7Every tile kind the native panel draws.", "§8Page " + (page + 1) + " of " + PAGES), true, null, TileRole.INFO,
                "", "", List.of()));
        tiles.add(tile(10, IconRef.item("minecraft:diamond"), 16, "§fDiamonds", List.of("§7A stack with an amount."), false, null,
                TileRole.INFO, "Items", "16", List.of()));
        tiles.add(tile(TOGGLE_SLOT, IconRef.item("minecraft:lever"), 1, "§eAuto pickup", List.of("§7Picks up items you walk over."),
                false, TileHint.of(HintKind.TOGGLE, toggle ? "ON" : "OFF"), TileRole.SETTING, "Settings", "",
                List.of(new TileAction(ClickKind.LEFT, "Left click: toggle"))));
        tiles.add(tile(NUMBER_SLOT, IconRef.item("minecraft:clock"), 1, "§eRange", List.of("§7How far pickup reaches."), false,
                new TileHint(HintKind.NUMBER, Integer.toString(range), true, List.of(), 0D, (double) MAX_RANGE, 1D), TileRole.SETTING,
                "Settings", "", List.of()));
        tiles.add(tile(TEXT_SLOT, IconRef.item("minecraft:name_tag"), 1, "§eNickname", List.of("§7Shown above your head."), false,
                new TileHint(HintKind.TEXT, nickname, true, List.of(), null, null, null), TileRole.SETTING, "Settings", "", List.of()));
        tiles.add(tile(CHOICE_SLOT, IconRef.item("minecraft:compass"), 1, "§eMode", List.of("§7How pickup picks."), false,
                new TileHint(HintKind.CHOICE, mode, true, MODES, null, null, null), TileRole.SETTING, "Settings", "",
                List.of(new TileAction(ClickKind.LEFT, "Left click: next value"), new TileAction(ClickKind.RIGHT, "Right click: previous value"))));
        tiles.add(tile(LAYOUT_SLOT, IconRef.item("minecraft:item_frame"), 1, "§eLayout", List.of("§7How this panel is drawn."), false,
                new TileHint(HintKind.CHOICE, layout, true, LAYOUTS, null, null, null), TileRole.SETTING, "Settings", "",
                List.of(new TileAction(ClickKind.LEFT, "Left click: next value"), new TileAction(ClickKind.RIGHT, "Right click: previous value"))));
        tiles.add(tile(RESET_SLOT, IconRef.item("minecraft:barrier"), 1, "§cReset everything", List.of("§7Puts every sample value back."),
                false, TileHint.of(HintKind.DESTRUCTIVE, "Reset"), TileRole.ACTION, "Settings", "", List.of(new TileAction(ClickKind.LEFT, ""))));
        tiles.add(tile(15, new IconRef("minecraft:paper", 1001, null, null), 1, "§fCustom model",
                List.of("§7Paper with custom model data 1001."), false, null, TileRole.INFO, "Items", "1001", List.of()));
        tiles.add(tile(16, IconRef.item("minecraft:writable_book"), 1, "§6A long description",
                List.of("§7This lore line is long enough that the row has to wrap it onto more than one line on small screens.",
                        "", "§8Blank line above."), false, null, TileRole.ENTRY, "Items", "",
                List.of(new TileAction(ClickKind.LEFT, ""), new TileAction(ClickKind.SHIFT_RIGHT, "Remove"))));
        int first = 19 + page * 3;
        for (int index = 0; index < 7; index++) {
            tiles.add(tile(19 + index, IconRef.item(SAMPLE_ITEMS[(first + index) % SAMPLE_ITEMS.length]), index + 1,
                    "§fEntry " + (page * 7 + index + 1), List.of("§7Page " + (page + 1) + " entry."), index == 3, null, TileRole.NAVIGATION,
                    "Entries", "", List.of(new TileAction(ClickKind.LEFT, ""))));
        }
        tiles.add(tile(LAST_ACTION_SLOT, IconRef.item("minecraft:spyglass"), 1, "§fLast action", List.of("§7" + lastAction), false, null,
                TileRole.INFO, "", "", List.of()));
        tiles.add(navigation(BACK_SLOT, "minecraft:arrow", "Back", "back"));
        if (page > 0) {
            tiles.add(navigation(FIRST_SLOT, "minecraft:spectral_arrow", "First page", "first"));
            tiles.add(navigation(PREVIOUS_SLOT, "minecraft:arrow", "Previous page", "previous"));
        }
        tiles.add(navigation(PAGE_SLOT, "minecraft:map", "§fPage " + (page + 1) + "/" + PAGES, "page-info"));
        if (page < PAGES - 1) {
            tiles.add(navigation(NEXT_SLOT, "minecraft:arrow", "Next page", "next"));
            tiles.add(navigation(LAST_SLOT, "minecraft:spectral_arrow", "Last page", "last"));
        }
        return new PanelView(SESSION, revision, "§fPanel preview", 6, PanelLayout.valueOf(layout), new PanelPaging(page, PAGES, PAGES * 7),
                List.of("Adapt"), tiles);
    }

    private static PanelTile tile(int slot, IconRef icon, int amount, String name, List<String> lore, boolean glint, TileHint hint,
                                  TileRole role, String section, String value, List<TileAction> actions) {
        return new PanelTile(slot, icon, amount, name, lore, glint, hint, role, section, value, null, actions);
    }

    @Override
    public void click(int sessionId, int clickRevision, int slot, ClickKind kind) {
        lastAction = "click " + slot + " " + kind;
        int step = kind.isShift() ? 10 : 1;
        switch (slot) {
            case TOGGLE_SLOT -> toggle = !toggle;
            case NUMBER_SLOT -> range = Math.max(0, Math.min(MAX_RANGE, range + (kind.isRight() ? -step : kind.isLeft() ? step : 0)));
            case CHOICE_SLOT -> mode = MODES.get(Math.floorMod(MODES.indexOf(mode) + (kind.isRight() ? -1 : 1), MODES.size()));
            case LAYOUT_SLOT -> layout = LAYOUTS.get(Math.floorMod(LAYOUTS.indexOf(layout) + (kind.isRight() ? -1 : 1), LAYOUTS.size()));
            case RESET_SLOT -> {
                toggle = true;
                range = 8;
                nickname = "Steve";
                mode = "NEAREST";
                layout = "FORM";
            }
            case FIRST_SLOT -> page = 0;
            case PREVIOUS_SLOT -> page = Math.max(0, page - 1);
            case NEXT_SLOT -> page = Math.min(PAGES - 1, page + 1);
            case LAST_SLOT -> page = PAGES - 1;
            case BACK_SLOT -> page = 0;
            default -> {
            }
        }
        publish();
    }

    @Override
    public void input(int sessionId, int inputRevision, int slot, String text) {
        lastAction = "input " + slot + " " + text;
        if (slot == NUMBER_SLOT) {
            try {
                range = Math.max(0, Math.min(MAX_RANGE, Integer.parseInt(text.trim())));
            } catch (NumberFormatException invalid) {
                lastAction = "input " + slot + " rejected";
            }
        } else if (slot == TEXT_SLOT) {
            nickname = text;
        } else if (slot == CHOICE_SLOT && MODES.contains(text)) {
            mode = text;
        } else if (slot == LAYOUT_SLOT && LAYOUTS.contains(text)) {
            layout = text;
        }
        publish();
    }

    @Override
    public void closed(int sessionId) {
        lastAction = "closed";
    }

    private void publish() {
        revision++;
        if (screens != null) {
            screens.update(view());
        }
    }

    private static PanelTile navigation(int slot, String item, String name, String target) {
        return tile(slot, IconRef.item(item), 1, "§f" + name, List.of(), false, TileHint.of(HintKind.NAVIGATION, target),
                TileRole.NAVIGATION, "", "", List.of(new TileAction(ClickKind.LEFT, "")));
    }
}
