/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.util.common.format;

import art.arcane.adapt.platform.value.Rgb;
import art.arcane.volmlib.util.format.ColorFormatter;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Legacy chat colours and formats. Dye colours are keyed by their upper-case vanilla name, e.g. {@code LIGHT_BLUE}.
 */
public enum C {
  BLACK('0', 0x00),
  DARK_BLUE('1', 0x1),
  DARK_GREEN('2', 0x2),
  DARK_AQUA('3', 0x3),
  DARK_RED('4', 0x4),
  ADAPT('4', 0x4),
  DARK_PURPLE('5', 0x5),
  GOLD('6', 0x6),
  GRAY('7', 0x7),
  DARK_GRAY('8', 0x8),
  BLUE('9', 0x9),
  GREEN('a', 0xA),
  AQUA('b', 0xB),
  RED('c', 0xC),
  LIGHT_PURPLE('d', 0xD),
  YELLOW('e', 0xE),
  WHITE('f', 0xF),
  MAGIC("<obf>", 'k', 0x10, true),
  BOLD('l', 0x11, true),
  STRIKETHROUGH('m', 0x12, true),
  UNDERLINE("<underlined>", 'n', 0x13, true),
  ITALIC('o', 0x14, true),
  RESET('r', 0x15);

  public static final char COLOR_CHAR = '§';
  public final static C[] COLORCYCLE = new C[]{C.GOLD, C.YELLOW, C.GREEN, C.AQUA, C.LIGHT_PURPLE, C.AQUA, C.GREEN, C.YELLOW, C.GOLD, C.RED};
  private final static C[] COLORS = new C[]{C.BLACK, C.DARK_BLUE, C.DARK_GREEN, C.DARK_AQUA, C.DARK_RED, C.DARK_PURPLE, C.GOLD, C.GRAY, C.DARK_GRAY, C.BLUE, C.GREEN, C.AQUA, C.RED, C.LIGHT_PURPLE, C.YELLOW, C.WHITE};
  private final static Map<Integer, C> BY_ID = new HashMap<>();
  private final static Map<Character, C> BY_CHAR = new HashMap<>();
  private final static Map<String, C> dyeChatMap = new LinkedHashMap<>();
  private final static Map<C, String> chatHexMap = new HashMap<>();
  private final static Map<String, String> dyeHexMap = new LinkedHashMap<>();

  static {
    chatHexMap.put(C.BLACK, "#000000");
    chatHexMap.put(C.DARK_BLUE, "#0000AA");
    chatHexMap.put(C.DARK_GREEN, "#00AA00");
    chatHexMap.put(C.DARK_AQUA, "#00AAAA");
    chatHexMap.put(C.DARK_RED, "#AA0000");
    chatHexMap.put(C.ADAPT, "#AA0000");
    chatHexMap.put(C.DARK_PURPLE, "#AA00AA");
    chatHexMap.put(C.GOLD, "#FFAA00");
    chatHexMap.put(C.GRAY, "#AAAAAA");
    chatHexMap.put(C.DARK_GRAY, "#555555");
    chatHexMap.put(C.BLUE, "#5555FF");
    chatHexMap.put(C.GREEN, "#55FF55");
    chatHexMap.put(C.AQUA, "#55FFFF");
    chatHexMap.put(C.RED, "#FF5555");
    chatHexMap.put(C.LIGHT_PURPLE, "#FF55FF");
    chatHexMap.put(C.YELLOW, "#FFFF55");
    chatHexMap.put(C.WHITE, "#FFFFFF");
    dyeChatMap.put("BLACK", C.DARK_GRAY);
    dyeChatMap.put("BLUE", C.DARK_BLUE);
    dyeChatMap.put("BROWN", C.GOLD);
    dyeChatMap.put("CYAN", C.AQUA);
    dyeChatMap.put("GRAY", C.GRAY);
    dyeChatMap.put("GREEN", C.DARK_GREEN);
    dyeChatMap.put("LIGHT_BLUE", C.BLUE);
    dyeChatMap.put("LIME", C.GREEN);
    dyeChatMap.put("MAGENTA", C.LIGHT_PURPLE);
    dyeChatMap.put("ORANGE", C.GOLD);
    dyeChatMap.put("PINK", C.LIGHT_PURPLE);
    dyeChatMap.put("PURPLE", C.DARK_PURPLE);
    dyeChatMap.put("RED", C.RED);
    dyeChatMap.put("LIGHT_GRAY", C.GRAY);
    dyeChatMap.put("WHITE", C.WHITE);
    dyeChatMap.put("YELLOW", C.YELLOW);
    dyeHexMap.put("BLACK", "#181414");
    dyeHexMap.put("BLUE", "#253193");
    dyeHexMap.put("BROWN", "#56331c");
    dyeHexMap.put("CYAN", "#267191");
    dyeHexMap.put("GRAY", "#414141");
    dyeHexMap.put("GREEN", "#364b18");
    dyeHexMap.put("LIGHT_BLUE", "#6387d2");
    dyeHexMap.put("LIME", "#39ba2e");
    dyeHexMap.put("MAGENTA", "#be49c9");
    dyeHexMap.put("ORANGE", "#ea7e35");
    dyeHexMap.put("PINK", "#d98199");
    dyeHexMap.put("PURPLE", "#7e34bf");
    dyeHexMap.put("RED", "#9e2b27");
    dyeHexMap.put("LIGHT_GRAY", "#a0a7a7");
    dyeHexMap.put("WHITE", "#a4a4a4");
    dyeHexMap.put("YELLOW", "#c2b51c");
  }

