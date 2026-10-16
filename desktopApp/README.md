# Headroom for macOS

The desktop app runs on the JVM with Compose Multiplatform and Nucleus. It shares
quota fetching, sign-in, Room storage, settings, history and reset operations with
the Android and iOS apps. Its macOS shell stays running in the menu bar when the
window closes; use **Quit Headroom** to stop background refresh.

The macOS baseline is Ventura 13 or later, including the `SMAppService` login-item
API. The packaged application includes its Java runtime.

## Shared presentation

Android and macOS render the same original `HeadroomApp` in `:core:ui`, backed by
the same four original ViewModels. Typography, indicators, palettes, motion and
shader source come from the original `:core:designsystem`; there is no separate
desktop implementation of the dashboard, settings, charts or sign-in screens.

`DesktopPresentation` owns the window-independent ViewModel store and assembles
`HeadroomUiGraph` over `Headroom.data`. It does not open another database or
convert Swift-friendly presentation models back into domain objects. The shell
provides native browser/clipboard callbacks, lifecycle/back hosting, navigation
requests and the macOS launch-at-login settings row. Android-only widget, tile
and reset-island controls are capability-gated.

The shared layout responds to the window's logical width, not its Retina pixel
count. Below 600dp it uses the mobile single-column list and floating toolbar;
from 600 to 839dp it uses a navigation rail and two columns of account cards;
at 840dp and wider it shows list and detail side by side. A selected account
stays selected when the window crosses these breakpoints.

## Local development

Host tools are pinned in the gitignored `mise.local.toml` at the repository root.
The Gradle wrapper remains the source of truth for Gradle.
Use Xcode Command Line Tools for macOS packaging. The root build also configures
Android projects, so retain the Android SDK setup described in the root README
(`ANDROID_HOME`, platform 37 and build tools 37).

The desktop stack is pinned to Nucleus `3.0.0-dev-202610132318`, Compose
Multiplatform `1.12.1` and Kotlin `2.4.20`. JDK 21 is sufficient; neither GraalVM
nor JDK 25 AOT caching is enabled. These are published timestamped artifacts from
Maven Central, not a changing snapshot repository.

```sh
mise trust
mise install
mise exec -- ./gradlew :desktopApp:run
mise exec -- ./gradlew :desktopApp:check
```

For a fresh checkout, create the local toolchain file with:

```sh
mise use --local --pin core:java@temurin-21.0.11+10.0.LTS core:python@3.14.8 core:node@24.22.0
```

## Packaging

Nucleus uses its pinned electron-builder tool to make the installer. Node is a
build-time tool only; the application itself is JVM/Compose, not Electron.

```sh
mise exec -- ./gradlew :desktopApp:createDistributable
mise exec -- ./gradlew :desktopApp:runDistributable
mise exec -- ./gradlew :desktopApp:packageDmg
```

Use the packaged `.app` to check native notification permission and delivery.
macOS notifications need a real application bundle and are unavailable during a
plain Gradle `run`. Local packages are unsigned unless signing is explicitly
configured; distributing them to other machines requires signing and notarization.

The local smoke test checks bundle metadata and starts an isolated native demo
scene. It uses temporary storage and fake secrets, does not register a login item
or request notification permission, and exits automatically:

```sh
mise exec -- python3 desktopApp/scripts/smoke-test.py
```

To exercise native resizing in the same isolated demo window:

```sh
mise exec -- ./gradlew :desktopApp:run --args='--smoke-test --resize-sweep'
```

This checks native resize callbacks, root draw sizes and adaptive width updates
at the layout breakpoints, followed by a continuous resize sweep. Reported
latencies end at Compose drawing, not Metal presentation; programmatic resizing
does not cover every AppKit mouse-drag condition.

The native status item is supplied by ComposeNativeTray. Nucleus's `menu-macos`
module controls an application's main menu and is not an `NSStatusItem` adapter.
The dashboard uses Tao-backed windows, without Swing/AWT browser or tray APIs.

## Tests

Desktop tests render Compose into offscreen Skia scenes. They use temporary
storage and fake credentials, browser, clipboard and login-item callbacks. The
macOS-only CoreFoundation bridge test calls the real framework but never invokes
Security or accesses the Keychain.

To record the demo scenes while running the integration tests:

```sh
HEADROOM_RECORD_SCREENSHOTS=true mise exec -- ./gradlew :desktopApp:test \
  --tests '*DesktopAppIntegrationTest' --rerun
```

Images are written to `desktopApp/build/reports/desktop-screenshots/`; they are
rendered test scenes, not screenshots of the user's desktop. Provider sign-in,
notification permission/delivery and login-item registration also need manual
testing with the packaged app.

`SharedUiArchitectureTest` guards the common source boundary, verifies that both
shells render the original application, and rejects a new desktop screen fork.
The integration tests also resize one existing composition across the shared
layout breakpoints, including resizing with an account detail open.

## Data and credentials

The database and preferences live in `~/Library/Application Support/Headroom`.
Credentials are separate entries in the macOS Keychain, never in those files or
the application's logs. Unit tests use temporary directories and fake Keychain
backends rather than accessing a user's accounts.

The resident process owns refreshes, sign-in and notification checks. Closing a
window does not start another worker process. Reset notifications require fresh
provider data confirming a reset, rather than trusting the predicted reset time.

## Artwork

The application and template status-item icons use the same quota-ring mark as
the Android launcher. Regenerate the checked-in PNG and ICNS assets with:

```sh
python3 desktopApp/scripts/generate-icons.py
```
