package art.arcane.adapt.gameplay.demo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.World;

final class PlateSearch {
    static final String PENDING = "ADAPT_QA DEMO LOCATE PENDING";
    static final int SAMPLE_STEP = 8;
    static final List<Integer> X_OFFSETS = offsets(-DemoStudio.PLATE_HALF_WIDTH, DemoStudio.PLATE_HALF_WIDTH);
    static final List<Integer> Z_OFFSETS = offsets(-DemoStudio.PLATE_NORTH, DemoStudio.PLATE_SOUTH);

    private static final int REPORTED_BIOMES = 6;

    private final World world;
    private final PlateSurvey survey;
    private final int radius;
    private final int step;
    private final List<Candidate> candidates;
    private final AtomicReferenceArray<Verdict> verdicts;
    private final Map<Rejection, Integer> rejections = new EnumMap<>(Rejection.class);
    private final Map<String, Integer> originBiomes = new HashMap<>();
    private final Thread worker;
    private volatile RuntimeException failure;
    private volatile boolean cancelled;
    private int cursor;

    private PlateSearch(World world, PlateSurvey survey, int radius, int step) {
        this.world = world;
        this.survey = survey;
        this.radius = radius;
        this.step = step;
        this.candidates = candidates(radius, step);
        this.verdicts = new AtomicReferenceArray<>(candidates.size());
        this.worker = Thread.ofPlatform().daemon().name("Adapt demo plate search").unstarted(this::scan);
    }

    static PlateSearch start(World world, int radius, int step, PlateSurvey survey) {
        if (radius < 0 || step < 1) {
            throw new IllegalArgumentException("Locate needs a radius of at least 0 and a step of at least 1");
        }
        PlateSearch search = new PlateSearch(world, survey, radius, step);
        search.worker.start();
        return search;
    }

    boolean matches(World target, int targetRadius, int targetStep) {
        return world.equals(target) && radius == targetRadius && step == targetStep;
    }

    void cancel() {
        cancelled = true;
    }

    String poll() {
        while (cursor < candidates.size()) {
            Verdict verdict = verdicts.get(cursor);
            if (verdict == null) {
                RuntimeException failed = failure;
                if (failed != null) {
                    return "ADAPT_QA ERROR plate search failed at candidate " + cursor + ": " + failed;
                }
                return PENDING + " " + cursor + "/" + candidates.size();
            }
            Candidate candidate = candidates.get(cursor);
            cursor++;
            originBiomes.merge(verdict.originBiome(), 1, Integer::sum);
            Rejection rejection = verdict.rejection() == null && !survey.confirm(candidate) ? Rejection.SURFACE : verdict.rejection();
            if (rejection == null) {
                cancel();
                return "ADAPT_QA DEMO LOCATE " + candidate.x() + " " + candidate.z() + " " + verdict.originBiome();
            }
            rejections.merge(rejection, 1, Integer::sum);
        }
        return "ADAPT_QA ERROR no " + survey.goal() + " plate origin within " + radius + " blocks of 0 0; " + candidates.size()
                + " candidates, rejected " + rejections + ", origin biomes " + commonBiomes();
    }

    private static List<Candidate> candidates(int radius, int step) {
        int steps = radius / step;
        long limit = (long) radius * radius;
        List<Candidate> result = new ArrayList<>((2 * steps + 1) * (2 * steps + 1));
        for (int i = -steps; i <= steps; i++) {
            for (int j = -steps; j <= steps; j++) {
                Candidate candidate = new Candidate(i * step, j * step);
                if (candidate.distanceSquared() <= limit) {
                    result.add(candidate);
                }
            }
        }
        result.sort(Comparator.comparingLong(Candidate::distanceSquared).thenComparingInt(Candidate::x).thenComparingInt(Candidate::z));
        return result;
    }

    private static List<Integer> offsets(int from, int to) {
        List<Integer> result = new ArrayList<>();
        for (int offset = from; offset < to; offset += SAMPLE_STEP) {
            result.add(offset);
        }
        result.add(to);
        return List.copyOf(result);
    }

    private void scan() {
        try {
            for (int index = 0; index < candidates.size() && !cancelled; index++) {
                verdicts.set(index, survey.judge(candidates.get(index), () -> cancelled));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException failed) {
            Bukkit.getLogger().log(Level.SEVERE, "Adapt demo plate search failed", failed);
            failure = failed;
        }
    }

    private String commonBiomes() {
        return originBiomes.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(REPORTED_BIOMES)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(" "));
    }

    enum Rejection {
        UNAVAILABLE,
        BIOME,
        WATER,
        SLOPE,
        SURFACE,
        HEADROOM,
        LAVA
    }

    record Candidate(int x, int z) {
        long distanceSquared() {
            return (long) x * x + (long) z * z;
        }
    }

    record Verdict(String originBiome, Rejection rejection) {
    }
}
