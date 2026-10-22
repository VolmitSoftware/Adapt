package art.arcane.adapt.api.xp;


import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ItemTransferXpLedger {
  private static final int MAX_MATERIALS = 2048;
  private final Map<String, Allowance> materials = new LinkedHashMap<>();

  public synchronized double claim(String material, int amount, int stackSize, long windowMillis, long now) {
    if (material == null || amount <= 0 || stackSize <= 0 || windowMillis <= 0L) {
      return 0D;
    }
    Allowance allowance = materials.get(material);
    if (allowance == null) {
      if (materials.size() >= MAX_MATERIALS) {
        Iterator<Map.Entry<String, Allowance>> entries = materials.entrySet().iterator();
        while (entries.hasNext()) {
          Allowance candidate = entries.next().getValue();
          if (now - candidate.updatedAt >= windowMillis) {
            entries.remove();
          }
        }
        if (materials.size() >= MAX_MATERIALS) {
          return 0D;
        }
      }
      allowance = new Allowance(stackSize, now);
      materials.put(material, allowance);
    }
    long elapsed = Math.max(0L, now - allowance.updatedAt);
    allowance.remaining = Math.min(stackSize, allowance.remaining + (double) elapsed / windowMillis * stackSize);
    allowance.updatedAt = Math.max(now, allowance.updatedAt);
    double granted = Math.min(amount, allowance.remaining);
    allowance.remaining -= granted;
    return granted;
  }

  private static final class Allowance {
    private double remaining;
    private long updatedAt;

    private Allowance(double remaining, long updatedAt) {
      this.remaining = remaining;
      this.updatedAt = updatedAt;
    }
  }
}
