import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Assignment algorithms; every request owns its working state. */
public final class Algorithms {
    private Algorithms() { }

    /** Prefix DP, followed by a walk backwards through optimal transitions. */
    static Map<String, Object> lcs(Map<String, Object> input) {
        String a = (String) input.get("a");
        String b = (String) input.get("b");
        int n = a.length();
        int m = b.length();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                dp[i][j] = a.charAt(i - 1) == b.charAt(j - 1)
                        ? dp[i - 1][j - 1] + 1
                        : Math.max(dp[i - 1][j], dp[i][j - 1]);
            }
        }
        StringBuilder sequence = new StringBuilder(dp[n][m]);
        int i = n;
        int j = m;
        while (i > 0 && j > 0) {
            if (a.charAt(i - 1) == b.charAt(j - 1)) {
                sequence.append(a.charAt(i - 1));
                i--;
                j--;
            } else if (dp[i - 1][j] >= dp[i][j - 1]) {
                i--;
            } else {
                j--;
            }
        }
        return Map.of("length", dp[n][m], "sequence", sequence.reverse().toString());
    }

    /** Interval DP: choose the final adjacent split after solving both children. */
    static Map<String, Object> merge(Map<String, Object> input) {
        List<?> sizes = (List<?>) input.get("sizes");
        int n = sizes.size();
        List<int[]> plan = new ArrayList<>();
        if (n <= 1) {
            return Map.of("cost", 0L, "plan", plan);
        }
        long[] prefix = new long[n + 1];
        for (int i = 0; i < n; i++) {
            prefix[i + 1] = prefix[i] + ((Number) sizes.get(i)).longValue();
        }
        long[][] dp = new long[n][n];
        int[][] split = new int[n][n];
        for (int length = 2; length <= n; length++) {
            for (int left = 0; left + length <= n; left++) {
                int right = left + length - 1;
                long combinedSize = prefix[right + 1] - prefix[left];
                long best = Long.MAX_VALUE;
                for (int middle = left; middle < right; middle++) {
                    long candidate = dp[left][middle] + dp[middle + 1][right] + combinedSize;
                    if (candidate < best) {
                        best = candidate;
                        split[left][right] = middle;
                    }
                }
                dp[left][right] = best;
            }
        }
        appendMergePlan(0, n - 1, split, plan);
        return Map.of("cost", dp[0][n - 1], "plan", plan);
    }

    // Depth is at most 200 under the merge contract. Children must precede parent.
    private static void appendMergePlan(int left, int right, int[][] split, List<int[]> plan) {
        if (left == right) {
            return;
        }
        int middle = split[left][right];
        appendMergePlan(left, middle, split, plan);
        appendMergePlan(middle + 1, right, split, plan);
        plan.add(new int[] {left, middle, right});
    }

    /** Take/skip DP on an iterative parent-before-child traversal of the tree. */
    static Map<String, Object> tree(Map<String, Object> input) {
        int n = ((Number) input.get("n")).intValue();
        List<?> weights = (List<?>) input.get("weights");
        List<?> edges = (List<?>) input.get("edges");
        List<Integer> selected = new ArrayList<>();
        if (n == 0) {
            return Map.of("weight", 0L, "selected", selected);
        }

        // Compact adjacency uses two entries per undirected edge.
        int[] offsets = new int[n + 1];
        for (Object edgeObject : edges) {
            List<?> edge = (List<?>) edgeObject;
            offsets[((Number) edge.get(0)).intValue() + 1]++;
            offsets[((Number) edge.get(1)).intValue() + 1]++;
        }
        for (int i = 1; i <= n; i++) {
            offsets[i] += offsets[i - 1];
        }
        int[] next = offsets.clone();
        int[] neighbours = new int[2 * (n - 1)];
        for (Object edgeObject : edges) {
            List<?> edge = (List<?>) edgeObject;
            int u = ((Number) edge.get(0)).intValue();
            int v = ((Number) edge.get(1)).intValue();
            neighbours[next[u]++] = v;
            neighbours[next[v]++] = u;
        }

        int[] parent = new int[n];
        int[] order = new int[n];
        parent[0] = -1;
        order[0] = 0;
        int visited = 1;
        for (int index = 0; index < n; index++) {
            int u = order[index];
            for (int edge = offsets[u]; edge < offsets[u + 1]; edge++) {
                int v = neighbours[edge];
                if (v != parent[u]) {
                    parent[v] = u;
                    order[visited++] = v;
                }
            }
        }
        long[] take = new long[n];
        long[] skip = new long[n];
        for (int index = n - 1; index >= 0; index--) {
            int u = order[index];
            take[u] = ((Number) weights.get(u)).longValue();
            for (int edge = offsets[u]; edge < offsets[u + 1]; edge++) {
                int v = neighbours[edge];
                if (v != parent[u]) {
                    take[u] += skip[v];
                    skip[u] += Math.max(take[v], skip[v]);
                }
            }
        }
        boolean[] chosen = new boolean[n];
        for (int u : order) {
            chosen[u] = (parent[u] == -1 || !chosen[parent[u]]) && take[u] > skip[u];
            if (chosen[u]) {
                selected.add(u);
            }
        }
        return Map.of("weight", Math.max(take[0], skip[0]), "selected", selected);
    }

    /** Check every possible start, including overlapping occurrences. */
    static Map<String, Object> naive(Map<String, Object> input) {
        String text = (String) input.get("text");
        String pattern = (String) input.get("pattern");
        List<Integer> positions = new ArrayList<>();
        for (int start = 0; start <= text.length() - pattern.length(); start++) {
            if (matchesAt(text, pattern, start)) {
                positions.add(start);
            }
        }
        return Map.of("positions", positions);
    }

    private static boolean matchesAt(String text, String pattern, int start) {
        for (int j = 0; j < pattern.length(); j++) {
            if (text.charAt(start + j) != pattern.charAt(j)) {
                return false;
            }
        }
        return true;
    }

    /** Prefix-function fallback preserves the longest still-usable border. */
    static Map<String, Object> kmp(Map<String, Object> input) {
        String text = (String) input.get("text");
        String pattern = (String) input.get("pattern");
        int n = text.length();
        int m = pattern.length();
        if (m == 0) {
            return Map.of("positions", boundaries(n));
        }
        List<Integer> positions = new ArrayList<>();
        if (m > n) {
            return Map.of("positions", positions);
        }
        int[] prefix = new int[m];
        for (int i = 1; i < m; i++) {
            int matched = prefix[i - 1];
            while (matched > 0 && pattern.charAt(i) != pattern.charAt(matched)) {
                matched = prefix[matched - 1];
            }
            if (pattern.charAt(i) == pattern.charAt(matched)) {
                matched++;
            }
            prefix[i] = matched;
        }
        int matched = 0;
        for (int i = 0; i < n; i++) {
            while (matched > 0 && text.charAt(i) != pattern.charAt(matched)) {
                matched = prefix[matched - 1];
            }
            if (text.charAt(i) == pattern.charAt(matched)) {
                matched++;
            }
            if (matched == m) {
                positions.add(i - m + 1);
                matched = prefix[m - 1];
            }
        }
        return Map.of("positions", positions);
    }

    private static List<Integer> boundaries(int length) {
        List<Integer> positions = new ArrayList<>(length + 1);
        for (int i = 0; i <= length; i++) {
            positions.add(i);
        }
        return positions;
    }

    /** Polynomial rolling hash filters candidates; characters decide exactness. */
    static Map<String, Object> rk(Map<String, Object> input) {
        String text = (String) input.get("text");
        String pattern = (String) input.get("pattern");
        int n = text.length();
        int m = pattern.length();
        if (m == 0) {
            return Map.of("positions", boundaries(n));
        }
        List<Integer> positions = new ArrayList<>();
        if (m > n) {
            return Map.of("positions", positions);
        }
        final long base = 31;
        final long modulus = 101;
        long highestPower = 1;
        long patternHash = 0;
        long windowHash = 0;
        for (int j = 0; j < m; j++) {
            patternHash = (patternHash * base + pattern.charAt(j)) % modulus;
            windowHash = (windowHash * base + text.charAt(j)) % modulus;
            if (j + 1 < m) {
                highestPower = highestPower * base % modulus;
            }
        }
        for (int start = 0; start <= n - m; start++) {
            if (windowHash == patternHash && matchesAt(text, pattern, start)) {
                positions.add(start);
            }
            if (start < n - m) {
                long withoutFirst = (windowHash - text.charAt(start) * highestPower) % modulus;
                if (withoutFirst < 0) {
                    withoutFirst += modulus;
                }
                windowHash = (withoutFirst * base + text.charAt(start + m)) % modulus;
            }
        }
        return Map.of("positions", positions);
    }

    /** Prefix doubling with stable rank-pair radix sorts, then Kasai LCP. */
    static Map<String, Object> suffix(Map<String, Object> input) {
        String text = (String) input.get("text");
        int n = text.length();
        int[] sa = new int[n];
        int[] lcp = new int[n];
        if (n == 0) {
            return Map.of("sa", sa, "lcp", lcp, "repeated", "");
        }
        int[] rank = new int[n];
        for (int i = 0; i < n; i++) {
            sa[i] = i;
            rank[i] = text.charAt(i) - 32;
        }
        int[] temporary = new int[n];
        int[] newRank = new int[n];
        int[] counts = new int[Math.max(n + 1, 96)];
        int classes = 95;
        for (int width = 1; width < n; width *= 2) {
            // Missing second halves have key 0; every real rank has key rank+1.
            countingSort(sa, temporary, rank, width, classes, counts);
            countingSort(temporary, sa, rank, 0, classes, counts);
            int lastRank = 0;
            newRank[sa[0]] = 0;
            for (int i = 1; i < n; i++) {
                int previous = sa[i - 1];
                int current = sa[i];
                int previousSecond = previous + width < n ? rank[previous + width] : -1;
                int currentSecond = current + width < n ? rank[current + width] : -1;
                if (rank[previous] != rank[current] || previousSecond != currentSecond) {
                    lastRank++;
                }
                newRank[current] = lastRank;
            }
            int[] oldRank = rank;
            rank = newRank;
            newRank = oldRank;
            classes = lastRank + 1;
            if (classes == n) {
                break;
            }
        }

        int[] inverse = new int[n];
        for (int i = 0; i < n; i++) {
            inverse[sa[i]] = i;
        }
        int common = 0;
        int longest = 0;
        int repeatedStart = 0;
        for (int start = 0; start < n; start++) {
            int position = inverse[start];
            if (position == 0) {
                common = 0;
                continue;
            }
            int previousStart = sa[position - 1];
            while (start + common < n && previousStart + common < n
                    && text.charAt(start + common) == text.charAt(previousStart + common)) {
                common++;
            }
            lcp[position] = common;
            if (common > longest) {
                longest = common;
                repeatedStart = start;
            }
            if (common > 0) {
                common--;
            }
        }
        return Map.of("sa", sa, "lcp", lcp,
                "repeated", text.substring(repeatedStart, repeatedStart + longest));
    }

    /** Stable counting sort of suffix indices by one component of a rank pair. */
    private static void countingSort(int[] source, int[] destination, int[] rank,
                                     int offset, int classes, int[] counts) {
        Arrays.fill(counts, 0, classes + 1, 0);
        int n = rank.length;
        for (int suffix : source) {
            int key = suffix + offset < n ? rank[suffix + offset] + 1 : 0;
            counts[key]++;
        }
        int total = 0;
        for (int key = 0; key <= classes; key++) {
            int frequency = counts[key];
            counts[key] = total;
            total += frequency;
        }
        for (int suffix : source) {
            int key = suffix + offset < n ? rank[suffix + offset] + 1 : 0;
            destination[counts[key]++] = suffix;
        }
    }

    /** One trie per dictionary, complete failure transitions, aggregated visits. */
    static Map<String, Object> aho(Map<String, Object> input) {
        List<?> patterns = (List<?>) input.get("patterns");
        List<?> texts = (List<?>) input.get("texts");
        int p = patterns.size();
        List<long[]> rows = new ArrayList<>(texts.size());
        if (p == 0) {
            for (int i = 0; i < texts.size(); i++) {
                rows.add(new long[0]);
            }
            return Map.of("counts", rows);
        }

        final int alphabet = 95;
        List<int[]> transitions = new ArrayList<>();
        transitions.add(emptyTransitions(alphabet));
        int[] terminal = new int[p];
        for (int i = 0; i < p; i++) {
            String pattern = (String) patterns.get(i);
            int state = 0;
            for (int j = 0; j < pattern.length(); j++) {
                int symbol = pattern.charAt(j) - 32;
                int[] row = transitions.get(state);
                if (row[symbol] == -1) {
                    row[symbol] = transitions.size();
                    transitions.add(emptyTransitions(alphabet));
                }
                state = row[symbol];
            }
            // Duplicate dictionary positions share their trie terminal.
            terminal[i] = state;
        }

        int states = transitions.size();
        int[] failure = new int[states];
        int[] breadthFirst = new int[states];
        int head = 0;
        int tail = 0;
        int[] root = transitions.get(0);
        for (int symbol = 0; symbol < alphabet; symbol++) {
            if (root[symbol] == -1) {
                root[symbol] = 0;
            } else {
                breadthFirst[tail++] = root[symbol];
            }
        }
        while (head < tail) {
            int state = breadthFirst[head++];
            int[] row = transitions.get(state);
            int[] fallbackRow = transitions.get(failure[state]);
            for (int symbol = 0; symbol < alphabet; symbol++) {
                if (row[symbol] == -1) {
                    row[symbol] = fallbackRow[symbol];
                } else {
                    int child = row[symbol];
                    failure[child] = fallbackRow[symbol];
                    breadthFirst[tail++] = child;
                }
            }
        }

        for (Object textObject : texts) {
            String text = (String) textObject;
            long[] visits = new long[states];
            int state = 0;
            for (int i = 0; i < text.length(); i++) {
                state = transitions.get(state)[text.charAt(i) - 32];
                visits[state]++;
            }
            // Failure parents are shallower, so reverse BFS accumulates all suffix matches.
            for (int i = tail - 1; i >= 0; i--) {
                int child = breadthFirst[i];
                visits[failure[child]] += visits[child];
            }
            long[] counts = new long[p];
            for (int i = 0; i < p; i++) {
                counts[i] = visits[terminal[i]];
            }
            rows.add(counts);
        }
        return Map.of("counts", rows);
    }

    private static int[] emptyTransitions(int alphabet) {
        int[] row = new int[alphabet];
        Arrays.fill(row, -1);
        return row;
    }
}
