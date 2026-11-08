# Assignment 4 — analysis

## Environment and actual commands

Language: **Java**, compiled with `javac --release 17 -encoding UTF-8`.
Local runtime/compiler: Oracle Java 25.0.1 LTS, HotSpot 64-Bit Server VM;
launcher: CPython 3.12.14. GitHub Public checks use Temurin Java 17/Python 3.12.
Hardware: AMD Ryzen 7 7735HS (8 cores/16 logical processors), physical RAM
16,369,225,728 bytes (about 15.25 GiB). OS: Windows 11 Pro 10.0.26200.
The launcher and student runner use a 384 MiB Java heap.

The actual local commands, from the repository root, are:

```powershell
$a4Python = 'C:\Users\MrForest\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
& $a4Python tools/run.py --setup
& $a4Python tools/run.py --check
& $a4Python tools/run.py --input examples/lcs.jsonl
& $a4Python tests/student/run_tests.py --skip-build
& $a4Python tests/student/benchmark.py
```

On other systems, replace `& $a4Python` with `python` (Python 3.12+ and a Java
17+ JDK on PATH). The launcher builds `Java/source/*.java` into `Java/build`
then executes `java -Xmx384m -cp Java/build Main`. That Java command also runs
an already-built persistent UTF-8 JSON Lines process: one result per request,
with only results on stdout. Every request creates fresh local algorithm state.

## Algorithms, correctness and complexity

### LCS

`dp[i][j]` is the optimal common subsequence length for the first `i` characters
of `a` and first `j` of `b`. Empty prefixes give the zero row/column. Equal last
characters use `dp[i-1][j-1]+1`; a mismatch uses
`max(dp[i-1][j],dp[i][j-1])`. Increasing row/column order prepares all dependencies.
An optimum can end with equal last characters; on a mismatch it must omit at
least one last character. These exhaustive alternatives prove the transition
by induction on prefix sizes.

Backtrack from the final cell, taking equal characters diagonally and otherwise
following a neighbour with the same optimum; mismatch ties move up. Reverse the
collected characters. Each diagonal preserves order in both strings and
accounts for one unit of the optimal value, so the reconstruction is valid and
optimal. An empty input gives length zero and the empty sequence.
Time and auxiliary memory: `O((|a|+1)(|b|+1))`; output `O(length+1)`;
input `O(|a|+|b|+1)`. `tea_time`/`team` distinguishes subsequences from substrings;
`AB`/`BA` checks acceptance of alternative optimal witnesses.

### Adjacent merging

`dp[l][r]` is the minimum cost to merge original closed interval `[l,r]` into
one block. A singleton costs zero. Prefix sums compute interval size in constant
time. Try every final split `l<=k<r` with
`dp[l][k]+dp[k+1][r]+sum(l..r)`, processing intervals by increasing length.
Every legal final merge has precisely these adjacent children; replacing either
child plan with its optimum cannot increase cost. Induction on interval length
therefore proves the optimum.

Remember the earliest minimizing split. Reconstruction emits child plans before
the parent `[l,k,r]`, ensuring the blocks already exist when merged; it emits
exactly `n-1` merges for `n>1`. Empty/singleton inputs have cost zero and empty
plans. `long` arithmetic handles totals beyond 32 bits: even the bound
`(n-1)*sum(sizes)` is below `4*10^13`. Time `O(n^3)`, auxiliary memory
`O(n^2)` (including split table and `O(n)` reconstruction stack), input/output
`O(n)`; empty input `O(1)`. `[2,3,4]` costs 14, while all-zero blocks test ties.
Choosing two arbitrary smallest blocks is invalid when they are nonadjacent.

### Tree independent set

An explicit traversal roots the tree at zero, records parents and visit order;
reverse order processes children first. Define
`take[u]=weight[u]+sum(skip[child])` and
`skip[u]=sum(max(take[child],skip[child]))`. A leaf's skip is zero. Selecting
`u` excludes its children; excluding it permits independent optima in the child
subtrees. Those exhaustive cases establish correctness by subtree induction.
The root is a computational choice, not a restriction on feasible sets.

