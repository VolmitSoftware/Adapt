package art.arcane.adapt.command;

import java.util.List;

/**
 * One chat menu page drawn by {@link CommandHelpPort#menu}: a title banner, a back link to {@code parentCommand} (none
 * when blank), the {@code entries} of {@code requestedPage} ({@code pageSize} per page; MiniMessage lines for players,
 * plain lines for the console) or {@code emptyLine} when there are none, and page links that run
 * {@code command page=N}.
 */
public record CommandMenu(String title, String command, String parentCommand, List<String> entries, String emptyLine,
                          int requestedPage, int pageSize) {
  public CommandMenu {
    entries = List.copyOf(entries);
  }
}
