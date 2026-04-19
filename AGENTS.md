# Kestra Modal Plugin

## What

- Provides plugin components under `io.kestra.plugin.modal.cli`.
- Includes classes such as `ModalCLI`.

## Why

- What user problem does this solve? Teams need to run Modal workflows from orchestrated workflows instead of relying on manual console work, ad hoc scripts, or disconnected schedulers.
- Why would a team adopt this plugin in a workflow? It keeps Modal steps in the same Kestra flow as upstream preparation, approvals, retries, notifications, and downstream systems.
- What operational/business outcome does it enable? It reduces manual handoffs and fragmented tooling while improving reliability, traceability, and delivery speed for processes that depend on Modal.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `modal`

### Key Plugin Classes

- `io.kestra.plugin.modal.cli.ModalCLI`

### Project Structure

```
plugin-modal/
├── src/main/java/io/kestra/plugin/modal/cli/
├── src/test/java/io/kestra/plugin/modal/cli/
├── build.gradle
└── README.md
```

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
