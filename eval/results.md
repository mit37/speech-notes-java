# Eval results

Generated: 2026-09-26 22:32 UTC by `scripts/run_eval.sh` — offline, with `Vosk vosk-api (16 kHz mono, model vosk-model-small-en-us-0.15)`.

| Fixture | Audio s | Wall s | Real-time factor | Words | WER | Keyword recall | Key-term recall |
|---|---|---|---|---|---|---|---|
| lecture-01-caching | 40.2 | 4.0 | 0.1 | 105 | 7.6% | 62.5% (5/8) | 20.0% (1/5) |
| lecture-02-budgeting | 38.8 | 3.9 | 0.1 | 95 | 2.1% | 87.5% (7/8) | 14.3% (1/7) |
| lecture-03-testing | 38.7 | 4.1 | 0.11 | 102 | 4.9% | 87.5% (7/8) | 0.0% (0/7) |
| lecture-04-interviews | 37.5 | 4.2 | 0.11 | 90 | 4.4% | 85.7% (6/7) | 0.0% (0/6) |
| lecture-05-indexes | 39.3 | 3.4 | 0.09 | 97 | 6.2% | 75.0% (6/8) | 33.3% (2/6) |

- Mean WER: **5.1%** (25 errors over 489 reference words, 5 fixtures).
- Overall real-time factor: **0.1** (19.7s of processing for 194.5s of audio).
- Keyword recall: **79.5%** of the hand-written rubric phrases were heard by the recogniser.
- Key-term recall: **12.9%** of the phrases it heard were surfaced as key terms by the offline formatter.

## What the recogniser missed

- `lecture-01-caching`: cache, two weak caches, bet about the future
- `lecture-02-budgeting`: payday
- `lecture-03-testing`: coverage badge
- `lecture-04-interviews`: practise three problems
- `lecture-05-indexes`: writes slower, table scan

## What the formatter put in Key terms

Scored against the same hand-written rubric, but only against the phrases the recogniser
actually heard: a phrase that never reached the transcript cannot be a formatter
failure. Key terms are quoted exactly as the notes contain them.

The denominator is small and the clips are short — see the last section before reading
these numbers as a verdict on the formatter.

- `lecture-01-caching` — minute expiry, hit rate, percent, cash, data, week
  - heard by the recogniser but not key-termed: expiry rule, stale data, eighty four percent, ninety one percent
- `lecture-02-budgeting` — separate savings, months, day
  - heard by the recogniser but not key-termed: fixed cost, rent, emergency fund, three months, six months, income is irregular
- `lecture-03-testing` — test, code, framework, sweet
  - heard by the recogniser but not key-termed: never fails, break the code, test the rule, slower version of the framework, before every commit, two hundred and thirty tests, flaky test
- `lecture-04-interviews` — interview, answers
  - heard by the recogniser but not key-termed: restate the problem, simplest approach, complexity, trade offs, memory test, going quiet
- `lecture-05-indexes` — query plan, index, data, table
  - heard by the recogniser but not key-termed: reads faster, composite index, statistics, promises expire

## What the recogniser wrote (lecture-01-caching, verbatim)

```text
this lecture is about cashing in web systems first a cash only pays off when the same data is requested more than once second every cached entry needs an expiry rule otherwise stale data outlives the bug that created it third measure the hit rate before you add another layer because to we caches are still week in our experiment a one minute expiry reached eighty four percent in a five minute expiry reached ninety one percent action instrument the hit rate on the product page this week finally remember that a cash is a bed about the future not a copy of the truth
```

## What this does not measure

- A microphone, a real lecture, or any other language: the audio is synthetic speech
  (Windows SAPI voices) of the committed scripts in `eval/fixtures`.
- Whether the notes are *readable*. Key-term recall asks whether the right phrases were
  surfaced, not whether the paragraphs, bullets or action items are any good; those
  are covered by tests, not by a score.
- A large rubric: 39 hand-written phrases across five fixtures, written before the
  extractor was changed. A phrase list this short moves in 2.6-point steps per
  phrase, so treat the number as a smoke test, not a benchmark.
- Anything about longer recordings. Every fixture is about forty seconds long, and most
  of the rubric's phrases are used exactly once in that time. A frequency-based
  extractor can only surface what repeats, so these clips are its floor case: a
  twenty-minute lecture repeats its terms far more than a forty-second clip can.
- Gemini paths: this run is entirely offline and never touches the network.
