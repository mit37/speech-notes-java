# Eval results

Generated: 2026-09-26 22:33 UTC by `scripts/run_eval.sh` — offline, with `Vosk vosk-api (16 kHz mono, model vosk-model-en-us-0.22)`.

| Fixture | Audio s | Wall s | Real-time factor | Words | WER | Keyword recall | Key-term recall |
|---|---|---|---|---|---|---|---|
| lecture-01-caching | 40.2 | 9.8 | 0.24 | 105 | 4.8% | 75.0% (6/8) | 16.7% (1/6) |
| lecture-02-budgeting | 38.8 | 6.7 | 0.17 | 95 | 5.3% | 87.5% (7/8) | 14.3% (1/7) |
| lecture-03-testing | 38.7 | 7.3 | 0.19 | 102 | 2.9% | 87.5% (7/8) | 0.0% (0/7) |
| lecture-04-interviews | 37.5 | 6.8 | 0.18 | 90 | 6.7% | 71.4% (5/7) | 0.0% (0/5) |
| lecture-05-indexes | 39.3 | 8.6 | 0.22 | 97 | 6.2% | 87.5% (7/8) | 28.6% (2/7) |

- Mean WER: **5.2%** (25 errors over 489 reference words, 5 fixtures).
- Overall real-time factor: **0.2** (39.3s of processing for 194.5s of audio).
- Keyword recall: **82.1%** of the hand-written rubric phrases were heard by the recogniser.
- Key-term recall: **12.5%** of the phrases it heard were surfaced as key terms by the offline formatter.

## What the recogniser missed

- `lecture-01-caching`: cache, two weak caches
- `lecture-02-budgeting`: payday
- `lecture-03-testing`: coverage badge
- `lecture-04-interviews`: trade offs, practise three problems
- `lecture-05-indexes`: writes slower

## What the formatter put in Key terms

Scored against the same hand-written rubric, but only against the phrases the recogniser
actually heard: a phrase that never reached the transcript cannot be a formatter
failure. Key terms are quoted exactly as the notes contain them.

The denominator is small and the clips are short — see the last section before reading
these numbers as a verdict on the formatter.

- `lecture-01-caching` — minute expiry, hit rate, data, percent, week, cash
  - heard by the recogniser but not key-termed: expiry rule, stale data, eighty four percent, ninety one percent, bet about the future
- `lecture-02-budgeting` — separate savings, months, day
  - heard by the recogniser but not key-termed: fixed cost, rent, emergency fund, three months, six months, income is irregular
- `lecture-03-testing` — test, code, framework, suite
  - heard by the recogniser but not key-termed: never fails, break the code, test the rule, slower version of the framework, before every commit, two hundred and thirty tests, flaky test
- `lecture-04-interviews` — answers, interview
  - heard by the recogniser but not key-termed: restate the problem, simplest approach, complexity, memory test, going quiet
- `lecture-05-indexes` — query plan, index, data, table, read
  - heard by the recogniser but not key-termed: reads faster, composite index, table scan, statistics, promises expire

## What the recogniser wrote (lecture-01-caching, verbatim)

```text
this lecture is about cashing in web systems first a cash only pays off when the same data is requested more than once second every cached entry needs an expiry rule otherwise stale data outlives the bug that created it third measure the hit rate before you add another layer because two week caches are still weak in our experiment a one minute expiry reached eighty four percent in a five minute expiry reached ninety one percent action instrument the hit rate on the product page this week finally remember that a cash is a bet about the future not a copy of the truth
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