Reconstruction is iterative too: a selected parent forces skip, otherwise choose
take only when strictly better than skip. Skipping ties is valid. Vertices are
visited once, no vertex is repeated and no edge has two selected endpoints.
The empty set handles negative weights; an empty tree returns zero/empty.
Totals use `long`, with a conservative sum bound `10^14`. Time and auxiliary
space `O(n+1)`, including reconstruction; input/output `O(n+1)`.
The 100,000-vertex chain tests stack safety, and stars test large totals.

### Naive search

For nonempty patterns examine starts `0..n-m` inclusive. At each start compare
characters `j=0..m-1`, stopping on mismatch; emit only complete matches. Every
possible start is checked exactly once, proving both completeness and soundness.
Advance starts by one to keep overlaps and sorted output. For the empty pattern,
the same loop visits boundaries `0..n` and zero comparisons succeed at each one;
`m>n` makes the loop empty and gives no starts.
Time `O(n*m+n+1)`, auxiliary memory `O(1)`, output `O(z+1)` and input
`O(n+m+1)`. Empty/empty gives `[0]`; `aaaaa`/`aaa` gives `[0,1,2]`.
Spaces, punctuation and case are significant ASCII characters.

### KMP

`pi[i]` is the longest proper prefix of the pattern also ending as a suffix at
`i`. Build left to right, falling back through `pi[j-1]` on mismatches then
extending on equality. During scanning, `j` is the pattern-prefix length matching
a suffix of processed text. Each fallback retains the next feasible border and
discards only impossible alignments, so no match is skipped. After a full match
emit the start and fall back to `pi[m-1]`, preserving overlaps. Handle empty
patterns before pattern indexing, and return empty for overlong patterns.

Each extension raises `j` once and fallback strictly lowers it, giving amortized
time `O(n+m+z+1)`. Auxiliary memory `O(m+1)`, output `O(z+1)`, input
`O(n+m+1)`. `abababa`/`aba` gives `[0,2,4]`; resetting `j` to zero after matches
would lose valid overlaps.

### Exact Rabin–Karp

Hash a length-`m` string as `sum(ASCII(s[j])*31^(m-1-j)) mod 101`. Compute the
pattern and first window left to right and `high=31^(m-1) mod 101`. Slide by
subtracting `outgoing*high`, normalizing the remainder, multiplying by 31 and
adding the incoming ASCII code. This preserves the polynomial hash invariant.
Unequal hashes imply unequal strings; equal hashes require explicit character
verification. Thus genuine matches pass and collisions cannot become false
matches. All starts, including overlaps, are examined in order. Empty/overlong
patterns are handled before constructing a window.

For `1<=m<=n`, time `O(n+m+c*m)`, with `c` equal-hash candidate windows; the
bound across all inputs is `O(n*m+n+m+1)`. Exact RK has no deterministic linear
worst-case guarantee. Rolling scalars require `O(1)` auxiliary storage, plus
`O(z+1)` output and `O(n+m+1)` input. `ab` and `dj` both hash to 75: searching
`abdj` for `ab` must give only `[0]`. Java `%` may be negative after subtraction,
so the implementation normalizes it before further use.

### Suffix array and Kasai

Prefix doubling ranks prefixes of length `width`, initially by ASCII value.
Sort positions by `(rank[i],rank[i+width])`, with the absent second half ordered
below real ranks. Two stable counting-sort passes, second key then first,
order these pairs. Assign new ranks to unequal pairs and double the width.
Inductively the pair order represents twice-longer prefixes; once ranks are
distinct or width covers the text, this is full suffix order. There are exactly
the nonempty original suffixes, no added sentinel and no materialized suffix
strings. Counting arrays and buffers are linear; `O(log(n+1))` rounds give
`O(n log(n+1))` time and `O(n+1)` auxiliary space.

