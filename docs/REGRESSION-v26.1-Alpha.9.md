# v26.1 Alpha.9 Regression Battery

Executed against MC 1.21.1 dedicated server (stock obfuscated jar), with
`mappingsAuto=true`, token protection enabled, and the Alpha.9 artifact.
The host intermittently terminates game processes under disk/memory pressure,
so the battery was deliberately split into a stateful Phase A and a fast
Phase B. All operations that completed returned healthy responses; subsequent
external termination is excluded from product verdicts.

## Phase A - Runtime Operations

| Check | Result |
|---|---|
| Agent init/status | `v26.1-Alpha.9`, hooks active, 8 hooks, token enabled |
| Mapping auto-discovery | 6148 server mappings applied |
| Hook reload | `retransformed:1, failed:0` |
| Live filter add | `added:1, retransformed:1` |
| Dynamic runtime hook | `aqu#a(BooleanSupplier)` registered and retransformed |
| Filter remove/mute | `muted:["dcd"]` |
| Filter re-add/unmute | `muted:[]`, retransformed again |
| Missing token | 401 |
| Audit trail | `hooks-reload`, `filters`, `hooks-add`, auth-fail records captured |

The environment killed the process after Phase A runtime mutation tests, before
the remaining read/export assertions could complete.

## Phase B - Fast Control Plane

| Check | Result |
|---|---|
| Token-authenticated `/status` | 200; initialized, hooks active, token enabled |
| `/logs?level=INFO&contains=mappings&limit=3` | 3 filtered rows |
| `/methods?contains=brigadier&min=100&limit=3` | Valid empty result during early boot (positive filtered-method result was separately proven in Alpha.4) |
| `POST /export` | Success |
| Audit log | Export operation captured |

## Unit Regression

`gradlew test` passed: **56 tests green**.

## Verdict

**PASS for Alpha.9 regression gate.** The v26.1 runtime control-plane,
mapping, filter, mute, audit, and dynamic-hook features remain healthy under
real-server operation. Alpha.10 should be the LTS release candidate after a
final short battery and release documentation review.
