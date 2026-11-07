# Android CHANGELOG

## 0.3.1 / code4 — active mode and input diagnostics

- Teal selected/activated mode button with ● and explicit current software-mode text, rendered from accepted worker snapshots (apart from default EASY startup initialization).
- Show actual incoming pitch/velocity and rejection reason for unsupported channels/out-of-layout notes, rather than retaining stale hit text; local NOTE_ON diagnostics after submission attempts are not sound-success acknowledgments.
- Preserve all routing tables, native code, UiPublisher, lighting and permissions. User confirmed DRUM correspondence restored by manually correcting Octave; no automatic remap/configuration write.
- ModeFeedback2140 checks, independent384 valid MIDI routing-equivalence checks and full parent regression/build passed. Fresh sol review NO BLOCKING FINDINGS. Original-key in-place update verified against actual installed0.3 certificate; device0.3.1 and all three active mode views checked.
- Physical long-run/timbre/sustain/RGB acceptance remains scoped separately. Review: ../docs/REVIEW_ANDROID_V031.md.

## 0.3 / code3 — offline instruments prototype

- Add pinned MIT TinySoundFont and MIT FluidR3 GM sampled acoustic piano/percussion, complete offline notices and checked-source manifest.
- Add arm64 API26 AAudio JNI adapter, callback-owned preallocated synth, bounded generation-safe command queue, finite output limiting, asynchronous load/close and stale-loader disposal.
- Add EASY/DRUM/KEY layouts, real GM mapping, note-owner coalescing, sustain ownership, panic/lifecycle handling and manual-mode-intent protection.
- Replace misleading measured EASY velocity100 with fixed/no-data UI; ordinary MIDI Notes retain N/127.
- Keep original EASY WAV/SoundPool and audited RGB boundary; only small mode gate added to MidiLighting. Subsequently integrated only sibling Diffusion.java and DiamondTest.java, byte-identical to Terra source; existing LightingTest remains untouched. Build explicitly executes the 76477-check diamond regression, and the old-circle negative control is rejected.
- Add real-font host/sanitizer and pure routing/ownership/controller tests; all unchanged suites pass. Audio-output-route errors fail closed with relaunch instruction; no automatic stream hot-recovery.
- Corrected source passed fresh independent re-review; parent rebuilt, sanitized and packaged with original signing identity, verified actual installed0.2 certificate, and installed0.3 in place. Device native readiness and three layouts/screen auditions checked; physical timbre/velocity/diamond/lifecycle acceptance still pending. Lane key was never copied.

0.1/0.2 history and user feedback are preserved verbatim in README.md below the 0.3 section.

## 0.3 corrective pass after independent merge block

- Guard inactive TSF slots by playingPreset before inspecting channel/key; pinned vendor/header/asset hashes untouched.
- Exercise first-note/retrigger/post-panic cases and test-only ASan inactive-field poisoning around the production scan; removing the guard is rejected by a negative control. ASan/UBSan are not general uninitialized-memory detectors; installed host MSan unsupported and Valgrind absent.
- Replace MainActivity atomic pending flag with production UiPublisher: actual applied-mode generation, dirty revisions across capture/delivery, and acknowledgement-driven latest-state drain. Includes auto EASY in the same manual generation.
- Add64 deterministic production publisher checks and two scheduling negative controls; all previous assertions retained. Full offline build, sanitizer suite and package audit rerun successfully.
- A fresh sol review cleared the two P1 blockers for source integration. Parent integration/build/install is recorded in ../docs/REVIEW_ANDROID_V03.md and ../tests/reports/android-v03-*. This does not accept physical audio/RGB performance.
