package art.arcane.adapt.client.protocol;

/** The page a paged menu shows: {@code page} counts from 0, {@code total} is the number of entries on all pages. */
public record PanelPaging(int page, int pages, int total) {
  public PanelPaging {
    if (pages < 1 || page < 0 || page >= pages || total < 0) {
      throw new IllegalArgumentException("bad paging " + page + "/" + pages + " of " + total);
    }
  }
}
