# AGENTS.md — VIRTUAL ANDROID MANDATORY ENTRYPOINT

This repository is governed by a strict canonical plan.

## READ FIRST — BEFORE ANY WORK

Every assistant/agent/session MUST, in this exact order:

1. Read `VIRTUAL_ANDROID_MASTER_PLAN.md`.
2. Read `PROJECT_STATE.json`.
3. Inspect the live default branch/HEAD and relevant CI state.
4. Work only on the next unblocked gate in the master plan unless the user explicitly changes the plan.

## Hard rules

- Do not redesign the product or architecture from chat memory.
- Do not equate a Drive RESULT ZIP with canonical integration or final completion.
- Do not skip gate dependencies.
- Do not weaken no-root/no-bootloader, host/guest isolation, mandatory fallback, storage immutability, network isolation, secure RPC, or bounded guest-agent rules.
- Do not modify unrelated repositories.
- Do not silently change this plan. If the user explicitly changes project direction, update the master plan + state first and bump the plan version.
- Preserve historical RESULT archives unchanged.
- Resolve conflicts in favor of the canonical master plan, with tests/evidence.
- Update `PROJECT_STATE.json` only after evidence exists.

## Current starting point

Plan v1.0.2 is canonical. G0 and G1 are complete on `main`; CHAT-12 is integrated and the post-merge canonical source checks are green.

The next required gate is **G2 / CHAT-01: integrate the P1 device-capability and physical-device acceptance path**. Source/CI readiness is not `DEVICE_ACCEPTED`; G2 PASS still requires fresh evidence from a real stock ARM64 Android device.
