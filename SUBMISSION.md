# Submission notes

Chosen language: **Java** (`submission.json`).

The implementation is in `Java/source/Algorithms.java`; the supplied JSONL
transport is preserved. `ANALYSIS.md` documents all eight commands, correctness,
reconstruction, complexity, environment, actual commands, sources and AI
assistance. `tests/student` contains independent tests and recorded results,
plus the reproducible search benchmark and raw measurements.

Run from the repository root with Python 3.12+ and a Java 17+ JDK:

```sh
python tools/run.py --setup
python tools/run.py --check
python tests/student/run_tests.py --skip-build
python tests/student/benchmark.py
```

For Moodle Online text use `https://github.com/kairagz27/daa-assignment-4/commit/`
followed by the full 40-character SHA from `git rev-parse HEAD`, and state
`Language: Java`. Verify the Public checks run for that SHA. The repository is
public, so the instructor can access it. Click **Submit assignment** and verify
the final submission status; saving a draft is not a final submission. The
deadline supplied for the assignment is 8 November 2026, 23:59.

The independent defence is a separate 15-question/25-minute assessment worth
50 points. Review the official lesson and ungraded practice; take the assessed
quiz independently. The practical work and automated verification here do not
constitute completion of that quiz.
