# VIRTUAL ANDROID — CANONICAL MASTER EXECUTION PLAN

**Plan ID:** VA-MASTER-PLAN  
**Version:** 1.0.2  
**Canonical date:** 2026-09-03  
**Canonical repository:** `tigpetryan-rgb/Virtual-Android`  
**Default branch:** `main`

> **MANDATORY:** Every new chat/agent/session MUST read `AGENTS.md`, this plan, and `PROJECT_STATE.json` before changing code, docs, CI, issues, branches, or release state. No session may silently redefine the product, architecture, dependency order, or completion criteria.

## 1. Authority order

When sources disagree:

1. explicit new user instruction intentionally changing the plan;
2. this file;
3. `PROJECT_STATE.json` backed by live GitHub/Drive evidence;
4. canonical GitHub default branch + passing CI;
5. archived Drive RESULT artifacts;
6. task-specific handoffs;
7. older chat summaries/experiments.

A RESULT ZIP proves an artifact exists; it does **not** prove canonical integration or final acceptance. A status change requires evidence.

## 2. Immutable product objective

Build a universal-as-practical ARM64 Android APK that runs an isolated secondary AOSP-based Android environment on a normal Android phone:

- no root requirement;
- no bootloader unlock requirement;
- no permanent host-OS modification;
- separate guest CPU/RAM/storage/network boundaries;
- hardware virtualization acceleration where safely available;
- mandatory software-emulation fallback where acceleration is unavailable;
- later privileged AI system agent inside the guest boundary, not as an unrestricted host-privileged agent.

“Universal” may only be claimed after the device matrix and fallback gates pass.

## 3. Non-negotiable architecture

### Host/guest isolation
The host APK orchestrates the runtime. Guest state stays private except through explicitly mediated import/export. Root, bootloader unlock, patched host kernel, or host-system replacement cannot be the normal production path.

### Backend
QEMU/software emulation remains the compatibility baseline until a better portable fallback is proven. AVF/pKVM is capability-gated acceleration and must never remove fallback availability.

### Storage
Verified/base guest images are immutable after ingestion. Every VM gets independent writable state. Snapshot/restore/clone/reset operate on writable state. Metadata/state updates must be crash-safe; low-space/quota failures must fail safely.

### Display/input
Display transport is private by default. Input is deterministic, bounded, validated, and tied to the intended guest/session. No unauthenticated external control channel is acceptable.

### Network
Default stance is minimum exposure. Guest internet/DNS/LAN/forwarding behavior is explicit policy, never accidental reachability.

### Host↔guest RPC
RPC must be authenticated, authorized, schema-validated, bounded, session-aware/replay-aware where relevant, and least-privilege. No production debug bypass or committed secret.

### AI agent
The privileged agent lives inside the guest boundary. Tools/actions are explicitly scoped and auditable. Host interaction goes through the secure RPC/lifecycle model.

### Lifecycle/recovery
VM start/stop/restart are explicit state transitions. Clean shutdown is preferred; forced termination is detected as dirty/recovery-required state. App process death, app restart, device reboot, and interrupted storage operations require defined recovery behavior.

### Reproducibility/security
Clean checkout builds/tests must be reproducible with documented/pinned toolchain inputs. High/critical threat-model findings block release unless closed, mitigated, or explicitly accepted by the user with rationale.

## 4. Mandatory status vocabulary

- `RESULT_ARCHIVED` — workstream RESULT artifact exists in Drive.
- `INTEGRATED` — merged into canonical repo and survives canonical tests/build.
- `DEVICE_ACCEPTED` — passed defined physical ARM64 Android acceptance.
- `BLOCKED` — dependency/capability/decision prevents progress.
- `SUPERSEDED` — retained as history, not active implementation.
- `FAILED` — acceptance gate failed.
- `DONE` — allowed only when all required integration + acceptance gates for that milestone pass.

**Never translate `RESULT_ARCHIVED` into `DONE`.**

## 5. Current evidence

All 13 parallel workstreams have archived RESULT artifacts. CHAT-12 has additionally been integrated into the canonical baseline with live CI evidence:

