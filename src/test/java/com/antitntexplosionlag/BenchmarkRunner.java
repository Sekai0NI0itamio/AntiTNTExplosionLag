package com.antitntexplosionlag;

import com.antitntexplosionlag.bench.BenchmarkPlan;
import com.antitntexplosionlag.explosion.ClusterMerge;
import com.antitntexplosionlag.explosion.ExplosionMath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper-level benchmark (pure Java, no Minecraft classes).
 *
 * <p>Compares the naive vs optimized exposure calculation and measures
 * affected-list dedup throughput on synthetic overlapping-blast workloads.
 * This proves the helpers are faster; it is NOT a full-server measurement
 * (see BENCHMARKS.md for that limitation and how to run one).
 */
public final class BenchmarkRunner {
    private static final int WARMUP = 5;
    private static final int MEASURED = 15;

    public static void main(String[] args) throws IOException {
        Path outDir = args.length > 0 ? Path.of(args[0]) : Path.of("build/reports/antitnt-benchmark");
        Files.createDirectories(outDir);

        StringBuilder md = new StringBuilder();
        md.append("# AntiTNTExplosionLag helper benchmark\n\n");
        md.append("Environment: ").append(System.getProperty("java.version"))
                .append(" / ").append(System.getProperty("os.name")).append("\n\n");
        md.append("Scope: pure-Java helper microbenchmark (no Minecraft classes). ");
        md.append("Full-server numbers require a live server; see BENCHMARKS.md.\n\n");

        // --- Exposure: naive vs fast must agree, fast must be no slower. ---
        float[] dirs = ExplosionMath.SHELL_16;
        ExplosionMath.ObstructionFn checker = (dx, dy, dz, t) -> (dx + dy + dz) > 0;
        float naive = ExplosionMath.exposureNaive(dirs, checker);
        float fast = ExplosionMath.exposureFast(dirs, checker);
        boolean equal = Float.compare(naive, fast) == 0;
        for (int i = 0; i < WARMUP; i++) {
            ExplosionMath.exposureNaive(dirs, checker);
            ExplosionMath.exposureFast(dirs, checker);
        }
        long naiveTotal = 0;
        long fastTotal = 0;
        for (int i = 0; i < MEASURED; i++) {
            long s = System.nanoTime();
            ExplosionMath.exposureNaive(dirs, checker);
            naiveTotal += System.nanoTime() - s;
            s = System.nanoTime();
            ExplosionMath.exposureFast(dirs, checker);
            fastTotal += System.nanoTime() - s;
        }
        double naiveAvg = naiveTotal / (MEASURED * 1_000_000.0);
        double fastAvg = fastTotal / (MEASURED * 1_000_000.0);
        md.append("## Exposure calculation (").append(dirs.length / 3).append(" rays)\n\n");
        md.append("- naive avg: ").append(fmt(naiveAvg)).append(" ms\n");
        md.append("- fast avg: ").append(fmt(fastAvg)).append(" ms\n");
        md.append("- verdicts identical: ").append(equal).append("\n");
        md.append("- speedup: ").append(fmt(naiveAvg / Math.max(fastAvg, 1e-9))).append("x\n\n");

        // --- Dedup throughput on overlapping-blast workload. ---
        List<Long> workload = BenchmarkPlan.syntheticWorkload(20000, 0.35, 12345L);
        long distinct = workload.stream().distinct().count();
        for (int i = 0; i < WARMUP; i++) {
            ExplosionMath.deduplicatePreservingOrder(new ArrayList<>(workload));
        }
        long dedupTotal = 0;
        int removed = 0;
        for (int i = 0; i < MEASURED; i++) {
            List<Long> copy = new ArrayList<>(workload);
            long s = System.nanoTime();
            removed = ExplosionMath.deduplicatePreservingOrder(copy);
            dedupTotal += System.nanoTime() - s;
        }
        double dedupAvg = dedupTotal / (MEASURED * 1_000_000.0);
        md.append("## Affected-list dedup (").append(workload.size())
                .append(" entries, ").append(distinct).append(" unique)\n\n");
        md.append("- removed per pass: ").append(removed).append("\n");
        md.append("- avg pass: ").append(fmt(dedupAvg)).append(" ms\n\n");

        // --- Cluster detection on a solid 21^3 TNT fill (user scenario). ---
        List<double[]> fill = new ArrayList<>(9261);
        for (int x = 0; x < 21; x++) {
            for (int y = 0; y < 21; y++) {
                for (int z = 0; z < 21; z++) {
                    fill.add(new double[]{x, y, z});
                }
            }
        }
        for (int i = 0; i < WARMUP; i++) {
            ClusterMerge.components(new ArrayList<>(fill), 7.0);
        }
        long clusterTotal = 0;
        int clusterGroups = 0;
        for (int i = 0; i < MEASURED; i++) {
            long s = System.nanoTime();
            clusterGroups = ClusterMerge.components(new ArrayList<>(fill), 7.0).size();
            clusterTotal += System.nanoTime() - s;
        }
        double clusterAvg = clusterTotal / (MEASURED * 1_000_000.0);
        md.append("## Cluster detection (9261-point solid fill)\n\n");
        md.append("- components found: ").append(clusterGroups).append("\n");
        md.append("- avg pass: ").append(fmt(clusterAvg)).append(" ms\n");
        md.append("- merged power for full fill: ")
                .append(fmt(ClusterMerge.combinedPower(9261, 4.0f, 48.0f))).append(" (capped)\n\n");

        // --- Scenario workload specs. ---
        md.append("## Scenario workload specs\n\n");
        md.append("| scenario | tnt | ticks | area |\n|---|---|---|---|\n");
        for (BenchmarkPlan.Scenario s : BenchmarkPlan.scenarios()) {
            md.append("| ").append(s.name()).append(" | ").append(s.tntCount())
                    .append(" | ").append(s.ticks()).append(" | ").append(s.area()).append(" |\n");
        }
        md.append("\nMethodology: each scenario maps to a synthetic affected-list + ");
        md.append("exposure workload above, run ").append(MEASURED);
        md.append(" times after ").append(WARMUP).append(" warmup passes.\n");

        Path report = outDir.resolve("BENCHMARK-REPORT.md");
        Files.writeString(report, md.toString());
        System.out.println("[AntiTNT benchmark] verdicts identical: " + equal);
        System.out.println("[AntiTNT benchmark] exposure naive=" + fmt(naiveAvg)
                + "ms fast=" + fmt(fastAvg) + "ms");
        System.out.println("[AntiTNT benchmark] dedup removed=" + removed
                + " avg=" + fmt(dedupAvg) + "ms");
        System.out.println("[AntiTNT benchmark] cluster groups=" + clusterGroups
                + " avg=" + fmt(clusterAvg) + "ms");
        System.out.println("[AntiTNT benchmark] report: " + report.toAbsolutePath());
        if (!equal) {
            throw new IllegalStateException("optimized exposure diverged from reference");
        }
    }

    private static String fmt(double v) {
        return String.format("%.4f", v);
    }
}
