# BCDebugJavaAgent

Internally | BlockConnect uses a Minecraft‑debug‑level JavaAgent, which is suitable for bytecode‑level analysis and log retention for Minecraft in environments where AprismJDK is not used or unavailable.

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
├── bc-hooks-26/             # MC 26.x specific hooks (26.2, 26.1.2)
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
# With Minecraft (via mdl — needs -Xbootclasspath/a to avoid duplicate class loading)
java -Xbootclasspath/a:bcdebug-javaagent.jar -javaagent:bcdebug-javaagent.jar=logLevel=DEBUG -jar minecraft.jar

# Standalone test
java -Xbootclasspath/a:bcdebug-javaagent.jar -javaagent:bcdebug-javaagent.jar=logLevel=DEBUG,classfilters=dev.blockconnect.bcagent.test -cp test-classes dev.blockconnect.bcagent.test.TestTarget
```

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
| `exportOnShutdown` | `true` | Export JSONL logs on JVM exit |
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

| MC Range | JDK | Hook Profile |
|---|---|---|
| 26.1–26.2 | 21+ | `26` |
| 1.21.x | 21 | `1.21` (planned) |
| 1.20.x | 17 | `1.20` (planned) |
| 1.12.2 | 8 | `1.12` (planned) |

## License

MIT License — Copyright (c) 2026 BlockConnect
