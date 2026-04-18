# Kestra Modal Plugin

## What

- Provides plugin components under `io.kestra.plugin.modal.cli`.
- Includes classes such as `ModalCLI`.

## Why

- This plugin integrates Kestra with Modal CLI.
- It provides tasks that execute Modal CLI commands.

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
