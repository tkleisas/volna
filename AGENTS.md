# Volna development notes

## Test-driven development

This project follows test-driven development. For every behavior change, bug fix,
mapping change, or state/persistence change:

1. Write a focused failing test that expresses the requested behavior.
2. Run the targeted test and confirm it fails for the expected reason.
3. Implement the smallest change that makes it pass.
4. Run the targeted test again, then the full unit-test suite, lint, and build.
5. Add the regression test to the commit. Do not leave placeholder tests.

When a behavior lives behind Android UI or native code, put the logic in a pure
Java/Kotlin seam that can be tested on the JVM. If that is not practical, add the
closest regression test and record the remaining UI/native manual check. Do not
use "tested manually" as the only verification for new logic.

## Commands

Targeted unit test:

```sh
./gradlew :app:testDebugUnitTest --tests "org.bokontep.wavesynth.ClassTest"
```

Native YAWN DSP tests:

```sh
./native-tests/run.sh
```

Full local verification before committing:

```sh
./native-tests/run.sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Release verification (tags only):

```sh
./scripts/check_16kb_page_size.sh path/to/Volna.apk path/to/Volna.aab
```

CI runs the unit tests, lint, debug build, and on `v*` tags the signed APK/AAB
release plus 16 KB alignment check.

## Current test coverage

JVM tests cover wavetable parsing, microtonal pitch splitting and cell
boundaries, touch expression, touch/relative modulation math, RTP-MIDI packets,
the spectrum/FFT analyzer, Android preferences/wavetable persistence, custom
view state, main-layout inflation, modulation-matrix source/target interaction,
scale definitions/storage, and configurable register layouts. Robolectric is
used for JVM Android UI tests with a fake engine for matrix tests.

Native C++ tests cover MIDI 1.0 conversion, MIDI pitch transpose/flush,
scale quantization, pitch-modulation note tracking, envelopes, and the YAWN
Wavetable, Subtractive, and Karplus-Strong engines using the custom
`native-tests/run.sh` harness.

CI runs native tests, JVM/Robolectric tests, lint, and the debug build; tags
also build the signed APK/AAB and verify 16 KB alignment. Do not add
example/placeholder tests.