| ID | Workstream | Artifact | Integration |
|---|---|---|---|
| CHAT-01 | P1 device acceptance | `RESULT_ARCHIVED` | NEXT |
| CHAT-02 | AOSP/framework | `RESULT_ARCHIVED` | PENDING |
| CHAT-03 | display hardening | `RESULT_ARCHIVED` | PENDING |
| CHAT-04 | deterministic input | `RESULT_ARCHIVED` | PENDING |
| CHAT-05 | AI agent | `RESULT_ARCHIVED` | PENDING |
| CHAT-06 | secure RPC | `RESULT_ARCHIVED` | PENDING |
| CHAT-07 | storage/snapshots | `RESULT_ARCHIVED` | PENDING |
| CHAT-08 | network isolation | `RESULT_ARCHIVED` | PENDING |
| CHAT-09 | performance | `RESULT_ARCHIVED` | PENDING |
| CHAT-10 | AVF/pKVM backend | `RESULT_ARCHIVED` | PENDING |
| CHAT-11 | lifecycle hardening | `RESULT_ARCHIVED` | PENDING |
| CHAT-12 | CI reproducibility | `RESULT_ARCHIVED` | INTEGRATED |
| CHAT-13 | threat model | `RESULT_ARCHIVED` | PENDING |

The project is **not release-complete**.

## 6. Strict gate order

### G0 — Governance and canonical repository — **PASS**
Evidence:
- repo: `tigpetryan-rgb/Virtual-Android`;
- default branch: `main`;
- repo was empty at initial inventory, so no contradictory legacy code/docs required deletion;
- initialization commit: `fe9b4d20669a638890bf5d7144ef1ef0f543324b`;
- canonical governance files installed on root.

### G1 — CHAT-12 CI reproducibility — **PASS**
The canonical baseline was reconstructed from the archived CHAT-12 source using integrity repair evidence rather than restarting staging.

Evidence:
- recovered source ZIP SHA-256: `fdf40b01bc1dd3cc68b39fa118aab9b312683756126df63c25b71bce15d086a1`;
- integrity diagnostic run `33672125749`: 109 ZIP entries, `testzip=None`, exact target SHA-256;
- canonical source materialization commit: `d3021d5aa77c539ab756be34665a2b0c7f14f11b`;
- source/reproducibility workflow run `33709407052` succeeded after toolchain-stable initramfs packaging checks and deterministic metadata validation;
- Android compile run `33709904931` on commit `df64f7dbe45cfb7074cc0cc2797a9d010cb790e4` succeeded through debug APK assembly, JNI ELF 16 KiB verification, QEMU dependency/hash closure, and artifact upload;
- temporary staging/repair/bootstrap workflows were removed from the canonical tree.

**PASS:** clean-checkout canonical source checks and Android build path have real CI evidence. This G1 promotion is merged only if `source-checks` also passes on the exact finalization tree.

### G2 — CHAT-01 P1/device capability — **NEXT**
Integrate capability detection, no-root/no-bootloader normal path, fallback-vs-acceleration matrix, clear unsupported states.

**PASS:** physical ARM64 device baseline acceptance evidence exists.

### G3 — CHAT-02 AOSP/framework
Integrate verified guest bundle ingestion, guest boot lifecycle, stable framework/UI boot target, diagnostics.

**PASS:** canonical runtime boots guest to the defined stable state.

### G4 — CHAT-03 display → CHAT-04 input
Integrate private display transport, deterministic input mapping, orientation/size/session handling, no cross-session leakage.

**PASS:** display and touch/key acceptance pass together.

### G5 — CHAT-07 storage/snapshots
Integrate immutable base, writable per-instance state, crash-safe metadata, snapshot/restore, clone/reset, export/import, quota guards, dirty recovery.

**PASS:** persistence + forced-stop/recovery acceptance passes.

### G6 — CHAT-08 network isolation
Integrate explicit policy, minimum exposure, no accidental host/LAN exposure.

**PASS:** only intended reachability is demonstrated.

### G7 — CHAT-06 secure host↔guest RPC
Integrate authn/authz, schemas, bounds, session/replay handling, no debug bypass.

**PASS:** positive and negative security tests pass.

### G8 — CHAT-11 lifecycle hardening
Integrate explicit VM/app transitions, clean shutdown, dirty detection, process-death/reboot recovery.

**PASS:** repeated lifecycle/recovery scenarios pass.

