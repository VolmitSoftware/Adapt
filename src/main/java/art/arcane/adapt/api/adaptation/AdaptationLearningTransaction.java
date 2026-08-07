package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.volmlib.integration.VaultEconomy;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicBoolean;

public final class AdaptationLearningTransaction {
  private static final String REFUND_RECEIPT_PREFIX = "vault-learning-refund-";
  private static final String PENDING_REFUND_KEY = "vault-learning-pending-refund";
  private static final AtomicBoolean MISSING_PROVIDER_WARNED = new AtomicBoolean(false);

  private AdaptationLearningTransaction() {
  }

  public static Result learn(Adaptation<?> adaptation, Player player, int targetLevel, boolean bypassCosts) {
    AdaptPlayer adaptPlayer = adaptation.getPlayer(player);
    PlayerSkillLine skillLine = adaptPlayer.getSkillLine(adaptation.getSkill().getName());
    if (skillLine == null) {
      return Result.SKILL_LINE_UNAVAILABLE;
    }
    settlePendingRefund(player, skillLine);
    int currentLevel = skillLine.getAdaptationLevel(adaptation.getName());
    boolean regionGranted = isRegionGranted(skillLine, adaptation.getName());
    int paidLevel = regionGranted ? 0 : currentLevel;
    int normalizedTarget = Math.max(0, Math.min(targetLevel, adaptation.getMaxLevel()));
    if (normalizedTarget <= paidLevel) {
      return Result.NO_CHANGE;
    }
    int knowledgeCost = adaptation.getCostFor(normalizedTarget, paidLevel);
    int powerCost = adaptation.getPowerCostFor(normalizedTarget, paidLevel);
    if (!bypassCosts && !adaptPlayer.getData().hasPowerAvailable(powerCost)) {
      return Result.INSUFFICIENT_POWER;
    }
    if (!bypassCosts && skillLine.getKnowledge() < knowledgeCost) {
      return Result.INSUFFICIENT_KNOWLEDGE;
    }

    VaultEconomy.Charge charge = null;
    double moneyPerKnowledge = activeMoneyPerKnowledge();
    double refundPercent = activeRefundPercent();
    if (!bypassCosts && knowledgeCost > 0 && moneyPerKnowledge > 0D) {
      VaultEconomy economy = availableEconomy();
      if (economy != null) {
        double amount = knowledgeCost * moneyPerKnowledge;
        VaultEconomy.ChargeResult result = economy.withdraw(
            player,
            amount,
            "Adapt learning " + adaptation.getName() + " for " + player.getUniqueId()
        );
        if (!result.successful()) {
          return switch (result.status()) {
            case INSUFFICIENT_FUNDS -> Result.INSUFFICIENT_FUNDS;
            case VAULT_UNAVAILABLE, PROVIDER_UNAVAILABLE -> Result.ECONOMY_UNAVAILABLE;
            case SUCCESS, INVALID_AMOUNT, TRANSACTION_FAILED -> Result.ECONOMY_FAILED;
          };
        }
        charge = result.charge();
      }
    }

    if (!bypassCosts && !skillLine.spendKnowledge(knowledgeCost)) {
      refundCharge(charge, player, skillLine, adaptation.getName());
      return Result.INSUFFICIENT_KNOWLEDGE;
    }

    try {
      skillLine.setAdaptation(adaptation, normalizedTarget);
      if (regionGranted) {
        markRegionGranted(skillLine, adaptation.getName(), false);
      }
      if (charge != null && refundPercent > 0D) {
        storeRefundReceipts(
            skillLine,
            adaptation.getName(),
            adaptation,
            paidLevel,
            normalizedTarget,
            moneyPerKnowledge,
            refundPercent
        );
      }
      if (charge != null) {
        charge.commit();
      }
      return Result.LEARNED;
    } catch (RuntimeException exception) {
      clearRefundReceipts(skillLine, adaptation.getName(), paidLevel, normalizedTarget);
      skillLine.setAdaptation(adaptation, currentLevel);
      if (regionGranted) {
        markRegionGranted(skillLine, adaptation.getName(), true);
      }
      if (!bypassCosts) {
        skillLine.giveKnowledge(knowledgeCost);
      }
      refundCharge(charge, player, skillLine, adaptation.getName());
      throw exception;
    }
  }

