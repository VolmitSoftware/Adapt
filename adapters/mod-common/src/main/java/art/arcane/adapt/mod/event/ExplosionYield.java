package art.arcane.adapt.mod.event;

/** Implemented by the explosion mixin: the drop yield handlers chose, or NaN for vanilla's. */
public interface ExplosionYield {
    float adapt$yield();

    void adapt$yield(float yield);
}
