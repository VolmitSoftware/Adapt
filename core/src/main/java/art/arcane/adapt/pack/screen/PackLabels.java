package art.arcane.adapt.pack.screen;

import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.LearnRules;
import art.arcane.adapt.client.view.RequirementView;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.pack.text.LegacyText;
import art.arcane.adapt.pack.text.PackFontMetrics;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/** The words pack screens put on learn and unlearn controls: costs, refunds and why a control is refused. */
final class PackLabels {
  static final int INK = 0x2E2216;
  static final int MUTED = 0x5A4430;
  static final int FAINT = 0x766450;
  static final int MOSS = 0x2E5619;
  static final int WINE = 0x8D261D;
  static final int BLUE = 0x23406E;
  static final int GOLD_INK = 0x6B440C;
  static final String GREY = coloured(MUTED, "");
  static final String GREEN = coloured(MOSS, "");
  static final String RED = coloured(WINE, "");
  static final String BLUE_TEXT = coloured(BLUE, "");

  private PackLabels() {
  }

  /**
   * The next step of an adaptation as one line: "Learn II for 6 knowledge" (or free) when allowed, with the power it
   * drains after it; else what stops it.
   */
  static List<String> next(CatalogIndex catalog, LearnRules.LearnCheck check) {
    List<String> lines = new ArrayList<>();
    if (!check.allowed()) {
      lines.add(RED + refusal(catalog, check));
      return lines;
    }
    String level = roman(check.to());
    lines.add(check.free() ? GREEN + AdaptLanguage.text(GuiMessages.PACK_LEARN, trusted("level", level)) + ", "
        + AdaptLanguage.text(GuiMessages.PACK_FREE)
        : BLUE_TEXT + AdaptLanguage.text(GuiMessages.PACK_LEARN_FOR, trusted("level", level), trusted("cost", check.knowledgeCost())));
    if (check.powerCost() > 0) {
      lines.add(BLUE_TEXT + AdaptLanguage.text(GuiMessages.POWER_DRAIN, trusted("power", check.powerCost())));
    }
    return lines;
  }

  /** The refund line of an allowed unlearn, or the refusal line of a refused one. */
  static List<String> unlearn(AdaptationView adaptation, LearnRules.UnlearnCheck check) {
    List<String> lines = new ArrayList<>();
    if (!check.allowed()) {
      lines.add(RED + refusal(adaptation, check));
    } else if (check.refunded()) {
      lines.add(BLUE_TEXT + AdaptLanguage.text(GuiMessages.PACK_REFUND, trusted("refund", check.refund())));
    }
    return lines;
  }

  static String refusal(CatalogIndex catalog, LearnRules.LearnCheck check) {
    return switch (check.block()) {
      case NONE -> "";
      case DISABLED -> AdaptLanguage.text(GuiMessages.PACK_DISABLED);
      case DENIED -> AdaptLanguage.text(GuiMessages.PACK_DENIED);
      case LEARNED -> AdaptLanguage.text(GuiMessages.PACK_LEARNED);
      case REQUIREMENTS -> AdaptLanguage.text(GuiMessages.REQUIRES, trusted("requirement", requirements(catalog, check.missing())));
      case POWER -> AdaptLanguage.text(GuiMessages.PACK_NEED_POWER, trusted("power", check.powerShort()));
      case KNOWLEDGE -> AdaptLanguage.text(GuiMessages.PACK_NEED_KNOWLEDGE, trusted("knowledge", check.knowledgeShort()));
    };
  }

  static String refusal(AdaptationView adaptation, LearnRules.UnlearnCheck check) {
    return switch (check.block()) {
      case NONE -> "";
      case DISABLED -> AdaptLanguage.text(GuiMessages.PACK_DISABLED);
      case DENIED -> AdaptLanguage.text(GuiMessages.PACK_DENIED);
      case NOT_LEARNED -> AdaptLanguage.text(GuiMessages.PACK_NOT_LEARNED);
      case PERMANENT -> AdaptLanguage.text(GuiMessages.PACK_PERMANENT);
      case DEPENDENTS -> AdaptLanguage.text(GuiMessages.REQUIRED_BY_DEPENDENTS,
          trusted("dependents", names(check.dependents())), trusted("adaptation", adaptation.name()));
    };
  }

  private static String requirements(CatalogIndex catalog, List<RequirementView> missing) {
    List<String> parts = new ArrayList<>();
    for (RequirementView requirement : missing) {
      parts.add(AdaptLanguage.text(GuiMessages.REQUIREMENT_LEVEL, trusted("adaptation",
          catalog.adaptationName(requirement.adaptationId())), trusted("level", requirement.level())));
    }
    return String.join(", ", parts);
  }

  private static String names(List<AdaptationView> adaptations) {
    List<String> parts = new ArrayList<>();
    for (AdaptationView adaptation : adaptations) {
      parts.add(adaptation.name());
    }
    return String.join(", ", parts);
  }

  /** {@code number} in Roman numerals as the level pips show it, for 1 to 3999; other numbers in digits. */
  static String roman(int number) {
    if (number < 1 || number > 3999) {
      return String.valueOf(number);
    }
    int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
    StringBuilder out = new StringBuilder();
    int rest = number;
    for (int index = 0; index < values.length; index++) {
      while (rest >= values[index]) {
        out.append(numerals[index]);
        rest -= values[index];
      }
    }
    return out.toString();
  }

  /**
   * An item or tag id as words, for lines that cannot carry the client's own item names: {@code minecraft:cobweb} is
   * "Cobweb", {@code #minecraft:logs} is "Logs"; null when it names nothing.
   */
  static String itemWords(String itemId) {
    Id type = itemId == null ? null : Id.tryParse(itemId.startsWith("#") ? itemId.substring(1) : itemId);
    if (type == null) {
      return null;
    }
    StringBuilder words = new StringBuilder();
    for (String word : type.path().substring(type.path().lastIndexOf('/') + 1).split("_")) {
      if (!word.isEmpty()) {
        words.append(words.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
      }
    }
    return words.toString();
  }

  /** A stat line led by "+ ", unless the line already leads with its own sign. */
  /** How wide a tooltip's description lines may run, in GUI pixels, before they wrap. */
  static final int TOOLTIP_WIDTH = 180;

  /** {@code text} wrapped to tooltip lines of at most {@link #TOOLTIP_WIDTH}, each in the muted ink. */
  static List<String> described(String text) {
    List<String> lines = new ArrayList<>();
    for (String line : PackFontMetrics.vanilla().wrapWords(text, TOOLTIP_WIDTH)) {
      lines.add(GREY + line);
    }
    return lines;
  }

  static String stat(String line) {
    String plain = LegacyText.plain(MarkupPort.current().legacy(line)).stripLeading();
    return plain.startsWith("+") || plain.startsWith("-") ? line : "+ " + line;
  }

  /** Lore and title text use the design palette as legacy hex codes. {@code text} in the 0xRRGGBB colour. */
  static String coloured(int rgb, String text) {
    StringBuilder out = new StringBuilder("§x");
    for (char digit : String.format("%06x", rgb & 0xFFFFFF).toCharArray()) {
      out.append('§').append(digit);
    }
    return out.append(text).toString();
  }
}