Kasai inverts the suffix array. In text-start order compare a suffix to its
preceding suffix in SA order, extending reused common length `h`, storing it at
the current rank and then decreasing it by at most one. Rank zero has no
predecessor and resets `h`. Removing a common leading character leaves a valid
`h-1` lower bound for the next comparison. Total extensions are linear, so
Kasai is `O(n)` time and `O(n)` auxiliary space. `lcp[0]=0` by convention.

Suffixes sharing any prefix are contiguous in sorted order. Any repeated
substring therefore appears in an adjacent suffix pair's common prefix; the
maximum adjacent LCP is the longest repeat length. Return the substring at a
maximizing start; ties and overlaps are valid. Empty input returns empty arrays
and string, and one-character/all-distinct inputs have no repeat. Input and
combined output each occupy `O(n+1)` space. `banana` gives SA
`[5,3,1,0,4,2]`, LCP `[0,1,3,0,0,2]`, repeat `ana`.

### Aho–Corasick

Insert patterns in a trie with 95 transition columns (ASCII 32–126); retain
one terminal-state reference per original dictionary position. Duplicates share
states, while keeping separate result columns. BFS builds failure links to the
longest proper suffix represented in the trie and completes missing transitions
from the failure state. Root misses return to root. Failure depth is smaller,
so BFS prepares dependencies. The automaton state after each character is the
longest dictionary-prefix suffix of processed text.

For each text create fresh state zero and `long` visits, incrementing the reached
state for every character. Reverse BFS order adds each state's count to its
failure state. A pattern ending at that character is a terminal on this failure
chain, so propagation counts every suffix-pattern occurrence exactly once,
including overlaps. Extract terminal totals in original dictionary order.
This shares duplicate terminals and avoids inherited output lists or per-match
records. Independent texts restart both state and counts.

Let `L` include duplicate pattern lengths, `p` dictionary size, `S` actual trie
states and `q` text count. Construction is `O(L+95*S+p)` time and
`O(95*S+p)` auxiliary memory. Each text costs `O(|text|+S+p)` including
propagation/output extraction. Output memory `O(q*(p+1)+1)`, input memory
`O(L+totalTextLength+p+q+1)`. Empty dictionaries keep one empty row per text;
empty texts produce zero rows of dictionary width; no texts produce `[]`.
`['he','she','he']` against `['sheshe','sheshe','']` yields
`[[2,2,2],[2,2,2],[0,0,0]]`, checking suffixes, duplicates and reset.

## Own tests and actual results

The independent student runner includes exact fixtures, small brute-force/oracle
checks, witness validation, mixed persistent requests and full-domain scale
checks. Fixtures and compact input/expected/actual records are under
`tests/student`. The supplied checks do not establish an official mark.

Actual result: **660/660 student requests PASS**: 521 small cases (41 named
fixtures plus 60 deterministic randomized cases per command), 120 repeated mixed
requests, and 19 full-domain scale cases. Seed: 20261108. Full inputs, expected
values and actual small witnesses are in `tests/student/test-results.json`;
large fixtures have compact summaries and reproducible generators in the runner.
The public command also passed **44/44 cases** and **two complete batches in one
process**; its actual stdout/stderr is in `tests/student/public-checks.txt`.
Setup returned `PASS setup: ping`; the example run returned
`{"sequence":"team","length":4}`.

Across the student batches, the maximum measured process wall time was
0.238 seconds and the maximum output was 5,000,213 bytes (Aho 10,000 patterns,
100 texts). Each batch stayed below 20 seconds and 8 MiB. Timings are local
observations including JVM startup/transport, not guarantees about official
grading hardware. Scale fixtures check correctness of LCS at 1,000x1,000;
200-block merges; 100,000-node chains/stars; each search's maximum lengths;
50,000-character suffix arrays; and dense, duplicate and near-100,000-state Aho
inputs. Witness checks allow alternative optima rather than matching one plan.