  static {
    for (C color : values()) {
      BY_ID.put(color.intCode, color);
      BY_CHAR.put(color.code, color);
    }
  }

  private final int intCode;
  private final char code;
  private final String token;
  private final boolean isFormat;
  private final String toString;

  C(char code, int intCode) {
    this("^", code, intCode, false);
  }

  C(String token, char code, int intCode) {
    this(token, code, intCode, false);
  }

  C(char code, int intCode, boolean isFormat) {
    this("^", code, intCode, isFormat);
  }

  C(String token, char code, int intCode, boolean isFormat) {
    this.code = code;
    this.token = token.equalsIgnoreCase("^") ? "<" + name().toLowerCase(Locale.ROOT) + ">" : token;
    this.intCode = intCode;
    this.isFormat = isFormat;
    this.toString = new String(new char[]{COLOR_CHAR, code});
  }

  public static float[] spin(float[] c, int shift) {
    return new float[]{spin(c[0], shift), spinc(c[1], shift), spinc(c[2], shift)};
  }

  public static float[] spin(float[] c, int a, int b, int d) {
    return new float[]{spin(c[0], a), spinc(c[1], b), spinc(c[2], d)};
  }

  public static float spin(float c, int shift) {
    float g = ((((int) Math.floor(c * 360)) + shift) % 360) / 360F;
    return g < 0 ? 1f - g : g;
  }

  public static float spinc(float c, int shift) {
    float g = ((((int) Math.floor(c * 255)) + shift)) / 255F;
    return Math.max(0f, Math.min(g, 1f));
  }

  public static java.awt.Color spin(java.awt.Color c, int h, int s, int b) {
    float[] hsb = java.awt.Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
    hsb = spin(hsb, h, s, b);
    return java.awt.Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
  }

  public static String spinToHex(C color, int h, int s, int b) {
    return "#" + Integer.toHexString(spin(color.awtColor(), h, s, b).getRGB()).substring(2);
  }

  public static String aura(String s, int hrad, int srad, int vrad) {
    return aura(s, hrad, srad, vrad, 0.3D);
  }

  public static String aura(String s, int hrad, int srad, int vrad, double pulse) {
    StringBuilder b = new StringBuilder();
    boolean c = false;

    for (char i : s.toCharArray()) {
      if (c) {
        c = false;

        C o = C.getByChar(i);

        if (o.isColor() && (hrad != 0 || srad != 0 || vrad != 0)) {
          if (pulse > 0) {
            b.append(pulse(spinToHex(o, hrad, srad, vrad), spinToHex(o, -hrad, -srad, -vrad), pulse));
          } else {
            b.append("<gradient:")
                .append(spinToHex(o, hrad, srad, vrad))
                .append(":")
                .append(spinToHex(o, -hrad, -srad, -vrad))
                .append(">");
          }
        } else {
          b.append(C.getByChar(i).token);
        }

        continue;
      }

      if (i == C.COLOR_CHAR) {
        c = true;
        continue;
      }

      b.append(i);
    }

    return b.toString();
  }

  /** MiniMessage gradient tag whose phase follows the wall clock, as {@code VolmitSender.pulse} renders it. */
  public static String pulse(String colorA, String colorB, double speed) {
    return "<gradient:" + colorA + ":" + colorB + ":" + pulse(speed) + ">";
  }

  private static String pulse(double speed) {
    double phase = (((M.ms() / 16) * 15D * speed) % 1000D) / 1000D;
    return Form.f(((1D - phase) * 2D) - 1D, 3).replaceAll("\\Q,\\E", ".").replaceAll("\\Q?\\E", "-");
  }

  public static C getByChar(char code) {
    try {
      C c = BY_CHAR.get(code);
      return c == null ? C.WHITE : c;
    } catch (Exception e) {
      return C.WHITE;
    }
  }

  public static C getByChar(String code) {
    if (code == null || code.isEmpty()) {
      return C.WHITE;
    }
    return BY_CHAR.get(code.charAt(0));
  }

  public static String stripColor(final String input) {
    return ColorFormatter.stripColor(input);
  }

  /** Chat colour closest to a dye colour, by upper-case dye name; {@link #MAGIC} when unknown. */
  public static C dyeToChat(String dyeName) {
    C color = dyeName == null ? null : dyeChatMap.get(dyeName.toUpperCase(Locale.ROOT));
    return color == null ? C.MAGIC : color;
  }

  public static String chatToHex(C clr) {
    if (chatHexMap.containsKey(clr)) {
      return chatHexMap.get(clr);
    }

    return "#000000";
  }

