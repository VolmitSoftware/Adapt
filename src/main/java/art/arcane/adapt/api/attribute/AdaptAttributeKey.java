package art.arcane.adapt.api.attribute;

import org.bukkit.NamespacedKey;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public record AdaptAttributeKey(String adaptation, String slot, NamespacedKey key, UUID uuid) {
  public static final String NAMESPACE = "adaptbuff";
  private static final Map<String, Map<String, AdaptAttributeKey>> CACHE = new ConcurrentHashMap<>();

  public static AdaptAttributeKey of(String adaptationName, String slot) {
    String adaptationId = adaptationName == null ? "" : adaptationName;
    String slotId = slot == null ? "" : slot;
    Map<String, AdaptAttributeKey> bySlot = CACHE.get(adaptationId);
    if (bySlot == null) {
      bySlot = CACHE.computeIfAbsent(adaptationId, ignored -> new ConcurrentHashMap<>());
    } else {
      AdaptAttributeKey cached = bySlot.get(slotId);
      if (cached != null) {
        return cached;
      }
    }

    AdaptAttributeKey created = build(adaptationName, slot);
    AdaptAttributeKey existing = bySlot.putIfAbsent(slotId, created);
    return existing == null ? created : existing;
  }

  public static String sanitize(String raw) {
    if (raw == null || raw.isEmpty()) {
      return "";
    }

    String lower = raw.toLowerCase(Locale.ROOT);
    StringBuilder out = new StringBuilder(lower.length());
    for (int i = 0; i < lower.length(); i++) {
      char c = lower.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-') {
        out.append(c);
      } else {
        out.append('_');
      }
    }
    return out.toString();
  }

  private static AdaptAttributeKey build(String adaptationName, String slot) {
    String sanitizedAdaptation = sanitize(adaptationName);
    String sanitizedSlot = sanitize(slot);
    String base = sanitizedAdaptation.isEmpty() ? "_" : sanitizedAdaptation;
    String value = sanitizedSlot.isEmpty() ? base : base + "_" + sanitizedSlot;
    NamespacedKey key = new NamespacedKey(NAMESPACE, value);
    UUID uuid = UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8));
    return new AdaptAttributeKey(base, sanitizedSlot, key, uuid);
  }
}
