"""Independent deterministic correctness checks for the submitted JSONL program.

Run from the repository root: python tests/student/run_tests.py
Only the Python standard library is required. No production algorithm is imported.
"""
from __future__ import annotations

import argparse
from collections import Counter
from functools import cmp_to_key, lru_cache
import json
from pathlib import Path
import random
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
SEED = 20261108


def occurrences(text: str, pattern: str) -> list[int]:
    # The test oracle deliberately uses Python's independent string primitive.
    return [i for i in range(len(text) - len(pattern) + 1)
            if text.startswith(pattern, i)]


def lcs_oracle(a: str, b: str) -> int:
    """Exhaustively enumerate subsequences, rather than reuse prefix DP."""
    best = 0
    for bits in range(1 << len(a)):
        candidate = ''.join(c for i, c in enumerate(a) if bits >> i & 1)
        if len(candidate) > best and is_subsequence(candidate, b):
            best = len(candidate)
    return best


def is_subsequence(candidate: str, text: str) -> bool:
    index = 0
    for char in text:
        if index < len(candidate) and char == candidate[index]:
            index += 1
    return index == len(candidate)


@lru_cache(maxsize=None)
def merge_oracle(blocks: tuple[int, ...]) -> int:
    """Enumerate possible NEXT adjacent merges, not interval DP's last split."""
    if len(blocks) < 2:
        return 0
    return min(blocks[i] + blocks[i + 1]
               + merge_oracle(blocks[:i] + (blocks[i] + blocks[i + 1],) + blocks[i + 2:])
               for i in range(len(blocks) - 1))


def tree_oracle(weights: list[int], edges: list[list[int]]) -> int:
    best = 0
    for bits in range(1 << len(weights)):
        if not any(bits >> u & 1 and bits >> v & 1 for u, v in edges):
            best = max(best, sum(w for v, w in enumerate(weights) if bits >> v & 1))
    return best


def suffix_oracle(text: str) -> dict:
    """Direct suffix comparison: no rank doubling, and no suffix-string copies."""
    n = len(text)

    def compare(i: int, j: int) -> int:
        while i < n and j < n and text[i] == text[j]:
            i += 1
            j += 1
        if i == n or j == n:
            return (i < n) - (j < n)
        return (text[i] > text[j]) - (text[i] < text[j])

    sa = sorted(range(n), key=cmp_to_key(compare))
    lcp = [0] * n
    best = ''
    for rank in range(1, n):
        i, j = sa[rank - 1], sa[rank]
        length = 0
        while i + length < n and j + length < n and text[i + length] == text[j + length]:
            length += 1
        lcp[rank] = length
        if length > len(best):
            best = text[i:i + length]
    return {'sa': sa, 'lcp': lcp, 'repeated_length': len(best)}


def int_value(value) -> bool:
    return type(value) is int and -(1 << 63) <= value < (1 << 63)


def int_list(value) -> bool:
    return type(value) is list and all(int_value(item) for item in value)


def validate(request: dict, actual: dict, expected: dict) -> None:
    assert type(actual) is dict, 'response must be a JSON object'
    task = request['task']
    if task == 'lcs':
        length, sequence = actual['length'], actual['sequence']
        assert int_value(length) and type(sequence) is str
        assert length == expected['length'] == len(sequence), 'LCS optimum/length'
        assert is_subsequence(sequence, request['a']) and is_subsequence(sequence, request['b'])
    elif task == 'merge':
        assert int_value(actual['cost']) and type(actual['plan']) is list
        sizes = request['sizes']
        blocks = [(i, i) for i in range(len(sizes))]
        total = 0
        assert len(actual['plan']) == max(0, len(sizes) - 1)
        for step in actual['plan']:
            assert int_list(step) and len(step) == 3
            left, middle, right = step
            index = blocks.index((left, middle))
            assert index + 1 < len(blocks) and blocks[index + 1] == (middle + 1, right)
            blocks[index:index + 2] = [(left, right)]
            total += sum(sizes[left:right + 1])
        assert len(blocks) <= 1 and actual['cost'] == total == expected['cost']
    elif task == 'tree':
        chosen = actual['selected']
        assert int_value(actual['weight']) and int_list(chosen)
        chosen_set = set(chosen)
        assert len(chosen_set) == len(chosen) and all(0 <= v < request['n'] for v in chosen)
        assert not any(u in chosen_set and v in chosen_set for u, v in request['edges'])
        assert sum(request['weights'][v] for v in chosen) == actual['weight'] == expected['weight']
    elif task in ('naive', 'kmp', 'rk'):
        assert int_list(actual['positions']) and actual['positions'] == expected['positions']
    elif task == 'suffix':
        assert int_list(actual['sa']) and int_list(actual['lcp']) and type(actual['repeated']) is str
        assert actual['sa'] == expected['sa'] and actual['lcp'] == expected['lcp']
        repeated = actual['repeated']
        assert len(repeated) == expected['repeated_length']
        # find a second overlapping occurrence without requiring a particular tied witness
        assert not repeated or len(occurrences(request['text'], repeated)) >= 2
    elif task == 'aho':
        counts = actual['counts']
        assert type(counts) is list and len(counts) == len(request['texts'])
        assert all(int_list(row) and len(row) == len(request['patterns']) for row in counts)
        assert counts == expected['counts']
    else:
        raise AssertionError('unrecognized test task')


