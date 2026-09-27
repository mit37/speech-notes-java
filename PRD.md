# PRD — Offline speech-to-notes (Java) (v2 rebuild)

**Repo:** `mit37/speech-notes-java`
**Description:** Offline speech-to-notes in Java: on-device Vosk transcription, optional Gemini-formatted notes, and screenshot Q&A.
**Topics:** `java`, `speech-recognition`, `vosk`, `javafx`, `gemini`, `note-taking`, `offline-first`
**Priority:** Tier 3 (Lab). It shows Java (useful for fintech/enterprise recruiters who look for it).

---

## 1. Pitch

Lecture or meeting audio → clean structured notes, **with transcription entirely on your machine**. Vosk handles speech-to-text offline. If you add a Gemini key, the raw transcript becomes formatted notes (headings, bullets, key terms, action items); without one, a rule-based formatter still gives you usable notes. Press a hotkey to screenshot a slide and ask a question about it.

Be precise in the README: **transcription is always offline; formatting and screenshot Q&A use Gemini only when enabled.**

## 2. Goals / non-goals

**Goals**
- G1. Live mode (mic) and file mode (WAV/MP3 via a bundled decoder) using the **Vosk Java API** with a small English model (downloaded by `scripts/download_model.sh`, not committed).
- G2. Rolling transcript with timestamps and partial vs final results shown in the UI.
- G3. Formatter interface:
  - `RuleBasedFormatter` (offline): paragraphs by pause length, detects "first/second/finally" lists, bolds repeated key terms (TF-IDF over the session), and extracts "action:"/"todo" phrases.
  - `GeminiFormatter` (optional): sends the transcript chunk → structured Markdown via the Gemini API (REST through `java.net.http`, key from an env var/settings), with a spend/requests cap.
- G4. Screenshot Q&A: a hotkey captures the screen (`java.awt.Robot`), shows a preview, the user types a question → Gemini multimodal answer attached to the notes at that timestamp. Disabled when offline, with a clear message.
- G5. Export notes to Markdown and PDF (OpenPDF or Apache PDFBox).
- G6. JavaFX desktop UI: record / pause / stop, transcript pane, notes pane, screenshot list.

**Non-goals:** speaker diarization, cloud sync, mobile, languages other than English (the design allows adding models; not done in v2).

## 3. Architecture

```
AudioSource (Mic | File) → VoskRecognizer wrapper → TranscriptStore (events)
                                              ↓
                          Formatter (RuleBased | Gemini) → NotesDocument
Screenshot hotkey → ScreenCapture → QAService (Gemini) → NotesDocument.attach(ts, img, q, a)
Exporter (MD | PDF)
UI (JavaFX, MVVM-ish: controllers + observable view models)
```

## 4. Tech stack

Java 21 (records, sealed interfaces, virtual threads for background work), Gradle (Kotlin DSL), JavaFX 21+, Vosk (`com.alphacephei:vosk`), Jackson, OpenPDF/PDFBox, JUnit 5 + AssertJ + Mockito, TestFX for UI smoke tests (headless via Monocle). Spotless + Checkstyle in CI. A `jpackage` installer on release.

## 5. Evaluation (generated)

- 5 synthetic "lecture" WAVs generated with a local TTS from known scripts (committed; small).
- WER of Vosk small vs large model (the large one is optional; download script provided).
- A note-quality rubric (coverage of the script's key points: automatic by keyword recall) for RuleBased vs Gemini; Gemini is marked "not measured" if there's no key in the cloud instance.
- Real-time factor (processing time / audio time) on the cloud CPU.

## 6. Milestones

1. Gradle scaffold, CI, model download script.
2. File-mode transcription with Vosk + tests on the WAV fixtures.
3. TranscriptStore + RuleBasedFormatter (the most unit tests).
4. JavaFX UI (transcript + notes) with a TestFX smoke test.
5. Mic mode (with a device-less test via a fake AudioSource).
6. GeminiFormatter + QAService behind interfaces with mocked HTTP tests; caps.
7. Export MD/PDF.
8. Eval, README, `jpackage` artifacts on the release, tag v2.0.0.

## 7. Cloud-instance constraints

No mic or display: file mode + a fake audio source + headless TestFX. The Gemini parts are tested with mocked HTTP unless a key is provided.

## 8. Definition of Done

- [ ] `./gradlew run --args="--file demo.wav"` produces notes fully offline
- [ ] RuleBasedFormatter + Vosk wrapper tests (≥50)
- [ ] Gemini features optional, capped and mocked in CI
- [ ] Eval numbers generated (WER, real-time factor, keyword recall)
- [ ] README states exactly which parts are offline; CI green; tag v2.0.0
