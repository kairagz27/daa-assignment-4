import java.util.*;

/** Reproducible in-process benchmark; not part of the submitted JSONL executable. */
public final class SearchBenchmark {
    private static volatile int blackhole;
    private static final int WARMUP = 20;
    private static final int ITERATIONS = 10;
    private static final long SEED = 20261108L;

    private static Map<String, Object> search(String method, Map<String, Object> input) {
        switch (method) {
            case "naive": return Algorithms.naive(input);
            case "kmp": return Algorithms.kmp(input);
            case "rk": return Algorithms.rk(input);
            default: throw new IllegalArgumentException(method);
        }
    }

    private static List<Integer> oracle(String text, String pattern) {
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i + pattern.length() <= text.length(); i++) {
            // Built-in matching is used only in this independent test oracle.
            if (text.startsWith(pattern, i)) expected.add(i);
        }
        return expected;
    }

    public static void main(String[] args) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 !?";
        String[] methods = {"naive", "kmp", "rk"};
        for (String kind : List.of("random", "repetitive")) {
            for (int n : new int[]{500, 1500, 3000}) {
                int m = n / 20;
                Random generator = new Random(SEED + n);
                StringBuilder builder = new StringBuilder(n);
                for (int i = 0; i < n; i++) {
                    builder.append(kind.equals("random")
                            ? alphabet.charAt(generator.nextInt(alphabet.length())) : 'a');
                }
                String text = builder.toString();
                // Random case has a guaranteed planted match; repetitive case is all 'a'.
                String pattern = text.substring(n / 3, n / 3 + m);
                Map<String, Object> input = Map.of("text", text, "pattern", pattern);
                List<Integer> expected = oracle(text, pattern);
                for (String method : methods) {
                    Object actual = search(method, input).get("positions");
                    if (!expected.equals(actual)) throw new AssertionError("Wrong output: " + method);
                }
                for (String method : methods) {
                    for (int i = 0; i < WARMUP; i++) {
                        blackhole = ((List<?>) search(method, input).get("positions")).size();
                    }
                    List<Double> runs = new ArrayList<>();
                    for (int run = 0; run < 3; run++) {
                        long start = System.nanoTime();
                        for (int i = 0; i < ITERATIONS; i++) {
                            blackhole = ((List<?>) search(method, input).get("positions")).size();
                        }
                        runs.add((System.nanoTime() - start) / (1_000_000.0 * ITERATIONS));
                    }
                    List<Double> sorted = new ArrayList<>(runs);
                    Collections.sort(sorted);
                    rows.add(Map.of("case", kind, "n", n, "m", m, "algorithm", method,
                            "runs_ms", runs, "median_ms", sorted.get(1),
                            "correct", true, "occurrences", expected.size(), "seed", SEED + n));
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("java_version", System.getProperty("java.runtime.version"));
        result.put("vm", System.getProperty("java.vm.name"));
        result.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        result.put("architecture", System.getProperty("os.arch"));
        result.put("warmup_calls_per_method_case", WARMUP);
        result.put("calls_per_measured_run", ITERATIONS);
        result.put("measured_runs", 3);
        result.put("alphabet", alphabet);
        result.put("generator", "java.util.Random(seed=20261108+n); random uniform alphabet, pattern=text[n/3:n/3+m]; repetitive all a");
        result.put("timing_scope", "In-process Algorithms method including output construction, dispatch and volatile result-size consumption; excludes compilation, JVM startup, JSON transport, generator and correctness check. Each run is 10 calls, reported as ms/call.");
        result.put("rows", rows);
        System.out.println(Json.write(result));
    }
}
