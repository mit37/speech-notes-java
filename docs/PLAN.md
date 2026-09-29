# PLAN — `speech-notes-java` (v2 rebuild)

Read [`../STANDARDS.md`](../STANDARDS.md) first, then [`../PRD.md`](../PRD.md). If they
disagree, STANDARDS wins. This file restates the plan as checkboxes so progress is visible
in the repo (STANDARDS §6.1).

- **Repo:** `github.com/mit37/speech-notes-java` — public, MIT, live since 2026-09-26 and released as
  `v2.0.0` (annotated tag on `c3f6aff`) with the Windows app image attached, CI green, the seven PRD
  topics set, and `main` protected with both CI jobs as required status checks. The demo recording is
  the one item still open.
- **Default branch:** `main`
- **Stack (PRD §4):** Java 21 · Gradle (Kotlin DSL) · JavaFX 21 · Vosk · Jackson · OpenPDF ·
  JUnit 5 + AssertJ + Mockito · TestFX (headless via Monocle) · Spotless + Checkstyle
- **Who built what:** all eight milestones were built by **Freebuff/GLM** under Mitansh's direction.
  Design, specs, review and evaluation are his. Commit dates are real — no `--date`, no
  `GIT_AUTHOR_DATE` / `GIT_COMMITTER_DATE` tricks (STANDARDS §1.2).

## Definition of Done (PRD §8), restated

- [x] `./gradlew run --args="--file demo.wav"` produces notes fully offline — verified end to end on
      `eval/audio/lecture-01-caching.wav` (105 words, RTF 0.10, exit 0), and by `CliFileModeTest`
- [x] `RuleBasedFormatter` + Vosk wrapper tests (≥ 50) — 229 tests in 31 classes, 227 pass, 2 skipped
      (`scripts/test_count.sh`); the formatter/Vosk part alone is well past 50
- [x] Gemini features optional, capped and mocked in CI — `FakeGeminiServer`, per-run request and
      character caps, `--dry-run`, no key in CI
- [x] Eval numbers generated (WER, real-time factor, keyword recall) — [`../eval/results.md`](../eval/results.md)
      and [`../eval/results-large.md`](../eval/results-large.md), both written by `scripts/run_eval.sh`
- [x] README states exactly which parts are offline — every number in it names the script that
      produced it, and the offline/online split is stated twice
- [x] The CI pipeline is green on GitHub and on this machine: Spotless → Checkstyle → test → test
      count → package → gitleaks, all six steps, the last one runnable locally as well
- [x] Tag `v2.0.0` — annotated tag on `c3f6aff`, pushed, released with the `jpackage` app image
      attached (`speech-notes-java-2.0.0-windows-x64.zip`, 91.9 MiB, launcher `--version` exits 0).
      The demo recording is the one asset still missing from the release.

## DoD → evidence map (STANDARDS §3: every acceptance criterion gets an automated test)

| DoD item | Evidence required before `v2.0.0` | Where | State |
|---|---|---|---|
| Offline file mode | `./gradlew run --args="--file <fixture>"` transcript + notes, replayable in CI | M2 | done — `CliFileModeTest`, `VoskTranscriberTest` |
| ≥ 50 formatter/Vosk tests | `./gradlew test`; count printed by `scripts/test_count.sh` | M2–M3 | done — 229 total, printed on every CI run |
| Capped, mocked Gemini | HTTP mocked with a fake server; cap enforced by a unit test; no key in CI | M6 | done — `GeminiClientTest`, `GeminiBudgetTest`, `GeminiFormatterTest`, `GeminiQaServiceTest` |
| Generated eval numbers | `scripts/run_eval.sh` printing WER, real-time factor, keyword recall | M8 | done — both result files committed |
| README honesty | offline/online split stated; "What this does not do"; no typed numbers | M8 | done — every number in Results names its script |

## Milestones (PRD §6), in order

**M1 — Gradle scaffold, CI, model download script**