  public static Result unlearn(Adaptation<?> adaptation, Player player, int targetLevel, boolean bypassCosts) {
    AdaptPlayer adaptPlayer = adaptation.getPlayer(player);
    PlayerSkillLine skillLine = adaptPlayer.getSkillLine(adaptation.getSkill().getName());
    if (skillLine == null) {
      return Result.SKILL_LINE_UNAVAILABLE;
    }
    settlePendingRefund(player, skillLine);
    int currentLevel = skillLine.getAdaptationLevel(adaptation.getName());
    int normalizedTarget = Math.max(0, targetLevel);
    if (normalizedTarget >= currentLevel) {
      return Result.NO_CHANGE;
    }
    if (adaptation.isPermanent() && !bypassCosts) {
      return Result.PERMANENT;
    }

    int paidLevel = isRegionGranted(skillLine, adaptation.getName()) ? 0 : currentLevel;
    int refundFloor = Math.min(normalizedTarget, paidLevel);
    int knowledgeRefund = adaptation.getRefundCostFor(refundFloor, paidLevel);
    double vaultRefund = refundReceiptAmount(skillLine, adaptation.getName(), refundFloor, paidLevel);
    skillLine.setAdaptation(adaptation, normalizedTarget);
    if (!bypassCosts && !AdaptConfig.get().isHardcoreNoRefunds()) {
      skillLine.giveKnowledge(knowledgeRefund);
      refundOrQueue(player, skillLine, vaultRefund, adaptation.getName());
    }
    clearRefundReceipts(skillLine, adaptation.getName(), normalizedTarget, currentLevel);
    return Result.UNLEARNED;
  }

  static String formattedLearnCost(int knowledgeCost) {
    double rate = activeMoneyPerKnowledge();
    VaultEconomy economy = currentEconomy();
    if (rate <= 0D || economy == null || !economy.isAvailable()) {
      return "";
    }
    return economy.format(knowledgeCost * rate);
  }

  static String formattedRefund(Adaptation<?> adaptation, Player player, int targetLevel, int currentLevel) {
    PlayerSkillLine skillLine = adaptation.getPlayer(player).getSkillLine(adaptation.getSkill().getName());
    if (skillLine == null) {
      return "";
    }
    double refund = refundReceiptAmount(skillLine, adaptation.getName(), targetLevel, currentLevel);
    VaultEconomy economy = currentEconomy();
    if (refund <= 0D || economy == null) {
      return "";
    }
    return economy.format(refund);
  }

  static boolean isConfigured() {
    AdaptConfig.LearningEconomy config = AdaptConfig.get().getLearningEconomy();
    return config != null && config.isEnabled() && positiveFinite(config.getMoneyPerKnowledge()) > 0D;
  }

  private static boolean isRegionGranted(PlayerSkillLine skillLine, String adaptationName) {
    PlayerAdaptation stored = skillLine.getAdaptation(adaptationName);
    return stored != null && stored.isRegionGranted();
  }

  private static void markRegionGranted(PlayerSkillLine skillLine, String adaptationName, boolean regionGranted) {
    PlayerAdaptation stored = skillLine.getAdaptation(adaptationName);
    if (stored == null) {
      return;
    }
    stored.setRegionGranted(regionGranted);
  }

  private static void storeRefundReceipts(
      PlayerSkillLine skillLine,
      String adaptationName,
      Adaptation<?> adaptation,
      int currentLevel,
      int targetLevel,
      double moneyPerKnowledge,
      double refundPercent
  ) {
    for (int level = currentLevel + 1; level <= targetLevel; level++) {
      double paidForLevel = adaptation.getCostFor(level) * moneyPerKnowledge;
      double refundable = paidForLevel * refundPercent;
      if (refundable <= 0D) {
        continue;
      }
      skillLine.getStorage().put(receiptKey(adaptationName, level), refundable);
    }
  }

  private static double refundReceiptAmount(
      PlayerSkillLine skillLine,
      String adaptationName,
      int targetLevel,
      int currentLevel
  ) {
    double refund = 0D;
    for (int level = Math.max(0, targetLevel) + 1; level <= currentLevel; level++) {
      refund += positiveFinite(storedDouble(skillLine, receiptKey(adaptationName, level)));
    }
    return refund;
  }

