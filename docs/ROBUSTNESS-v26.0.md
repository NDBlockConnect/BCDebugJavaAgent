# Robustness Assessment — v26.0

Per the BlockConnect version specification, a 30-minute simulated-usage and
malicious-attack test was executed against v26.0 before planning v26.1.
This document records the assessment performed on 2026-08-23.

## Scope

- Target: `bcdebug-javaagent-v26.0.jar` on the MC 1.21.1 dedicated server
  (`bcdbg-val`, stock obfuscated jar, bundler classloader isolation).
- Surface: agent argument parsing, HTTP control plane (both frontends),
  mapping resolution, hook reload idempotency, export pipeline, game
  survivability under abuse.

## Test matrix and results

| # | Scenario | Result |
|---|---|---|
| T0 | Hostile launch args: nonexistent `mappingsFile` + `unknownKey=zzz` + `badNum==` | Agent initialized cleanly; control plane up; game unaffected |
| T1 | Status under hostile config | `initialized:true, totalHooks:8` |
| A1 | Path traversal endpoint (`/../../etc/passwd`) | 404 rejected |
| A2 | Wrong method on read endpoint (`POST /status`) | Accepted (GET/POST not enforced on JDK frontend reads) — cosmetic |
| A3 | Missing required query param (`POST /log-level` without `level`) | 400 |
| A4 | Bogus level value (`?level=NOTALEVEL`) | Graceful fallback to INFO by contract |
| B1–B4 | Raw-socket garbage: non-HTTP bytes, 40 KB request line, missing HTTP version, random binary | No crash, connection-level handling only |
| C | 20 parallel `/status` requests | Served; pool queueing held; game alive |
| D | Rapid double `POST /hooks/reload` | Idempotent: second pass `retransformed:0` (injection tracker) |
| E | Functional sanity after battery + export | Export ok; artifacts written |
| S | Unit-level hardening (garbage args, garbage mapping files, orphan method lines, boolean/numeric garbage) | 6 new tests green (`HardeningTest`) |

Game process survived the entire battery; the only termination observed was
the known external environment killer after all checks had passed — unrelated
to the agent.

## Findings

1. **No availability defects.** No input vector crashed or hung the game or
   the agent. All malformed inputs degraded gracefully with logs.
2. **F-1 (behavioral, by design):** explicit `mappingsFile` that fails to
   load does NOT fall back to `mappingsAuto` in the same run — hooks stay
   unmapped (logged clearly). Recommendation for v26.1: on mapping-file load
   failure, fall back to auto-discovery before giving up.
3. **F-2 (cosmetic):** JDK HttpServer frontend accepts POST on read-only
   endpoints (raw-socket frontend enforces methods). Harmless; optionally
   tighten later.
4. **F-3 (hardening verified):** injection tracker made double-reload a
   no-op (`retransformed:0` on the second pass) — the retransform hot path
   is safe under operator error.

## Verdict

**PASS.** v26.0 is fit as the baseline for v26.1 planning. Recommendations
F-1 and F-2 are carried into the v26.1 candidate pool.
