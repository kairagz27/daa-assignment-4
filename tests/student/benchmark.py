"""Compile and measure the three implemented searches on identical verified inputs."""
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
BUILD = ROOT / "Java" / "build"
BUILD.mkdir(exist_ok=True)
subprocess.run([
    "javac", "--release", "17", "-encoding", "UTF-8", "-d", str(BUILD),
    *map(str, sorted((ROOT / "Java" / "source").glob("*.java"))),
    str(HERE / "SearchBenchmark.java"),
], check=True, timeout=60)
process = subprocess.run(
    ["java", "-Xmx384m", "-cp", str(BUILD), "SearchBenchmark"],
    capture_output=True, text=True, encoding="utf-8", check=True, timeout=60,
)
report = json.loads(process.stdout)
(HERE / "benchmark-results.json").write_text(
    json.dumps(report, indent=2) + "\n", encoding="utf-8",
)
table = [
    "| Case | n | m | Algorithm | Run 1 (ms/call) | Run 2 | Run 3 | Median | Correct |",
    "|---|---:|---:|---|---:|---:|---:|---:|---|",
]
for row in report["rows"]:
    times = " | ".join(f"{value:.6f}" for value in row["runs_ms"])
    table.append(
        f'| {row["case"]} | {row["n"]} | {row["m"]} | {row["algorithm"]} | '
        f'{times} | {row["median_ms"]:.6f} | yes |'
    )
(HERE / "benchmark-results.md").write_text("\n".join(table) + "\n", encoding="utf-8")
print("\n".join(table))
