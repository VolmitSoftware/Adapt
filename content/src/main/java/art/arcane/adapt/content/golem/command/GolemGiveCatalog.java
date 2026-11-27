package art.arcane.adapt.content.golem.command;

import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemRuneType;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.content.golem.item.GolemItemKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class GolemGiveCatalog {
  public static final String ALL = "all";

  private static final String MASTERWORK_HEART = GolemItemKind.MASTERWORK_HEART.id();
  private static final List<String> IDS = buildIds();
  private static final Set<String> ID_SET = Set.copyOf(IDS);
  private static final List<String> COMPLETIONS = buildCompletions();

  private GolemGiveCatalog() {
  }

  public static List<String> ids() {
    return IDS;
  }

  public static List<String> completions() {
    return COMPLETIONS;
  }

  public static String normalize(String input) {
    return input.trim().toLowerCase(Locale.ROOT);
  }

  public static boolean isItem(String id) {
    return ID_SET.contains(id);
  }

  private static List<String> buildIds() {
    List<String> ids = new ArrayList<>(4 + GolemCoreType.values().length + NodeType.values().length
        + GolemRuneType.values().length * GolemRuneType.MAX_TIER);
    ids.add(GolemItemKind.CROOK.id());
    ids.add(GolemItemKind.BLANK_CORE.id());
    for (GolemCoreType role : GolemCoreType.values()) {
      if (role != GolemCoreType.BLANK) {
        ids.add(GolemItemKind.CORE.id() + "/" + role.id());
      }
    }
    ids.add(GolemItemKind.BLANK_NODE.id());
    for (NodeType type : NodeType.values()) {
      ids.add(GolemItemKind.NODE.id() + "/" + type.id());
    }
    ids.add(GolemItemKind.BLANK_RUNE.id());
    for (GolemRuneType aspect : GolemRuneType.values()) {
      for (int tier = GolemRuneType.MIN_TIER; tier <= GolemRuneType.MAX_TIER; tier++) {
        ids.add(GolemItemKind.RUNE.id() + "/" + aspect.id() + "/" + tier);
      }
    }
    ids.add(MASTERWORK_HEART);
    return List.copyOf(ids);
  }

  private static List<String> buildCompletions() {
    List<String> completions = new ArrayList<>(IDS.size() + 1);
    completions.addAll(IDS);
    completions.add(ALL);
    return List.copyOf(completions);
  }
}
