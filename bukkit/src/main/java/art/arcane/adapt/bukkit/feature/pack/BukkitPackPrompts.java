package art.arcane.adapt.bukkit.feature.pack;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.pack.surface.PackPrompts;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.common.compat.PaperApi;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/**
 * Typed input for pack viewers: a Dialog over the open window on Paper, where the client returns to the same window
 * afterwards; elsewhere the window is put away and the answer is typed in chat.
 */
public final class BukkitPackPrompts implements PackPrompts {
  private final PaperDialogPrompts dialogs;
  private final ChatPrompts chat;

  private BukkitPackPrompts(PaperDialogPrompts dialogs, ChatPrompts chat) {
    this.dialogs = dialogs;
    this.chat = chat;
  }

  /** Creates the prompts; on servers without Dialogs registers the chat listener. */
  public static BukkitPackPrompts install(JavaPlugin plugin, BukkitPackWindows windows) {
    if (PaperApi.isPaper()) {
      return new BukkitPackPrompts(new PaperDialogPrompts(), null);
    }
    ChatPrompts chat = new ChatPrompts(windows);
    plugin.getServer().getPluginManager().registerEvents(chat, plugin);
    return new BukkitPackPrompts(null, chat);
  }

  @Override
  public boolean available(PlayerRef viewer) {
    Player player = BukkitRefs.player(viewer);
    return player != null && player.isOnline();
  }

  @Override
  public void text(PlayerRef viewer, String title, String current, int maxLength, Consumer<String> answer, Runnable cancelled) {
    Player player = BukkitRefs.player(viewer);
    if (dialogs != null) {
      dialogs.text(player, title, current, maxLength, answer, cancelled);
    } else {
      chat.text(player, title, maxLength, answer, cancelled);
    }
  }

  @Override
  public void number(PlayerRef viewer, String title, double current, Double min, Double max, Double step, DoubleConsumer answer,
                     Runnable cancelled) {
    Player player = BukkitRefs.player(viewer);
    if (dialogs != null) {
      dialogs.number(player, title, current, min, max, step, answer, cancelled);
    } else {
      chat.number(player, title, min, max, answer, cancelled);
    }
  }

  @Override
  public void choice(PlayerRef viewer, String title, List<String> options, int current, IntConsumer answer, Runnable cancelled) {
    Player player = BukkitRefs.player(viewer);
    if (dialogs != null) {
      dialogs.choice(player, title, options, current, answer, cancelled);
    } else {
      chat.choice(player, title, options, answer, cancelled);
    }
  }
}
