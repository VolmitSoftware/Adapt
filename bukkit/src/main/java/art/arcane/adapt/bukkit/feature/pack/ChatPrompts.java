package art.arcane.adapt.bukkit.feature.pack;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ConfigMessages;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

/** Prompts answered in chat, for servers without Dialogs. The open pack window is put away while the viewer types. */
final class ChatPrompts implements Listener {
  private final BukkitPackWindows windows;
  private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

  ChatPrompts(BukkitPackWindows windows) {
    this.windows = windows;
  }

  void text(Player player, String title, int maxLength, Consumer<String> answer, Runnable cancelled) {
    begin(player, title, List.of(), (String typed) -> {
      answer.accept(PromptValues.clip(typed, maxLength));
      return true;
    }, cancelled);
  }

  void number(Player player, String title, Double min, Double max, DoubleConsumer answer, Runnable cancelled) {
    begin(player, title, List.of(), (String typed) -> {
      Double value = PromptValues.number(typed, min, max);
      if (value == null) {
        return false;
      }
      answer.accept(value);
      return true;
    }, cancelled);
  }

  void choice(Player player, String title, List<String> options, IntConsumer answer, Runnable cancelled) {
    begin(player, title, options, (String typed) -> {
      int index = PromptValues.choice(typed, options);
      if (index < 0) {
        return false;
      }
      answer.accept(index);
      return true;
    }, cancelled);
  }

  private void begin(Player player, String title, List<String> options, Predicate<String> accept, Runnable cancelled) {
    Pending replaced = pending.put(player.getUniqueId(), new Pending(accept, cancelled));
    if (replaced != null) {
      replaced.cancelled().run();
    }
    windows.putAway(player);
    send(player, AdaptLanguage.text(ConfigMessages.ENTER_VALUE, untrusted("label", title)));
    for (int index = 0; index < options.size(); index++) {
      send(player, (index + 1) + ". " + options.get(index));
    }
    send(player, AdaptLanguage.text(ConfigMessages.TYPE_CANCEL));
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onChat(AsyncPlayerChatEvent event) {
    Player player = event.getPlayer();
    Pending waiting = pending.get(player.getUniqueId());
    if (waiting == null) {
      return;
    }
    event.setCancelled(true);
    String typed = event.getMessage();
    J.runEntity(player, () -> answer(player, waiting, typed));
  }

  private void answer(Player player, Pending waiting, String typed) {
    if (pending.get(player.getUniqueId()) != waiting) {
      return;
    }
    if (PromptValues.cancels(typed)) {
      pending.remove(player.getUniqueId(), waiting);
      waiting.cancelled().run();
      return;
    }
    pending.remove(player.getUniqueId(), waiting);
    if (!waiting.accept().test(typed)) {
      pending.putIfAbsent(player.getUniqueId(), waiting);
      send(player, AdaptLanguage.text(ConfigMessages.TRY_AGAIN_OR_CANCEL));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    pending.remove(event.getPlayer().getUniqueId());
  }

  private static void send(Player player, String markup) {
    BukkitRefs.player(player).sendMessage(markup);
  }

  private record Pending(Predicate<String> accept, Runnable cancelled) {
  }
}
