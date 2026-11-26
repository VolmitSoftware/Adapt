package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.theme.PageTheme;
import art.arcane.adapt.client.ui.UiSprites;

/** How a page's tiles find their skills' themes and the clock their hover frames run on. */
interface TileTheming {
    /** The theme of {@code skillId}, or null when themes are off or it has none. */
    PageTheme theme(UiSprites sprites, String skillId);

    /** The x of the spine a mutated page's kits meet at, for whose kit dresses a tile's medallion. */
    int fold();

    /** Milliseconds on the themes' clock. */
    long millis();

    /** Whether themes hold still. */
    boolean still();

    /** Draws {@code tile}'s frame after the page, above the clip of the list it scrolls in. */
    void defer(RowTile tile);
}