| Command / fixture in `cases.json` | Input | Expected | Actual | Defect checked |
|---|---|---|---|---|
| lcs / lcs-alternative-optima | `{"task":"lcs","a":"ABCA","b":"BAC"}` | `{"length":2}` | `{"sequence":"AC","length":2}` | accept either valid optimal length-two witness |
| merge / merge-large-cost | `{"task":"merge","sizes":[1000000000,1000000000,1000000000]}` | `{"cost":5000000000}` | `{"cost":5000000000,"plan":[[1,1,2],[0,0,2]]}` | cost 5000000000 exceeds signed 32-bit |
| tree / tree-parent-choice | `{"task":"tree","n":3,"weights":[4,9,6],"edges":[[0,1],[1,2]]}` | `{"weight":10}` | `{"selected":[0,2],"weight":10}` | reconstruction must respect selected parent |
| naive / naive-empty-both | `{"task":"naive","text":"","pattern":""}` | `{"positions":[0]}` | `{"positions":[0]}` | empty boundary zero |
| kmp / kmp-overlap | `{"task":"kmp","text":"abababa","pattern":"aba"}` | `{"positions":[0,2,4]}` | `{"positions":[0,2,4]}` | fallback after full match |
| rk / rk-known-collision | `{"task":"rk","text":"abdj","pattern":"ab"}` | `{"positions":[0]}` | `{"positions":[0]}` | ab and dj hash to 75; dj must be rejected |
| suffix / suffix-banana | `{"task":"suffix","text":"banana"}` | `{"sa":[5,3,1,0,4,2],"lcp":[0,1,3,0,0,2],"repeated_length":3}` | `{"sa":[5,3,1,0,4,2],"repeated":"ana","lcp":[0,1,3,0,0,2]}` | Kasai predecessor convention |
| aho / aho-duplicates-suffix-reset | `{"task":"aho","patterns":["he","she","he"],"texts":["sheshe","sheshe",""]}` | `{"counts":[[2,2,2],[2,2,2],[0,0,0]]}` | `{"counts":[[2,2,2],[2,2,2],[0,0,0]]}` | failure suffix counts, duplicate columns, independent repeated texts |

## Reproducible search comparison

Run `python tests/student/benchmark.py`. The harness uses
`java.util.Random(20261108+n)`. Random text is uniform over the 65-character
alphabet in `benchmark-results.json`, with the pattern taken from `text` at
`n/3` to ensure a match. Repetitive text/pattern are entirely `a`. The three
`(n,m)` pairs are `(500,25)`, `(1500,75)`, `(3000,150)`, within the common
domain. Every method receives identical inputs, and its entire positions list
is checked against the independent `String.startsWith` test oracle before timing.
Built-in matching is confined to the test oracle.

Each method/input gets 20 untimed warm-up calls then three measured runs of ten
calls. `System.nanoTime` durations are divided by ten and reported as ms/call;
the middle of the three values is the median. The identical timing boundary
includes dispatch, algorithm preprocessing, matching, output construction and
volatile result-size consumption. Compilation, JVM startup, JSON transport,
generator and correctness checks are excluded. JIT tier/CPU affinity are not
controlled and garbage collection is not forced; 20 calls do not ensure steady
state. Runtime/hardware are above. Raw times and metadata are retained in
`tests/student/benchmark-results.json` and `benchmark-results.md`. The table
below records the final sequential local benchmark run.

