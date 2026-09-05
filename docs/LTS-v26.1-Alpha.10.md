# v26.1 Alpha.10 LTS Candidate Validation

Final LTS-candidate validation against MC 1.21.1 dedicated server (stock
obfuscated jar), with `mappingsAuto=true` and `httpToken` enabled.

## Build Gate

- `gradlew fatJar test`: **56 tests green**.
- Agent bytecode target: Java 8 (major 52), established in Alpha.1 and
regressed through the line.

## Runtime Gate

| Check | Result |
|---|---|
| Agent init | `v26.1-Alpha.10`, initialized, token enabled |
| Mapping/hooks | Provider hooks active; runtime hook raises total hooks to 9 |
| Runtime hook | `RuntimeHook aqu#a args=1` fired at server tick rate |
| Late hook reload | `retransformed:1, failed:0` once target class loaded |
| Token enforcement | Missing token = 401 |
| Export | `POST /export` success |

The initial short battery ran before world initialization; its runtime hook
was retained by the registry and injected automatically when `aqu` loaded.
The late observation above confirms the full lifecycle, not merely the
request response.

## Verdict

**PASS.** Alpha.10 is the v26.1 LTS candidate. Promote to v26.1 GA only after
