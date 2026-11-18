package art.arcane.adapt.mod.platform.data;

/** Implemented on every mob by the platform mixins: whether an undead mob burns in daylight, saved with the mob. */
public interface AdaptSunBurn {
    boolean adapt$burnsInDaylight();

    void adapt$setBurnsInDaylight(boolean burns);
}