  private static void clearRefundReceipts(
      PlayerSkillLine skillLine,
      String adaptationName,
      int targetLevel,
      int currentLevel
  ) {
    for (int level = targetLevel + 1; level <= currentLevel; level++) {
      skillLine.getStorage().remove(receiptKey(adaptationName, level));
    }
  }

  private static void refundOrQueue(Player player, PlayerSkillLine skillLine, double amount, String adaptationName) {
    if (amount <= 0D) {
      return;
    }
    VaultEconomy economy = currentEconomy();
    if (economy != null && economy.deposit(
        player,
        amount,
        "Adapt unlearning refund for " + adaptationName + " and " + player.getUniqueId()
    )) {
      return;
    }
    double pending = pendingRefund(skillLine);
    skillLine.getStorage().put(PENDING_REFUND_KEY, pending + amount);
  }

  private static void settlePendingRefund(Player player, PlayerSkillLine skillLine) {
    double pending = pendingRefund(skillLine);
    if (pending <= 0D) {
      return;
    }
    VaultEconomy economy = currentEconomy();
    if (economy != null && economy.isAvailable() && economy.deposit(
        player,
        pending,
        "Adapt pending learning refund for " + player.getUniqueId()
    )) {
      skillLine.getStorage().remove(PENDING_REFUND_KEY);
    }
  }

  private static double pendingRefund(PlayerSkillLine skillLine) {
    return positiveFinite(storedDouble(skillLine, PENDING_REFUND_KEY));
  }

  private static double storedDouble(PlayerSkillLine skillLine, String key) {
    Object stored = skillLine.getStorage().get(key);
    if (stored instanceof Number number) {
      return number.doubleValue();
    }
    if (stored instanceof String string) {
      try {
        return Double.parseDouble(string);
      } catch (NumberFormatException ignored) {
        skillLine.getStorage().remove(key);
        Adapt.warn("Removed invalid Vault learning transaction value for " + key + ": " + string);
        return 0D;
      }
    }
    return 0D;
  }

  private static VaultEconomy availableEconomy() {
    VaultEconomy economy = currentEconomy();
    if (economy != null && economy.isAvailable()) {
      MISSING_PROVIDER_WARNED.set(false);
      return economy;
    }
    if (MISSING_PROVIDER_WARNED.compareAndSet(false, true)) {
      Adapt.warn("Vault learning prices are enabled, but Vault has no active economy provider; Knowledge-only learning remains available.");
    }
    return null;
  }

  private static VaultEconomy currentEconomy() {
    Adapt active = Adapt.instance;
    return active == null ? null : active.getVaultEconomy();
  }

  private static double activeMoneyPerKnowledge() {
    AdaptConfig.LearningEconomy config = AdaptConfig.get().getLearningEconomy();
    if (config == null || !config.isEnabled()) {
      return 0D;
    }
    return positiveFinite(config.getMoneyPerKnowledge());
  }

  private static double activeRefundPercent() {
    AdaptConfig.LearningEconomy config = AdaptConfig.get().getLearningEconomy();
    if (config == null) {
      return 0D;
    }
    return Math.min(100D, positiveFinite(config.getRefundPercent())) / 100D;
  }

  private static double positiveFinite(double value) {
    return Double.isFinite(value) && value > 0D ? value : 0D;
  }

  private static String receiptKey(String adaptationName, int level) {
    return REFUND_RECEIPT_PREFIX + adaptationName + "-level-" + level;
  }

  private static void refundCharge(
      VaultEconomy.Charge charge,
      Player player,
      PlayerSkillLine skillLine,
      String adaptationName
  ) {
    if (charge != null && !charge.refund()) {
      refundOrQueue(player, skillLine, charge.amount(), adaptationName + " learning rollback");
    }
  }

  public enum Result {
    LEARNED,
    UNLEARNED,
    NO_CHANGE,
    SKILL_LINE_UNAVAILABLE,
    INSUFFICIENT_POWER,
    INSUFFICIENT_KNOWLEDGE,
    INSUFFICIENT_FUNDS,
    ECONOMY_UNAVAILABLE,
    ECONOMY_FAILED,
    PERMANENT
  }
}
