package art.arcane.adapt.util.config;

/** Platform-only follow-up of an adapt.toml hotload: protection-plugin defaults. {@link #NONE} does nothing. */
public interface ConfigHotloadPort {
  ConfigHotloadPort NONE = new ConfigHotloadPort() {
    @Override
    public void coreConfigReloaded() {
    }
  };

  void coreConfigReloaded();
}