def oracle(request: dict) -> dict:
    task = request['task']
    if task == 'lcs':
        return {'length': lcs_oracle(request['a'], request['b'])}
    if task == 'merge':
        return {'cost': merge_oracle(tuple(request['sizes']))}
    if task == 'tree':
        return {'weight': tree_oracle(request['weights'], request['edges'])}
    if task in ('naive', 'kmp', 'rk'):
        return {'positions': occurrences(request['text'], request['pattern'])}
    if task == 'suffix':
        return suffix_oracle(request['text'])
    if task == 'aho':
        return {'counts': [[len(occurrences(text, pattern)) for pattern in request['patterns']]
                           for text in request['texts']]}
    raise ValueError(task)


def small_cases() -> list[dict]:
    cases = json.loads((Path(__file__).with_name('cases.json')).read_text(encoding='utf-8'))
    rng = random.Random(SEED)
    alphabet = 'ab AB!~'

    def add(name, request):
        cases.append({'name': name, 'input': request, 'defect': 'independent randomized oracle'})

    for index in range(60):
        a = ''.join(rng.choices(alphabet, k=rng.randrange(9)))
        b = ''.join(rng.choices(alphabet, k=rng.randrange(9)))
        add(f'lcs-random-{index}', {'task': 'lcs', 'a': a, 'b': b})
        add(f'merge-random-{index}', {'task': 'merge', 'sizes': [rng.randrange(6) for _ in range(rng.randrange(8))]})
        n = rng.randrange(11)
        edges = [[v, rng.randrange(v)] for v in range(1, n)]
        rng.shuffle(edges)
        add(f'tree-random-{index}', {'task': 'tree', 'n': n,
                                    'weights': [rng.randrange(-9, 10) for _ in range(n)], 'edges': edges})
        text = ''.join(rng.choices(alphabet, k=rng.randrange(45)))
        pattern = ''.join(rng.choices(alphabet, k=rng.randrange(12)))
        for task in ('naive', 'kmp', 'rk'):
            add(f'{task}-random-{index}', {'task': task, 'text': text, 'pattern': pattern})
        add(f'suffix-random-{index}', {'task': 'suffix', 'text': text})
        patterns = [''.join(rng.choices(alphabet, k=rng.randrange(1, 7))) for _ in range(rng.randrange(9))]
        if patterns:
            patterns += [patterns[0]]
        texts = [text, text, '', pattern]
        add(f'aho-random-{index}', {'task': 'aho', 'patterns': patterns, 'texts': texts})
    for case in cases:
        case['expected'] = oracle(case['input'])
    return cases


def scale_cases() -> list[dict]:
    cases = []

    def add(name, request, expected, defect):
        cases.append({'name': name, 'input': request, 'expected': expected, 'defect': defect})

    add('lcs-1000-by-1000', {'task': 'lcs', 'a': 'ab' * 500, 'b': 'ba' * 500},
        {'length': 999}, 'full DP domain and alternative optimal witnesses')
    add('merge-200-large', {'task': 'merge', 'sizes': [10**9] * 200}, {'cost': 1544 * 10**9},
        '200 equal leaves have total optimal depth 200*8-(256-200); signed 64-bit cost')
    add('merge-200-zeros', {'task': 'merge', 'sizes': [0] * 200}, {'cost': 0},
        'many tied optimal chronological plans')
    n = 100000
    add('tree-100000-chain', {'task': 'tree', 'n': n, 'weights': [10**9] * n,
                            'edges': [[i - 1, i] for i in range(1, n)]},
        {'weight': 50000 * 10**9}, 'deep chain must avoid recursion failure and 32-bit overflow')
    add('tree-100000-star', {'task': 'tree', 'n': n, 'weights': [10**9] * n,
                           'edges': [[0, i] for i in range(1, n)]},
        {'weight': 99999 * 10**9}, 'full degree and long sum')
    for task, text, pattern in (
        ('naive', 'a' * 3000, 'a' * 1500),
        ('naive', 'a' * 3000, 'a' * 2999 + 'b'),
        ('naive', 'a' * 3000, ''),
        ('kmp', 'a' * 200000, 'a' * 100000),
        ('kmp', 'a' * 200000, 'a' * 99999 + 'b'),
        ('kmp', 'a' * 200000, ''),
        ('rk', 'a' * 10000, 'a' * 2000),
        ('rk', 'a' * 2000, 'a' * 10000),
        ('rk', 'abdj' * 2500, 'ab'),
    ):
        add(f'{task}-scale-{len(cases)}', {'task': task, 'text': text, 'pattern': pattern},
            {'positions': occurrences(text, pattern)}, 'full declared bounds, overlap/fallback/collision verification')
    n = 50000
    add('suffix-50000-equal', {'task': 'suffix', 'text': 'a' * n},
        {'sa': list(range(n - 1, -1, -1)), 'lcp': list(range(n)), 'repeated_length': n - 1},
        'equal characters, overlapping longest repeat and no added sentinel')
    rng = random.Random(SEED + 1)
    text = ''.join(chr(rng.randrange(32, 127)) for _ in range(n))
    add('suffix-50000-printable-ascii', {'task': 'suffix', 'text': text}, suffix_oracle(text),
        'full ASCII suffix order and direct character-comparison LCP oracle')
    patterns = ['a' * 10] * 10000
    texts = ['a' * 2000] * 100
    add('aho-10000-patterns-100-texts', {'task': 'aho', 'patterns': patterns, 'texts': texts},
        {'counts': [[1991] * 10000 for _ in range(100)]},
        'maximum p,L,q,total text; duplicate terminals and independent text counts')
    patterns = ['a' * i for i in range(1, 447)]
    add('aho-dense-suffixes', {'task': 'aho', 'patterns': patterns, 'texts': ['a' * 200000, '']},
        {'counts': [[200001 - i for i in range(1, 447)], [0] * 446]},
        'dense suffix matches need aggregation instead of per-match output traversal')
    # Nearly 100000 distinct trie states; broad first character coverage, long common tails.
    patterns = [chr(32 + i) + 'x' * 1051 for i in range(95)]
    texts = ['x' * 200000]
    add('aho-nearly-100000-states', {'task': 'aho', 'patterns': patterns, 'texts': texts},
        {'counts': [[0 if pattern[0] != 'x' else 198949 for pattern in patterns]]},
        'near maximum states and dense fixed alphabet transition memory')
    return cases


