# How to use the Modal plugin

Run Modal CLI commands from Kestra flows to deploy and invoke serverless functions on Modal's cloud infrastructure.

## Authentication

Pass your Modal credentials via the `env` map using `MODAL_TOKEN_ID` and `MODAL_TOKEN_SECRET`. Store secrets in [secrets](https://kestra.io/docs/concepts/secret) and apply connection properties globally with [plugin defaults](https://kestra.io/docs/workflow-components/plugin-defaults).

## Tasks

`cli.ModalCLI` runs Modal CLI commands in a container (default image `ghcr.io/kestra-io/modal`) — set `commands` (required, list of shell commands to run). Use `beforeCommands` to run setup steps before the main commands. Pass credentials and other runtime values via `env` (a map of environment variables). Stage local files into the container with `inputFiles` and retrieve outputs with `outputFiles`.
