package art.arcane.adapt.api.golem.plinth;

public interface PlinthCharge {
  PlinthCharge FREE = new PlinthCharge() {
    @Override
    public void settle() {
    }

    @Override
    public void refund() {
    }
  };

  void settle();

  void refund();
}
