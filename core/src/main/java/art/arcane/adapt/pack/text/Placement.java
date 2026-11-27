package art.arcane.adapt.pack.text;

/**
 * What placing a label did: {@code lines} lines were drawn, {@code width} pixels wide at most. {@link Status#UNPLACEABLE}
 * drew nothing because the text uses a script the client draws from a font that cannot be moved off the title line;
 * the label then lives in the tooltip only.
 */
public record Placement(Status status, int width, int lines) {
  public static final Placement UNPLACEABLE = new Placement(Status.UNPLACEABLE, 0, 0);
  public static final Placement EMPTY = new Placement(Status.PLACED, 0, 0);

  public boolean placed() {
    return status != Status.UNPLACEABLE;
  }

  public enum Status {
    PLACED,
    TRUNCATED,
    UNPLACEABLE
  }
}
