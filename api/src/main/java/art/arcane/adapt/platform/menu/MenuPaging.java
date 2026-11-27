package art.arcane.adapt.platform.menu;

import org.jetbrains.annotations.ApiStatus;

/**
 * The page a paged menu shows. The constructor throws {@link IllegalArgumentException} when {@code page} is outside
 * {@code 0..pages-1} or a count is negative.
 *
 * @param page the shown page, from 0
 * @param pages the page count, at least 1
 * @param total the entries on all pages
 */
@ApiStatus.AvailableSince("1.0.0")
public record MenuPaging(int page, int pages, int total) {
  public MenuPaging {
    if (pages < 1 || page < 0 || page >= pages || total < 0) {
      throw new IllegalArgumentException("bad paging " + page + "/" + pages + " of " + total);
    }
  }
}
