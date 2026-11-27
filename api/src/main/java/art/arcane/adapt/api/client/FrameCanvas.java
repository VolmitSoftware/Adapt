package art.arcane.adapt.api.client;

import org.jetbrains.annotations.ApiStatus;

/**
 * What a hover frame draws with: a ring round the tile.
 *
 * <p>A position {@code s} runs clockwise along the tile's outline from its top-left corner, one unit per GUI
 * pixel, up to {@link #ringLength()}. An offset says how far outside the outline a pixel sits; -1 is one pixel
 * inside. Pixels are drawn in the role colours of the skill's theme kit, and sprites are the frame sprites of
 * the kit's atlas, turned to face out from the side they sit on.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface FrameCanvas {
  /** The ring's length: twice the tile's width plus twice its height. */
  int ringLength();

  /** The tile's width in GUI pixels. */
  int width();

  /** The tile's height in GUI pixels. */
  int height();

  /** Ring positions of the four corners, clockwise from the top left, which is 0. */
  int topLeft();

  /** Ring position of the top-right corner. */
  int topRight();

  /** Ring position of the bottom-right corner. */
  int bottomRight();

  /** Ring position of the bottom-left corner. */
  int bottomLeft();

  /** Ring positions of the four side middles. */
  int topMiddle();

  /** Ring position of the middle of the right side. */
  int rightMiddle();

  /** Ring position of the middle of the bottom side. */
  int bottomMiddle();

  /** Ring position of the middle of the left side. */
  int leftMiddle();

  /** Screen coordinates of the tile's first and last pixel columns and rows. */
  int left();

  /** Screen y of the tile's first pixel row. */
  int top();

  /** Screen x of the tile's last pixel column. */
  int right();

  /** Screen y of the tile's last pixel row. */
  int bottom();

  /** How far the frame has assembled, 0 to 1. */
  double assembled();

  /** The idle clock in seconds since the hover began. */
  double seconds();

  /** True while the pointer has left and the frame is going away. */
  boolean leaving();

  /** The side a ring position lies on: 0 top, 1 right, 2 bottom, 3 left. */
  int side(double s);

  /** Progress of the assembly between {@code from} and {@code to}, clamped to 0 to 1. */
  double progress(double from, double to);

  /** One pixel in a role colour of the kit's palette. */
  void pixel(double s, int offset, char role);

  /** As {@link #pixel(double, int, char)}, drawn with opacity {@code alpha}, 0 to 1. */
  void pixel(double s, int offset, char role, double alpha);

  /** One pixel in an exact ARGB colour. */
  void pixelArgb(double s, int offset, int argb, double alpha);

  /** Fresh sprite options for the next {@link #sprite(String, double, SpriteOptions)} call. */
  SpriteOptions options();

  /** A frame sprite of the kit on the ring at {@code s}, facing outward. */
  void sprite(String key, double s);

  /** As {@link #sprite(String, double)}, placed by {@code options}. A key the kit lacks draws nothing. */
  void sprite(String key, double s, SpriteOptions options);

  /** A frame sprite as drawn in the atlas, with its top left at screen position (x, y). */
  void raw(String key, double x, double y);

  /** As {@link #raw(String, double, double)}, drawn with opacity {@code alpha}, 0 to 1. */
  void raw(String key, double x, double y, double alpha);

  /** As {@link #raw(String, double, double, double)}, with the sprite's colours multiplied by {@code rgb}. */
  void raw(String key, double x, double y, double alpha, int rgb);

  /** How a sprite is placed on the ring. Each setter returns the same object. */
  @ApiStatus.NonExtendable
  interface SpriteOptions {
    /** Mirrors the sprite before it is turned to its side. */
    SpriteOptions flip(boolean flipped);

    /** Keeps the sprite upright on every side instead of turning it outward. */
    SpriteOptions upright();

    /** Moves the sprite this many pixels into the outline. */
    SpriteOptions sink(int pixels);

    /** Moves the sprite this far outward along the side's normal. */
    SpriteOptions outward(double pixels);

    /** Shifts the sprite this many pixels along the screen's x axis. */
    SpriteOptions dx(double pixels);

    /** Shifts the sprite this many pixels along the screen's y axis. */
    SpriteOptions dy(double pixels);

    /** The sprite's opacity, 0 to 1. */
    SpriteOptions alpha(double alpha);

    /**
     * Multiplies the sprite's colours by {@code rgb}, {@code 0xRRGGBB}; the bits above the low 24 are ignored. The
     * default {@code 0xFFFFFF} draws the sprite as it is in the atlas.
     */
    SpriteOptions tint(int rgb);
  }
}
