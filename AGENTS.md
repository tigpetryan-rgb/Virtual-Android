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

As of plan v1.0.1, all 13 parallel workstreams have archived RESULT artifacts, but canonical repository integration and final physical-device/end-to-end acceptance are still pending.

G0 is complete. The canonical repository is `tigpetryan-rgb/Virtual-Android` on `main`.

The next required gate is **G1: integrate CHAT-12 CI reproducibility and prove a clean-checkout reproducible canonical baseline before any other workstream integration**.
