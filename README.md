# BCDebugJavaAgent

Internally | BlockConnect uses a Minecraft‑debug‑level JavaAgent, which is suitable for bytecode‑level analysis and log retention for Minecraft in environments where AprismJDK is not used or unavailable.

**Status: v26.0 GA**

| Version | Theme |
|---|---|
| Alpha 1–2 | Core agent: ASM instrumentation, hook registry, exports |
| Alpha 3 | Multi-version profiles (1.20/1.21), plain `-javaagent` fix |
| Alpha 4 | Self-bootstrapping shim, dual-mode control plane |
| Alpha 5 | ProGuard mappings — class+method translation |
| Alpha 6 | Automatic mapping discovery (piston-meta + cache) |
| Alpha 7 | Readable exports (reverse translation) |
| Alpha 8 | Runtime operations (hot hook reload via retransform) |
| Alpha 9 | Client-side diagnosis hooks (setScreen / pauseGame) |
| Alpha 10 | Dynamic statistics filters (**LTS**) |
| **v26.0** | **GA — production release of the full line** |

## Overview

BCDebugJavaAgent is a JVM-level Java Agent for Minecraft Java Edition (1.12.2–26.2) that performs:

- **Bytecode analysis & recording** — instruments methods at class-load time using ASM, records entry/exit counts and timing
- **JVM-level hook debugging** — injects hooks into MC lifecycle methods (tick, render, world load) via SPI-discovered providers
- **Log behavior export** — exports JSONL logs and method statistics on JVM shutdown

## Project Structure

```
BCDebugJavaAgent/
├── bc-agent-core/          # Core: ASM bytecode analysis, hook registry, logging, export
├── bc-agent/               # Agent: premain/agentmain entry, config, HTTP control server
├── bc-hooks-26/            # MC 26.x specific hooks (26.2, 26.1.2)
├── bc-hooks-legacy/        # MC legacy hooks (1.20.x, 1.21.x, Mojang mappings)
├── build.gradle             # Root build + fatJar task
├── gradle.properties        # Version config
└── settings.gradle          # Multi-project settings
```

## Build

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
.\gradlew.bat fatJar
```

Output: `build/libs/bcdebug-javaagent-v26.0-Alpha.1.jar`

## Usage

```powershell
# Minecraft client or dedicated server — a bare -javaagent is sufficient.
# The shim self-appends its embedded jar to the bootstrap classpath at runtime,
# so no -Xbootclasspath/a is required in any launch mode.
java -javaagent:bcdebug-javaagent.jar=logLevel=DEBUG,hookProfile=auto -jar minecraft.jar

# Obfuscated legacy jar (1.20.x / 1.21.x stock server/client): either point
# mappingsFile at Mojang's published ProGuard txt, or let the agent resolve
# and cache mappings automatically (mappingsAuto=true).
java -javaagent:bcdebug-javaagent.jar=logLevel=DEBUG,hookProfile=1.21,mappingsAuto=true,logFile=true -jar server.jar nogui

# Standalone test
java -javaagent:bcdebug-javaagent.jar=logLevel=DEBUG,classfilters=dev.blockconnect.bcagent.test -cp test-classes dev.blockconnect.bcagent.test.TestTarget
```

### MDL workflow

MDL (v26.2.0+) can register the agent persistently per instance:

```powershell
mdl javaagent install <instance> bcdebug-javaagent.jar --params "hookProfile=auto,logFile=true"
mdl javaagent list <instance>
```

### Control plane transports

With `enableHttp=true` the agent prefers the JDK `com.sun.net.httpserver`
frontend. In environments where that module is not linkable from bootstrap
code (notably bundler-based dedicated servers), it automatically degrades to a
raw-socket implementation serving the same endpoints.

## Known Limitations

- **Legacy-version name obfuscation:** stock Mojang jars for 1.20.x/1.21.x ship
  with obfuscated class and method names (`aqu.a` style); only entry points
  keep real names. Provide `mappingsFile=<client.txt|server.txt>` so hook
  targets written in Mojang names are translated to runtime names — classes
  and methods, with JVM descriptors disambiguating overloads. MC 26.x ships
  unobfuscated and needs no mappings.
- Method statistics report deobfuscated class and method names when mappings
  are active; unresolved methods keep their runtime names, and JVM descriptors
  always contain runtime references. Hook target classes are auto-added as
  dynamic statistics filters on translation; broader game-code coverage on
  legacy jars still needs manual `classfilters` prefixes.
- `1.12` profile remains planned (requires legacy JDK 8 artifact).

### Configuration Keys

| Key | Default | Description |
|---|---|---|
| `outputDir` | `bcdebug-output` | Output directory for logs and exports |
| `logLevel` | `INFO` | TRACE, DEBUG, INFO, WARN, ERROR |
| `classFilters` | `net.minecraft.;com.mojang.` | Semicolon-separated class name prefixes to match |
| `excludeFilters` | `net.minecraft.client.main.Main` | Semicolon-separated class name prefixes to exclude |
| `logMethodEntry` | `true` | Log method entries |
| `logMethodExit` | `false` | Log method exits |
| `enableHooks` | `true` | Enable MC-specific hook injection |
| `hookProfile` | `auto` | Hook set: `26`, `1.21`, `1.20`, `1.12`, `auto` |
| `mappingsFile` | — | ProGuard mapping txt (Mojang `client.txt`/`server.txt`); translates hook targets to runtime names for obfuscated legacy jars || `exportOnShutdown` | `true` | Export JSONL logs on JVM exit |
| `enableHttp` | `false` | Enable HTTP control server |
| `httpPort` | `25595` | HTTP server port |

### HTTP API (when `enableHttp=true`)

| Endpoint | Method | Description |
|---|---|---|
| `/status` | GET | Agent status and record counts |
| `/methods` | GET | Method statistics summary |
| `/logs` | GET | Recent log entries (last 100) |
| `/export` | POST | Trigger immediate export to disk |

## Supported MC Versions

| MC Range | Game JVM | Hook Profile | Status |
|---|---|---|---|
| 26.1–26.2 | 21+ | `26` | Available |
| 1.21.x | 21 | `1.21` | Available |
| 1.20.x | 17 | `1.20` | Available |
| 1.12.2 | 8 | `1.12` | Planned (requires legacy JDK 8 artifact) |

### Profile auto-detection
With `hookProfile=auto` (default), the agent resolves the active profile at
startup:

1. System property `bcdebug.mcversion=<ver>` (explicit override)
2. System property `minecraft.version`
3. `version.json` in the game root (official launcher layout)
4. First `versions/*/version.json` under the game root

When detection fails, the default provider set (MC 26.x) is activated.
The agent bytecode targets release 17, so the same fatJar loads on JDK 17+
game JVMs.

## License

MIT License — Copyright (c) 2026 BlockConnect
