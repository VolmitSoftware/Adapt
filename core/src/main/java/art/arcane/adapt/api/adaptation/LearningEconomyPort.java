package art.arcane.adapt.api.adaptation;

import java.util.UUID;

/**
 * Money economy for learning prices and unlearning refunds (Vault on Bukkit). Without a port no economy exists and
 * learning costs only Knowledge.
 */
public interface LearningEconomyPort {
  LearningEconomyPort NONE = new LearningEconomyPort() {
    @Override
    public boolean isAvailable() {
      return false;
    }

    @Override
    public String format(double amount) {
      return "";
    }

    @Override
    public Withdrawal withdraw(UUID playerId, double amount, String context) {
      return new Withdrawal(Status.VAULT_UNAVAILABLE, null);
    }

    @Override
    public boolean deposit(UUID playerId, double amount, String context) {
      return false;
    }
  };

  /** True when the platform has an economy integration at all, even if no provider is active right now. */
  default boolean isPresent() {
    return this != NONE;
  }

  boolean isAvailable();

  String format(double amount);

  Withdrawal withdraw(UUID playerId, double amount, String context);

  boolean deposit(UUID playerId, double amount, String context);

  enum Status {
    SUCCESS,
    INVALID_AMOUNT,
    VAULT_UNAVAILABLE,
    PROVIDER_UNAVAILABLE,
    INSUFFICIENT_FUNDS,
    TRANSACTION_FAILED
  }

  /** A withdrawn amount held until the learning commits or rolls back. */
  interface Charge {
    double amount();

    boolean commit();

    boolean refund();
  }

  record Withdrawal(Status status, Charge charge) {
    public boolean successful() {
      return status == Status.SUCCESS && charge != null;
    }
  }
}
