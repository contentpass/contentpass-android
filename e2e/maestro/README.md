# Maestro E2E

This directory contains Maestro flows for the example app.

## Requirements

- Android emulator or device running the debug app
- Maestro CLI available on `PATH`

For local runs, install Maestro with:

```bash
curl -Ls "https://get.maestro.mobile.dev" | bash
```

## Flows

- `e2e/maestro/initial-unauthenticated-state.yaml` verifies the initial unauthenticated screen state.
- `e2e/maestro/count-impression.yaml` verifies counting an impression while unauthenticated.
- `e2e/maestro/logout-keeps-user-unauthenticated.yaml` verifies logout keeps the example app unauthenticated.

## Running Locally

Start an Android emulator or connect a device, then run:

```bash
scripts/run-maestro-e2e.sh
```
