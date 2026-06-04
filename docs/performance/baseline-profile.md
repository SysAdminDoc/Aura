# Baseline Profile and Macrobenchmark

Aura keeps Baseline Profile generation manual so normal debug, lint, and release builds do not start connected-device benchmark work.

## Device Requirements

- Use a physical Android device for startup and frame metrics.
- Use Android 13 or newer for non-root Baseline Profile generation; older devices need root.
- Keep the device charged and thermally stable before comparing profile/no-profile results.
- Do not treat emulator numbers as release evidence.

## Critical User Journeys

The profile generator exercises the public app UI:

- Startup into Wallpapers.
- Wallpapers grid scroll.
- Wallpaper Detail open/back.
- Videos grid scroll.
- Sounds list scroll.
- Favorites list scroll.

## Local Runbook

Connect one physical device, then run:

```powershell
adb devices -l
.\gradlew.bat :app:generateBaselineProfile -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
.\gradlew.bat :baselineprofile:connectedBenchmarkReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark
```

Expected artifacts:

- Generated profile: `app/src/main/generated/baselineProfiles/`
- Benchmark JSON and Perfetto traces: `baselineprofile/build/outputs/connected_android_test_additional_output/`
- Instrumentation reports: `baselineprofile/build/reports/`

## CI

`.github/workflows/performance.yml` is manual and targets a Linux self-hosted runner labeled `self-hosted`, `linux`, `android`, and `physical-device`. GitHub-hosted runners are intentionally not used because they do not provide representative physical-device startup or frame timing.

Attach the uploaded `aura-performance-*` artifact to release notes when it contains both:

- `StartupBenchmark` results for `CompilationMode.None()` and `CompilationMode.Partial(BaselineProfileMode.Require)`.
- `GridScrollBenchmark` frame metrics for Wallpapers, Videos, Sounds, and Favorites.
