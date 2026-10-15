# Termix — Native Android Terminal Emulator

Termix is a lightweight, modular native Android terminal emulator engineered for speed, robust process management, and broad Android compatibility (from API 18 through API 34+).

## Key Architecture & Features

### 1. Persistent Environment Configuration
Termix initializes and preserves exact application environment variables:
- `HOME=/data/data/com.csoftware.termix/files/home`
- `ENV=/data/data/com.csoftware.termix/env/home`
- `USER=/data/user/0/com.csoftware.termix/files/home`
- `PATH=/system/bin:/xbin:/system/xbin:/sbin:/system_ext/bin:/bin`

On first launch:
- Creates required application-private directories (`files/home` and `env`).
- Creates the `ENV` startup configuration file at `/data/data/com.csoftware.termix/env/home` as a file (not a directory).
- Automatically sources this startup configuration file whenever a new interactive shell session starts.
- Environment modifications made via settings or shell persist across application restarts.

### 2. Search PATH Resolution
- Preserves the search order:
  1. `/system/bin`
  2. `/xbin`
  3. `/system/xbin`
  4. `/sbin`
  5. `/system_ext/bin`
  6. `/bin`
- Non-destructive PATH verification: checks directory existence prior to searching without deleting missing directories from user configuration.
- Informative error messaging when a command or binary is missing or non-executable.

### 3. Interactive Terminal Engine
- Real interactive shell process execution via `ProcessBuilder` with continuous stdin/stdout streams.
- Interactive support for shell commands: `cd`, `pwd`, `export`, `echo`, `env`, `which`, `clear`, and `exit`.
- Sends `SIGINT` (Ctrl+C, ASCII `0x03`) to terminate running child commands.
- Concurrent multi-session management (Tab 1, Tab 2, etc.) without leaking or sharing mutable shell state.
- ANSI escape code parser rendering color text in dark charcoal (`#121517`) and terminal green (`#00E676`).

### 4. Non-Destructive Root Detection & Conditional Request
- **Step A (Detection)**: Checks known `su` locations and PATH binaries non-destructively without escalating privileges.
- **Step B (Conditional Request)**: If root is detected on device:
  - Displays a native dialog explaining that root is strictly optional.
  - User can choose **Grant Root** or **Continue Without Root**.
  - If granted, verifies root identity (`id -u == 0`) and opens an explicitly labeled `# ROOT` session.
  - If denied or unavailable, runs in standard `$ NORMAL` mode without nagging.
- **Step C (Unrooted Device)**: Never calls `su`, never prompts for root, and runs standard unprivileged shell seamlessly.

## Build and Setup Instructions

### Prerequisites
- JDK 11 or higher
- Android SDK (API 34)
- Gradle 8.x

### Build Commands
To build the debug APK:
```bash
gradle :app:assembleDebug
```

To run unit tests:
```bash
gradle :app:testDebugUnitTest
```

The compiled APK is available directly at the root of the project:
- `/termix-debug.apk`
- `/app-debug.apk`

Also available at standard Gradle output path:
- `app/build/outputs/apk/debug/app-debug.apk`
