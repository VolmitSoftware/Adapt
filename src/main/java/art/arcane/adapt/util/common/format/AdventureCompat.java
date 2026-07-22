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

import art.arcane.adapt.Adapt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AdventureCompat {
  private static final AtomicBoolean FALLBACK_NOTIFIED = new AtomicBoolean(false);
  private static final AtomicBoolean PARSE_FALLBACK_NOTIFIED = new AtomicBoolean(false);
  private static volatile boolean miniMessageCompatible = true;

  private AdventureCompat() {
  }

  public static Component deserialize(String message) {
    String source = normalize(message);
    if (miniMessageCompatible) {
      try {
        return MiniMessage.miniMessage().deserialize(source);
      } catch (Throwable e) {
        handleFailure(e);
      }
    }

    return LegacyComponentSerializer.legacySection().deserialize(C.translateAlternateColorCodes('&', stripTagsFallback(source)));
  }

  public static Component deserializeNoProcessing(String message) {
    String source = normalize(message);
    if (miniMessageCompatible) {
      try {
        return MiniMessage.builder().postProcessor(c -> c).build().deserialize(source);
      } catch (Throwable e) {
        handleFailure(e);
      }
    }

    return LegacyComponentSerializer.legacySection().deserialize(C.translateAlternateColorCodes('&', stripTagsFallback(source)));
  }

  public static String stripTags(String message) {
    String source = normalize(message);
    if (miniMessageCompatible) {
      try {
        return MiniMessage.miniMessage().stripTags(source);
      } catch (Throwable e) {
        handleFailure(e);
      }
    }

    return stripTagsFallback(source);
  }

  public static String toLegacySection(String message) {
    String source = normalize(message);
    if (miniMessageCompatible) {
      try {
        return LegacyComponentSerializer.legacySection().serialize(MiniMessage.miniMessage().deserialize(source));
      } catch (Throwable e) {
        handleFailure(e);
      }
    }

    return C.translateAlternateColorCodes('&', stripTagsFallback(source));
  }

  private static String normalize(String message) {
    return message == null ? "" : message;
  }

  private static String stripTagsFallback(String message) {
    return message.replaceAll("<[^>]+>", "");
  }

  private static void handleFailure(Throwable failure) {
    if (shouldDisableMiniMessage(failure)) {
      markIncompatible(failure);
      return;
    }

    if (PARSE_FALLBACK_NOTIFIED.compareAndSet(false, true)) {
      String reason = failure == null ? "unknown" : failure.getClass().getSimpleName();
      Adapt.verbose("MiniMessage parse fallback for a malformed message (" + reason + "); rendering continues normally.");
    }
  }

  static boolean shouldDisableMiniMessage(Throwable failure) {
    return failure instanceof LinkageError;
  }

  private static void markIncompatible(Throwable e) {
    miniMessageCompatible = false;
    if (FALLBACK_NOTIFIED.compareAndSet(false, true)) {
      String reason = e == null ? "unknown" : e.getClass().getSimpleName();
      Adapt.warn("MiniMessage compatibility fallback enabled (" + reason + ").");
      if (e != null) {
        Adapt.verbose("MiniMessage fallback reason: " + e.toString().toLowerCase(Locale.ROOT));
      }
    }
  }
}
