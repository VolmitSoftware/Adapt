package art.arcane.adapt.client.ui.kit;

/**
 * Colour, spacing and type tokens for every kit widget. Colours are ARGB. The default look is an open tome: ink on
 * parchment inside a leather cover, the Minecraft font with no shadow under ink, and muted gold, moss, wine and dusty
 * blue for accents, every colour taken from {@link UiRamp}. Section-sign colour codes in text are redrawn in {@link #legacyColours} so server text stays
 * readable on the page. The density is compact (14 unit controls, 4 unit padding) because the font cannot shrink, so
 * space is won through layout. Screens pass a skill's colour through {@link #withAccent(int)}.
 *
 * @param frame         thickness of a panel's cover rim, inside which padding starts
 * @param ribbon        the title ribbon's cloth
 * @param textInverse   text on dark fills such as the ribbon and learned level pips
 * @param controlHeight height of buttons, fields and tabs
 * @param tileSize      default icon tile size; a vanilla slot is 18
 * @param legacyColours the 16 colours legacy codes 0-9 and a-f draw in
 */
public record UiTheme(
        int panel,
        int panelBorder,
        int panelHighlight,
        int header,
        int surface,
        int surfaceHover,
        int surfacePressed,
        int surfaceBorder,
        int tile,
        int tileBorder,
        int text,
        int textMuted,
        int textDisabled,
        int accent,
        int destructive,
        int focus,
        int tooltip,
        int tooltipBorderTop,
        int tooltipBorderBottom,
        int scrim,
        int selection,
        int caret,
        int ribbon,
        int textInverse,
        int gap,
        int padding,
        int frame,
        int controlHeight,
        int tileSize,
        int[] legacyColours) {
    /** Section-sign colours 0-9 and a-f as ink that keeps WCAG AA on every parchment surface. */
    public static final int[] INK = {
            0xFF2E2216, 0xFF23406E, 0xFF2A4A18, 0xFF1C545A, 0xFF7E1E1A, 0xFF5E2A72, 0xFF6B440C, 0xFF5A4430,
            0xFF57493B, 0xFF2A4B86, 0xFF2E5619, 0xFF195260, 0xFF8D261D, 0xFF763078, 0xFF604807, 0xFF2E2216};

    public static final UiTheme DEFAULT = new UiTheme(
            UiRamp.PAGE[1],
            UiRamp.LEATHER[1],
            UiRamp.alpha(UiRamp.PAGE[0], 0x30),
            UiRamp.alpha(UiRamp.INK[1], 0x18),
            UiRamp.PAGE[3],
            UiRamp.PAGE[2],
            UiRamp.PAGE[4],
            UiRamp.PAGE[7],
            UiRamp.alpha(UiRamp.INK[1], 0x14),
            UiRamp.PAGE[5],
            UiRamp.INK[0],
            UiRamp.INK[1],
            UiRamp.INK[2],
            UiRamp.BLUE[1],
            UiRamp.WINE[2],
            UiRamp.BLUE[0],
            UiRamp.PAGE[0],
            UiRamp.PAGE[7],
            UiRamp.LEATHER[1],
            UiRamp.alpha(UiRamp.LEATHER[0], 0x80),
            UiRamp.BLUE[3],
            UiRamp.INK[0],
            UiRamp.LEATHER[2],
            UiRamp.PAGE[0],
            2,
            4,
            4,
            14,
            18,
            INK);

    /** The page colour, opaque: what icons and text drawn on a panel stand on. */
    public int backdrop() {
        return UiContrast.over(panel, 0xFF000000);
    }

    public UiTheme withAccent(int color) {
        return new UiTheme(panel, panelBorder, panelHighlight, header, surface, surfaceHover, surfacePressed, surfaceBorder, tile,
                tileBorder, text, textMuted, textDisabled, color, destructive, focus, tooltip, tooltipBorderTop, tooltipBorderBottom,
                scrim, selection, caret, ribbon, textInverse, gap, padding, frame, controlHeight, tileSize, legacyColours);
    }
}
