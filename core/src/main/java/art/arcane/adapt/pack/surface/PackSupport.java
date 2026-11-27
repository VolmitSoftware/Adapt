package art.arcane.adapt.pack.surface;

/**
 * What a viewer's pack windows can do, decided by the platform before a page is laid out: change the title without
 * reopening the window, and take over the 36 player slots. {@link #NONE} gets top-rows-only pages that reopen on a
 * title change.
 */
public record PackSupport(boolean retitleInPlace, boolean lowerSlots) {
  public static final PackSupport NONE = new PackSupport(false, false);
  public static final PackSupport FULL = new PackSupport(true, true);
}
