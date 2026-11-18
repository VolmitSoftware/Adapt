package art.arcane.adapt.mod.platform.data;

/** Implemented on every mob by the platform mixins. An unaware mob skips its AI step but keeps physics. */
public interface AdaptMobAwareness {
    boolean adapt$isAware();

    void adapt$setAware(boolean aware);
}