  /** Hex colour of a dye, by upper-case dye name. */
  public static String dyeToHex(String dyeName) {
    String hex = dyeName == null ? null : dyeHexMap.get(dyeName.toUpperCase(Locale.ROOT));
    return hex == null ? "#000000" : hex;
  }

  public static Rgb hexToColor(String hex) {
    if (hex.startsWith("#")) {
      hex = hex.substring(1);
    }

    if (hex.contains("x")) {
      hex = hex.substring(hex.indexOf("x"));
    }

    if (hex.length() != 6 && hex.length() != 3) {
      return null;
    }
    int sz = hex.length() / 3, mult = 1 << ((2 - sz) * 4), x = 0;

    for (int i = 0, z = 0; z < hex.length(); ++i, z += sz) {
      x |= (mult * Integer.parseInt(hex.substring(z, z + sz), 16)) << (i * 8);
    }

    return fromBgr(x & 0xffffff);
  }

  public static Rgb rgbToColor(String rgb) {
    String[] parts = rgb.split("[^0-9]+");
    if (parts.length < 3) {
      return null;
    }

    int x = 0, i;

    for (i = 0; i < 3; ++i) {
      x |= Integer.parseInt(parts[i]) << (i * 8);
    }

    return fromBgr(x & 0xffffff);
  }

  private static Rgb fromBgr(int bgr) {
    return new Rgb(bgr & 0xFF, (bgr >> 8) & 0xFF, (bgr >> 16) & 0xFF);
  }

  public static String generateColorTable() {
    StringBuilder str = new StringBuilder();

    str.append("<table><tr><td>Chat Color</td><td>Color</td></tr>");

    for (Map.Entry<C, String> e : chatHexMap.entrySet()) {
      str.append(String.format("<tr><td style='color: %2$s;'>%1$s</td>" + "<td style='color: %2$s;'>Test String</td></tr>", e.getKey().name(), e.getValue()));
    }

    str.append("</table>");
    str.append("<table><tr><td>Dye Color</td><td>Color</td></tr>");
    for (Map.Entry<String, String> e : dyeHexMap.entrySet()) {
      str.append(String.format("<tr><td style='color: %2$s;'>%1$s</td>" + "<td style='color: %2$s;'>Test String</td></tr>", e.getKey(), e.getValue()));
    }

    str.append("</table>");

    return str.toString();
  }

  public static String translateAlternateColorCodes(char altColorChar, String textToTranslate) {
    return ColorFormatter.translateAlternateColorCodes(altColorChar, textToTranslate);
  }

  public static C fromItemMeta(byte c) {
    for (C i : C.values()) {
      if (i.getItemMeta() == c) {
        return i;
      }
    }

    return null;
  }

  public static C randomColor() {
    return COLORS[(int) (Math.random() * (COLORS.length - 1))];
  }

  public static String getLastColors(String input) {
    return ColorFormatter.getLastColors(input);
  }

  public char getChar() {
    return code;
  }

  @Override
  public String toString() {
    return intCode == -1 ? token : toString;
  }

  public String hex() {
    return chatToHex(this);
  }

  public java.awt.Color awtColor() {
    return java.awt.Color.decode(hex());
  }

  public boolean isFormat() {
    return isFormat;
  }

  public boolean isColor() {
    return !isFormat && this != RESET;
  }

  public byte getMeta() {
    return switch (this) {
      case AQUA -> (byte) 11;
      case BLACK -> (byte) 0;
      case BLUE, DARK_AQUA -> (byte) 9;
      case BOLD, UNDERLINE, STRIKETHROUGH, RESET, MAGIC, ITALIC -> (byte) -1;
      case DARK_BLUE -> (byte) 1;
      case DARK_GRAY -> (byte) 8;
      case DARK_GREEN -> (byte) 2;
      case DARK_PURPLE -> (byte) 5;
      case DARK_RED -> (byte) 4;
      case GOLD -> (byte) 6;
      case GRAY -> (byte) 7;
      case GREEN -> (byte) 10;
      case LIGHT_PURPLE -> (byte) 13;
      case RED -> (byte) 12;
      case YELLOW -> (byte) 14;
      default -> (byte) 15;
    };
  }

  public byte getItemMeta() {
    return switch (this) {
      case AQUA, DARK_AQUA -> (byte) 9;
      case BLUE -> (byte) 3;
      case BOLD, UNDERLINE, RESET, STRIKETHROUGH, MAGIC, ITALIC -> (byte) -1;
      case DARK_BLUE -> (byte) 11;
      case DARK_GRAY -> (byte) 7;
      case DARK_GREEN -> (byte) 13;
      case DARK_PURPLE -> (byte) 10;
      case DARK_RED, RED -> (byte) 14;
      case GOLD, YELLOW -> (byte) 4;
      case GRAY -> (byte) 8;
      case GREEN -> (byte) 5;
      case LIGHT_PURPLE -> (byte) 2;
      case WHITE -> (byte) 0;
      default -> (byte) 15;
    };
  }
}