### G9 — CHAT-05 AI agent
Only after G7+G8. Agent stays guest-bound, scoped, auditable, and mediated.

**PASS:** allowed actions succeed; denied actions stay denied.

### G10 — CHAT-10 AVF/pKVM
Only after fallback baseline is stable. Add capability-gated acceleration without breaking fallback.

**PASS:** supported devices accelerate; unsupported devices retain accepted fallback.

### G11 — CHAT-09 performance
Add startup/boot/CPU/RAM/storage/network instrumentation and regression budgets without weakening correctness/security.

**PASS:** budgets have evidence and regressions are visible.

### G12 — CHAT-13 threat-model closure
Refresh threat model against actual integrated code. Close or explicitly accept release-blocking findings.

**PASS:** no unaddressed release-blocking findings.

### G13 — Final end-to-end acceptance/release
Clean clone→build→install; physical ARM64 matrix; fallback; acceleration where available; boot/display/input/storage/network/RPC/lifecycle/AI; stress/recovery; signed artifact; checksums/dependency inventory.

**PASS:** only now may release/project milestone be `DONE`.

## 7. Integration rule for RESULT ZIPs

For each workstream:
1. record artifact name/hash/source;
2. inspect handoff/verification/changed-files/source or patch;
3. compare to current canonical tree;
4. resolve conflicts using this plan and gate order;
5. apply smallest coherent change;
6. run gate-specific + regression tests;
7. commit with gate/workstream ID;
8. update `PROJECT_STATE.json` only from evidence;
9. preserve original Drive RESULT ZIP unchanged.

Never combine all workstream trees by “last writer wins”.

## 8. GitHub cleanup rule

Change or remove active GitHub material that directly conflicts with this plan, including:
- root/bootloader unlock as required production architecture;
- host OS replacement/coupling as product direction;
- AVF-only design without fallback;
- mutation of verified immutable base images;
- unauthenticated public control/display/RPC defaults;
- unrestricted host-privileged AI agent;
- committed secrets/debug bypasses;
- stale claims that the whole project is complete without G13 evidence;
- duplicate contradictory canonical plans.

Prefer update/supersede when history is useful. Delete only obsolete/unsafe/duplicate material with a traceable commit. Never delete historical evidence merely to rewrite the story.

## 9. Mandatory protocol for every future session

Before project work:
1. read `AGENTS.md`;
2. read this plan;
3. read `PROJECT_STATE.json`;
4. inspect live default-branch/HEAD and CI;
5. inspect relevant Drive RESULT artifact;
6. identify the next unblocked gate;
7. work only inside that gate unless the user explicitly changes the plan;
8. run relevant tests;
9. update state only with evidence;
10. leave a handoff: changes, tests, failures, exact next step.

If a requested task conflicts with this plan, explain the conflict and follow the plan unless the user explicitly changes it. Explicit plan changes require updating this file first, bumping the version, recording rationale, then changing implementation.

## 10. Branch/commit discipline

Default branch is canonical truth after G0. Prefer focused branches/PRs for integration. Use gate/workstream in commit/PR titles, e.g. `G5/CHAT-07: integrate storage lifecycle`. CI failures block status promotion. Conflict resolution must follow this plan, not chat preference.

## 11. Current completed vs remaining

Completed:
- product/architecture direction defined;
- 13 RESULT workstreams archived;
- dedicated canonical GitHub repo established;
- G0 governance established;
- G1/CHAT-12 reproducible canonical baseline integrated and evidenced.

Remaining:
- G2–G12 ordered integration/acceptance;
- G13 final release acceptance.

**NEXT REQUIRED GATE: G2 / CHAT-01 — P1 physical-device acceptance.**

## 12. Revision record

- `1.0.0` — initial strict plan after 13 RESULT archives were preserved.
- `1.0.1` — G0 completed; canonical GitHub repository established; architecture and gate order unchanged; next gate advanced to G1.
- `1.0.2` — G1/CHAT-12 reproducible canonical baseline integrated with exact source-integrity, source-checks, and Android compile evidence; next gate advanced to G2/CHAT-01, without starting G2 work.

## 13. Plan lock

Future sessions must not restart from scratch, skip dependencies, weaken isolation/security, remove fallback for convenience, call archived results “done”, or modify unrelated repositories.

**The plan changes only when the user explicitly changes it.**
