package art.arcane.adapt.api.mutation;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Data
@NoArgsConstructor
public class PlayerMutationData {
  private static final int MAX_DISCOVERED = 256;
  private static final int MAX_FORMULA_SIGILS = MutationLimits.FORMULA_SIGILS;
  private static final double MAX_DEEPBLOOD_ICHOR = MutationLimits.DEEPBLOOD_ICHOR;
  private static final double MAX_LIVING_LATTICE_ROOT_CHARGE = MutationLimits.LATTICE_ROOT_CHARGE;

  private List<String> discovered = new ArrayList<>();
  private String slotOneId = "";
  private String slotTwoId = "";
  private long slotOneReadyAt;
  private long slotTwoReadyAt;
  private Boolean slotOneUnlockedOverride;
  private Boolean slotTwoUnlockedOverride;
  private boolean cooperativeOptIn;
  private String temperboundBondId = "";
  private List<String> temperboundPieceIds = new ArrayList<>();
  private String masterworkItemId = "";
  private long masterworkAbandonReadyAt;
  private String deepbloodToolId = "";
  private double deepbloodIchor;
  private long deepbloodUpdatedAt;
  private double livingLatticeRootCharge;
  private String trophyImprint = "";
  private long trophyImprintExpiresAt;
  private Map<String, Long> formulaSigils = new LinkedHashMap<>();

  public void normalize() {
    slotOneId = normalizeId(slotOneId);
    slotTwoId = normalizeId(slotTwoId);
    temperboundBondId = normalizeId(temperboundBondId);
    masterworkItemId = normalizeId(masterworkItemId);
    deepbloodToolId = normalizeId(deepbloodToolId);
    trophyImprint = normalizeId(trophyImprint);
    slotOneReadyAt = Math.max(0L, slotOneReadyAt);
    slotTwoReadyAt = Math.max(0L, slotTwoReadyAt);
    masterworkAbandonReadyAt = Math.max(0L, masterworkAbandonReadyAt);
    trophyImprintExpiresAt = Math.max(0L, trophyImprintExpiresAt);
    deepbloodIchor = clampFinite(deepbloodIchor, 0D, MAX_DEEPBLOOD_ICHOR);
    livingLatticeRootCharge = clampFinite(livingLatticeRootCharge, 0D, MAX_LIVING_LATTICE_ROOT_CHARGE);
    deepbloodUpdatedAt = Math.max(0L, deepbloodUpdatedAt);
    discovered = normalizeIds(discovered, MAX_DISCOVERED);
    temperboundPieceIds = normalizeIds(temperboundPieceIds, 4);
    formulaSigils = normalizeSigils(formulaSigils);
  }

  public boolean discover(String mutationId) {
    String normalized = normalizeId(mutationId);
    if (normalized.isEmpty() || discovered.contains(normalized) || discovered.size() >= MAX_DISCOVERED) {
      return false;
    }
    return discovered.add(normalized);
  }

  public boolean undiscover(String mutationId) {
    String normalized = normalizeId(mutationId);
    if (normalized.isEmpty()) {
      return false;
    }
    return discovered.remove(normalized);
  }

  public String slotId(int slot) {
    return switch (slot) {
      case 1 -> slotOneId;
      case 2 -> slotTwoId;
      default -> "";
    };
  }

  public void setSlotId(int slot, String mutationId) {
    String normalized = normalizeId(mutationId);
    if (slot == 1) {
      slotOneId = normalized;
    } else if (slot == 2) {
      slotTwoId = normalized;
    }
  }

  public long slotReadyAt(int slot) {
    return switch (slot) {
      case 1 -> slotOneReadyAt;
      case 2 -> slotTwoReadyAt;
      default -> Long.MAX_VALUE;
    };
  }

  public void setSlotReadyAt(int slot, long readyAt) {
    long normalized = Math.max(0L, readyAt);
    if (slot == 1) {
      slotOneReadyAt = normalized;
    } else if (slot == 2) {
      slotTwoReadyAt = normalized;
    }
  }

  public Boolean slotUnlockedOverride(int slot) {
    return switch (slot) {
      case 1 -> slotOneUnlockedOverride;
      case 2 -> slotTwoUnlockedOverride;
      default -> null;
    };
  }

  public void setSlotUnlockedOverride(int slot, Boolean unlocked) {
    if (slot == 1) {
      slotOneUnlockedOverride = unlocked;
    } else if (slot == 2) {
      slotTwoUnlockedOverride = unlocked;
    }
  }

  public void clearCooldowns() {
    slotOneReadyAt = 0L;
    slotTwoReadyAt = 0L;
  }

  public void clearAll() {
    discovered = new ArrayList<>();
    slotOneId = "";
    slotTwoId = "";
    slotOneReadyAt = 0L;
    slotTwoReadyAt = 0L;
    slotOneUnlockedOverride = null;
    slotTwoUnlockedOverride = null;
    cooperativeOptIn = false;
    temperboundBondId = "";
    temperboundPieceIds = new ArrayList<>();
    masterworkItemId = "";
    masterworkAbandonReadyAt = 0L;
    deepbloodToolId = "";
    deepbloodIchor = 0D;
    deepbloodUpdatedAt = 0L;
    livingLatticeRootCharge = 0D;
    trophyImprint = "";
    trophyImprintExpiresAt = 0L;
    formulaSigils = new LinkedHashMap<>();
  }

  private static List<String> normalizeIds(List<String> values, int cap) {
    ArrayList<String> normalized = new ArrayList<>(Math.min(cap, values == null ? 0 : values.size()));
    if (values == null) {
      return normalized;
    }
    for (String value : values) {
      String id = normalizeId(value);
      if (!id.isEmpty() && !normalized.contains(id)) {
        normalized.add(id);
        if (normalized.size() >= cap) {
          break;
        }
      }
    }
    return normalized;
  }

  private static Map<String, Long> normalizeSigils(Map<String, Long> values) {
    LinkedHashMap<String, Long> normalized = new LinkedHashMap<>();
    if (values == null) {
      return normalized;
    }
    for (Map.Entry<String, Long> entry : values.entrySet()) {
      String id = normalizeId(entry.getKey());
      Long timestamp = entry.getValue();
      if (!id.isEmpty() && timestamp != null && timestamp > 0L) {
        normalized.put(id, timestamp);
        if (normalized.size() >= MAX_FORMULA_SIGILS) {
          break;
        }
      }
    }
    return normalized;
  }

  private static String normalizeId(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
  }

  private static double clampFinite(double value, double minimum, double maximum) {
    if (!Double.isFinite(value)) {
      return minimum;
    }
    return Math.max(minimum, Math.min(maximum, value));
  }
}
