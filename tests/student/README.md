# Student correctness tests

From the repository root, with Python 3.12+ and JDK 17+ on PATH:

```sh
python tests/student/run_tests.py
```

The runner compiles with the official launcher, then sends JSONL requests to the
real Java transport with a 384 MiB heap and a 20-second limit per batch. It also
enforces the published 8 MiB output bound. `--skip-build` reuses a current build;
`--small-only` omits maximum-domain tests.

`cases.json` contains named boundary/counterexample requests and the defect each
detects. The deterministic random seed is 20261108. Independent small oracles
enumerate LCS subsequences, enumerate next adjacent merges, and enumerate tree
vertex subsets. Searches use Python `startswith`; suffixes use direct character
comparisons and direct LCP comparison; dictionary counts use independent searches.
No submitted Java algorithm is imported into the oracle.

Witness validation accepts all valid optima, simulates chronological adjacent
merges, and checks tree IDs, uniqueness, adjacency and reconstructed weight.
Mixed requests are repeated in a single process to catch stale task state.

Scale tests cover 1000-by-1000 LCS, 200 blocks including 64-bit totals, 100000-node
chain and star, maximum naive/KMP/RK bounds, 50000-character suffix arrays, and
Aho inputs reaching 10000 dictionary positions, total pattern length 100000,
100 texts, total text length 200000, dense suffix matches and nearly 100000
trie states. Every scale result is checked, rather than merely timed.

The last actual run writes `test-results.json`, including exact small inputs,
expected results and actual witnesses. Large arrays have compact actual summaries;
the reproducible generator and exact expected formulas remain in the runner.

For the reproducible in-process search comparison, run:

```sh
python tests/student/benchmark.py
```

This compiles `SearchBenchmark.java` together with the selected Java source,
verifies outputs before timing, and writes `benchmark-results.json` plus its
Markdown table. Each of three measured runs averages ten calls after twenty
warm-up calls per method/input. See `ANALYSIS.md` for the timing boundary,
generator, environment and interpretation. Public execution output is saved in
`public-checks.txt`; the 660-request student report is `test-results.json`.