- [x] `docs/PLAN.md` restates the DoD and milestones
- [x] Gradle Kotlin DSL scaffold, Java 21 toolchain, wrapper committed
- [x] Formatting (Spotless) + static analysis (Checkstyle) wired into `check`
- [x] CLI entry point behind `./gradlew run` (file/mic/UI modes)
- [x] One passing smoke test (plus CLI parser tests)
- [x] `scripts/download_model.sh` — license-checked, checksum-pinned Vosk model download
- [x] CI: Spotless → Checkstyle → test → test count → package → gitleaks
- [x] `scripts/scan_secrets.sh` — the same scan runnable locally (`.gitleaks.toml` skips `build/`,
      `models/` and friends so it finishes in seconds)
- [x] `.gitignore`, `.env.example`, LICENSE, README skeleton per STANDARDS §4

**M2 — File-mode transcription (Vosk)**

- [x] `AudioSource` interface: `FileAudioSource` + `MicAudioSource`
- [x] `VoskRecognizer` wrapper emitting partial + final results
- [x] `./gradlew run --args="--file demo.wav"` writes notes fully offline
- [x] Tests run against the committed synthetic WAV fixtures (`eval/audio/*.wav`, 5 files)

**M3 — TranscriptStore + RuleBasedFormatter** (the bulk of the test suite)

- [x] `TranscriptStore` with timestamped events
- [x] `RuleBasedFormatter`: pause-based paragraphs, "first/second/finally" lists, key terms,
      `action:`/`todo` extraction
- [x] Key-term extractor revisited after the eval showed it surfacing `minute`, `percent` and
      `reached` ahead of the topic: repeated **phrases** with a cohesion score, a topic-position
      bonus and no `-ed` tail, plus a phrase-aware highlighter. Measured, not eyeballed — the
      key-term-recall column in `eval/results.md` is produced by the same script (12.9%, with the
      fixture-length caveat stated in the report and the README)
- [x] Test count for the formatter/Vosk wrappers crosses 50

**M4 — JavaFX UI** (transcript + notes panes)

- [x] MVVM-ish controllers + observable view models (`MainView`, `RecordViewModel`, `LiveSessionService`)
- [x] TestFX smoke test, headless via Monocle — with real robot clicks, plus pause/resume, stop,
      screenshot and Q&A flows
- [x] Screenshot hotkey (PRD G4): Ctrl/Cmd+Shift+S, registered on the window's scene and tested;
      window-scoped by design — a system-wide hook would need a native library the PRD's stack does
      not include (stated in the README limitations)

**M5 — Mic mode**

- [x] Real microphone capture (`MicAudioSource`), bounded by `--seconds`, pause/resume
- [x] Device-less tests via fakes (`FakeAudioSource`, `SlowAudioSource`, `SilentAudioSource`,
      `PausableAudioSource`, `LimitedAudioSource`)

**M6 — GeminiFormatter + QAService** (optional, capped, mocked in CI)

- [x] REST through `java.net.http`; key from the environment; hard request + character caps per run
- [x] Mocked-HTTP tests; disabled with a clear message when no key is present (`--format gemini`
      exits 1 with the fix; the UI falls back and says so)

**M7 — Export MD/PDF**

- [x] Markdown exporter + PDF exporter (OpenPDF), golden-file tests including a PDFBox read-back,
      bold runs, pagination and a sanitisation case

**M8 — Eval, README, release**

- [x] 5 synthetic lecture WAVs (local TTS, committed, small) + hand-written keyword rubrics
- [x] WER (small vs large model), real-time factor, keyword recall — generated by `scripts/run_eval.sh`
- [x] Key-term recall for the offline formatter, scored against the same rubric but only against the
      phrases that were actually heard, so a recognition miss is never charged to the formatter
- [x] `docs/DEMO.md` recording script (STANDARDS §5 — GUI project, Mitansh records it)
- [x] README filled from generated output only; `jpackage` app image built and run by
      `scripts/package_app.sh`
- [x] **Tag `v2.0.0`** — annotated tag on `c3f6aff`, released 2026-09-28 with the app image attached
- [ ] **Demo recording** — needs a display and a microphone; `docs/DEMO.md` is the script. When it is
      recorded it goes in the release assets and gets linked from the README's first section.

