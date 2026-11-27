package art.arcane.adapt.client.theme;

/**
 * How a book shown on its own, as the server pack's page is, is dressed: no spread neighbour or wide screen margin hides
 * its ends, so a solid band runs on to the cover's far end, side pieces keep {@link #APART} units clear of the corner and
 * pile pieces, and the top right corner pieces start at or right of {@code cornerFrom}, clear of the header's chips.
 */
public record Solo(int cornerFrom) {
    public static final int APART = 2;
}
