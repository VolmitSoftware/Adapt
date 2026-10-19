package art.arcane.adapt.api.golem;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

final class RecordingMender implements GolemMend.Mender {
  final List<GolemMend.RegenSenses> senses = new ArrayList<>();
  final List<GolemHandle> asked = new ArrayList<>();
  final List<GolemHandle> shattered = new ArrayList<>();
  final List<Location> shatteredAt = new ArrayList<>();
  long intervalMillis = 5_000L;
  double regenAmount;
  boolean shatters;

  @Override
  public long regenIntervalMillis() {
    return intervalMillis;
  }

  @Override
  public double regen(GolemHandle handle, GolemMend.RegenSenses next) {
    senses.add(next);
    return regenAmount;
  }

  @Override
  public boolean shatters(GolemHandle handle) {
    asked.add(handle);
    return shatters;
  }

  @Override
  public void shattered(GolemHandle handle, Location at) {
    shattered.add(handle);
    shatteredAt.add(at);
  }
}