| Case | n | m | Algorithm | Run 1 (ms/call) | Run 2 | Run 3 | Median | Correct |
|---|---:|---:|---|---:|---:|---:|---:|---|
| random | 500 | 25 | naive | 0.033460 | 0.033300 | 0.033230 | 0.033300 | yes |
| random | 500 | 25 | kmp | 0.025890 | 0.025570 | 0.025190 | 0.025570 | yes |
| random | 500 | 25 | rk | 0.044790 | 0.051260 | 0.042120 | 0.044790 | yes |
| random | 1500 | 75 | naive | 0.079360 | 0.076870 | 0.047870 | 0.076870 | yes |
| random | 1500 | 75 | kmp | 0.072670 | 0.071770 | 0.072150 | 0.072150 | yes |
| random | 1500 | 75 | rk | 0.128800 | 0.114990 | 0.060250 | 0.114990 | yes |
| random | 3000 | 150 | naive | 0.054750 | 0.053740 | 0.053580 | 0.053740 | yes |
| random | 3000 | 150 | kmp | 0.062330 | 0.049780 | 0.039590 | 0.049780 | yes |
| random | 3000 | 150 | rk | 0.045180 | 0.045140 | 0.044840 | 0.045140 | yes |
| repetitive | 500 | 25 | naive | 0.155040 | 0.147750 | 0.132090 | 0.147750 | yes |
| repetitive | 500 | 25 | kmp | 0.017090 | 0.021800 | 0.017200 | 0.017200 | yes |
| repetitive | 500 | 25 | rk | 0.099950 | 0.098440 | 0.091590 | 0.098440 | yes |
| repetitive | 1500 | 75 | naive | 0.182820 | 0.182990 | 0.188250 | 0.182990 | yes |
| repetitive | 1500 | 75 | kmp | 0.013710 | 0.014000 | 0.014560 | 0.014000 | yes |
| repetitive | 1500 | 75 | rk | 0.367710 | 0.379410 | 0.364350 | 0.367710 | yes |
| repetitive | 3000 | 150 | naive | 0.687700 | 0.684330 | 0.680320 | 0.684330 | yes |
| repetitive | 3000 | 150 | kmp | 0.033890 | 0.035950 | 0.035240 | 0.035240 | yes |
| repetitive | 3000 | 150 | rk | 1.216140 | 1.222840 | 1.221670 | 1.221670 | yes |

At the largest repetitive input, the measured medians were 0.684330 ms/call for
naive, 0.035240 for KMP and 1.221670 for exact RK. All repetitive windows are
genuine candidates, so naive and exact RK repeatedly compare a long pattern;
KMP reuses its prefix information. Random text usually rejects naive alignments
on their first character and RK rejects most windows by hash, making overhead
and JIT effects prominent at these sizes. For example, random medians do not
increase monotonically: this short warm-up and the JVM's changing compilation
state limit cross-size timing conclusions. These observations do not prove
asymptotic complexity or a universal speed ranking.

The deliberately small modulus 101 is a collision laboratory. Spurious equal
hashes require extra verification, while genuine repetitive matches also force
verification even with a larger modulus. These measurements do not represent
every production rolling-hash design. Collision correctness is separately
checked by `rk-known-collision` (`abdj`/`ab` -> `[0]`), not inferred from timing.


## Sources and AI assistance

- [Official template/specification](https://github.com/RZhaniya/daa-assignment-4-archive):
  contracts, required algorithms/bounds, rubric, testing rules and algorithm notes.
- [Interactive lesson](https://rzhaniya.github.io/daa-assignment-4-archive/) and
  supplied `docs/content.js`, plus both ungraded practice sets: recurrences,
  invariants, examples and preparation topics. Practice is separate from the defence.
- OpenAI Codex and cooperating coding agents generated the Java implementation,
  student tests, benchmark, analysis and study notes; performed code review and
  automated compilation/testing; and created the repository through GitHub's
  template-generation API because Windows browser control was blocked by URL policy.
- Required algorithms use standard arrays/collections and supplied JSON transport,
  without algorithm libraries or built-in search replacing their implementations.
  Simpler built-ins are permitted in independent test oracles.
- Verification reported here was performed automatically during this session. It
  does not claim the student personally authored/reviewed all code, completed
  interactive practice or took the defence. The student should review the code
  and reproduce the checks to prepare for the independent defence.