## First push checklist (Mitansh — STANDARDS §3 and §7)

The first push went out from this machine on 2026-09-26, and `v2.0.0` was published from the same
machine on 2026-09-28. Two items are left, and neither is a code change: the demo recording (needs a
display and a microphone) and the project page on mitanshm.com.

- [x] First commit: `69d4359`, Conventional Commits, real dates, nothing backdated. `gradlew` is
      committed 100755 so CI's `./gradlew` runs.
- [x] Create `mit37/speech-notes-java` (public, MIT), push `main` — 2026-09-26.
- [x] Repo description, copied from [`../PRD.md`](../PRD.md) line 4:
      *Offline speech-to-notes in Java: on-device Vosk transcription, optional Gemini-formatted
      notes, and screenshot Q&A.* — set when the repo was created.
- [x] CI green on `main`: run #2, `40711b5`. Run #1 on `69d4359` failed at the test-count step with
      exit 126 because `scripts/test_count.sh` was committed 100644, so Ubuntu would not execute it;
      `40711b5` sets every `scripts/*.sh` 100755 and adds a CI step that fails with a named error if
      one is not.
- [x] Topics (PRD line 5, all 7): `java`, `speech-recognition`, `vosk`, `javafx`, `gemini`,
      `note-taking`, `offline-first`.
- [x] Tag `v2.0.0` on `c3f6aff` with the `jpackage` app image attached to the release. The demo
      recording from `docs/DEMO.md` is still to come — a published release can take assets later.
      The tag deliberately sits on `c3f6aff`, the commit whose CI run was green and whose tree was
      packaged, and the commits that *record* the release came after it: they could not be written
      before it existed. So the README inside the `v2.0.0` source tarball is two documentation
      commits behind `main` on that one point — the release notes link to the current README rather
      than the tarball's copy.
- [x] `main` protected. Pull requests are not required: this is a solo repo that the owner pushes to
      directly, and `enforce_admins` is off so that stays possible. What is enforced is the rest —
      force-pushes and deletions are off, and both CI jobs (`lint, test, package`, `gitleaks`) are
      required status checks.
- [ ] Update the project page on mitanshm.com so its claims match this README (new numbers, the
      v2 note, the repo link) — STANDARDS §7.

### Housekeeping on this machine

- `build/tmp/large-model/vosk-model-en-us-0.22.zip` is the 1.8 GB download kept only so
  `scripts/download_model.sh vosk-model-en-us-0.22` does not have to fetch it again. The extracted
  `models/vosk-model-en-us-0.22` (2.7 GB) is what `run_eval.sh` actually uses, so the zip can be
  deleted whenever space is tight; both paths are gitignored.
- The working directory is a OneDrive folder named `990-PF pipeline`, from another project. The
  repository name is `speech-notes-java` and that is what the README and the app use.

## Cloud-instance constraints (STANDARDS §6.4)

This instance has **no microphone and no display**. What that changes:

| Requirement | How it is built anyway | What stays "not measured" |
|---|---|---|
| Mic mode (G1) | Behind `AudioSource`; tested with a fake source | Live mic latency / device behaviour |
| JavaFX UI (G6) | TestFX with Monocle headless driver, real robot clicks | Real rendering, windowing, hotkey capture |
| Screenshot Q&A (G4) | `ScreenCapture` behind an interface, `java.awt.Robot` in the real impl | Real screen capture (needs a display) — the test for it reports `skipped`, not `passed` |
| Gemini formatting + Q&A | Mocked HTTP in CI; dry-run mode with no key | Real Gemini note quality unless a key is supplied |
| WER / real-time factor | Computed here from committed fixtures | Numbers on other hardware |
| MP3 decode | Decoder bundled and asserted to register itself | End-to-end MP3 transcription — no encoder on this machine, so no fixture (`skipped`) |

## Non-goals that stay out (PRD §2)

Speaker diarization · cloud sync · mobile · languages other than English. The Vosk model
choice stays configurable, but no second language ships in v2.
