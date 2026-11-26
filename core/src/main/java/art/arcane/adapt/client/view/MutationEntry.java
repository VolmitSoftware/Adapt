package art.arcane.adapt.client.view;

import java.util.Objects;

/**
 * One adaptation, kept original or tool on the mutation spread: its icon, its name as a tooltip title and
 * {@code detail} as the tooltip body (empty for none). {@code level} is a learned or kept level, 0 when it has none.
 */
public record MutationEntry(String id, IconRef icon, String name, String detail, int level) {
  public MutationEntry {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(icon, "icon");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(detail, "detail");
  }
}