def execute(cases: list[dict], label: str, report: list[dict]) -> dict:
    payload = ''.join(json.dumps(case['input'], separators=(',', ':')) + '\n' for case in cases)
    start = time.perf_counter()
    process = subprocess.run(['java', '-Xmx384m', '-cp', str(ROOT / 'Java' / 'build'), 'Main'],
                             input=payload, text=True, encoding='utf-8', capture_output=True, timeout=20)
    elapsed = time.perf_counter() - start
    assert process.returncode == 0, process.stderr[-4000:]
    output_bytes = len(process.stdout.encode('utf-8'))
    assert output_bytes <= 8 * 1024 * 1024, 'batch exceeded published output limit'
    lines = process.stdout.splitlines()
    assert len(lines) == len(cases), f'{label}: expected {len(cases)} lines, got {len(lines)}'
    for case, line in zip(cases, lines):
        actual = json.loads(line)
        try:
            validate(case['input'], actual, case['expected'])
        except (AssertionError, KeyError, ValueError, TypeError) as error:
            raise AssertionError(f"{case['name']}: {error}; actual={str(actual)[:300]}") from error
        record = {'name': case['name'], 'task': case['input']['task'], 'status': 'PASS', 'defect': case['defect']}
        if len(line) < 500 and len(json.dumps(case['input'])) < 500:
            record.update(input=case['input'], expected=case['expected'], actual=actual)
        else:
            record['actual_summary'] = {key: (f'list length {len(value)}' if isinstance(value, list)
                                              else f'string length {len(value)}' if isinstance(value, str)
                                              else value) for key, value in actual.items()}
        report.append(record)
    print(f'PASS {label}: {len(cases)} requests, {elapsed:.3f}s process wall time, '
          f'{output_bytes} output bytes')
    return {'name': label, 'requests': len(cases), 'process_wall_seconds': elapsed,
            'output_bytes': output_bytes, 'status': 'PASS'}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--skip-build', action='store_true')
    parser.add_argument('--small-only', action='store_true')
    args = parser.parse_args()
    if not args.skip_build:
        subprocess.run([sys.executable, str(ROOT / 'tools' / 'run.py'), '--setup'], check=True, cwd=ROOT)
    report = []
    small = small_cases()
    batches = [execute(small, 'small independent oracles', report)]
    batches.append(execute(small[:40] + list(reversed(small[:40])) + small[:40],
                           'mixed repeated state reset', report))
    scales = [] if args.small_only else scale_cases()
    for case in scales:
        batches.append(execute([case], case['name'], report))
    output = {'seed': SEED, 'python': sys.version, 'passed': len(report),
              'by_task': dict(Counter(case['task'] for case in report)), 'batches': batches, 'cases': report}
    destination = Path(__file__).with_name('test-results.json')
    destination.write_text(json.dumps(output, indent=2, ensure_ascii=True) + '\n', encoding='utf-8')
    print(f'PASS ALL: {len(report)} validated requests; report: tests/student/test-results.json')


if __name__ == '__main__':
    main()
