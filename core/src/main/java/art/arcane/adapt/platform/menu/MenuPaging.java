package art.arcane.adapt.platform.menu;

/** The page a paged menu shows: {@code page} counts from 0, {@code total} counts the entries on all pages. */
public record MenuPaging(int page, int pages, int total) {
  public MenuPaging {
    if (pages < 1 || page < 0 || page >= pages || total < 0) {
      throw new IllegalArgumentException("bad paging " + page + "/" + pages + " of " + total);
    }
  }
}
