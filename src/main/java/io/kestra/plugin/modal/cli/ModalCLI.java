package io.kestra.plugin.modal.cli;

import java.util.List;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.runner.docker.Docker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
// callSuper = false: the inherited env has no @ToString.Exclude, keeps rendered secrets out
@ToString(callSuper = false)
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run Modal CLI commands",
    description = """
        Executes Modal CLI steps inside a task-runner container (default image `ghcr.io/kestra-io/modal`, through the `interpreter`, `/bin/sh -c` by default). Pass Modal tokens via environment variables; supports Namespace Files, input files, and optional output file collection.
        The inherited `warningOnStdErr` and `runner` properties are ignored. The deprecated `docker` option only applies on Kestra 1.3, use `taskRunner`.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Run a Hello-World [Modal](https://modal.com/) example. Make sure to replace the `MODAL_TOKEN_ID` and `MODAL_TOKEN_SECRET` with your Modal credentials.",
            full = true,
            code = {
                """
                    id: modal_hello_world
                    namespace: company.team
                    tasks:
                      - id: hello
                        type: io.kestra.plugin.modal.cli.ModalCLI
                        env:
                          MODAL_TOKEN_ID: "your_modal_token_id"
                          MODAL_TOKEN_SECRET: "your_modal_token_secret"
                        commands:
                          - modal run hello.py
                        inputFiles:
                          hello.py: |
                            import modal

                            app = modal.App("hello-world")

                            @app.function()
                            def hello():
                                print("hello from modal")
                                return "Success!"
                    """
            }
        ),
        @Example(
            title = "Pass environment variables to the Modal CLI task from Kestra's inputs.",
            full = true,
            code = {
                """
                    id: env_vars_modal
                    namespace: company.team
                    inputs:
                      - id: run_modal
                        displayName: Whether to run the Modal task
                        type: BOOL
                        defaults: true
                      - id: cpu
                        type: SELECT
                        displayName: CPU request
                        description: The number of CPU cores to allocate to the container
                        defaults: "0.25"
                        values: ["0.25", "0.5", "0.75", "1.0", "1.5", "2.0", "4.0", "8.0", "16.0", "32.0"]
                        dependsOn:
                          inputs:
                            - run_modal
                          condition: "{{ inputs.run_modal }}"

                      - id: memory
                        type: SELECT
                        displayName: Memory request
                        description: Amount of memory in MiB to allocate to the container
                        defaults: "512"
                        values: ["512", "1024", "2048", "4096", "8192", "16384", "32768"]
                        dependsOn:
                          inputs:
                            - run_modal
                          condition: "{{ inputs.run_modal }}"
                    tasks:
                      - id: set_compute_resources
                        type: io.kestra.plugin.modal.cli.ModalCLI
                        env:
                          MODAL_TOKEN_ID: "{{ secret('MODAL_TOKEN_ID') }}"
                          MODAL_TOKEN_SECRET: "{{ secret('MODAL_TOKEN_SECRET') }}"
                          CPU: "{{ inputs.cpu }}"
                          MEMORY: "{{ inputs.memory }}"
                        commands:
                          - modal run script.py
                        inputFiles:
                          script.py: |
                            import os
                            import modal

                            app = modal.App(
                                "env-vars",
                                secrets=[modal.Secret.from_local_environ(env_keys=["CPU", "MEMORY"])],
                            )


                            @app.function(cpu=float(os.getenv("CPU")), memory=int(os.getenv("MEMORY")))
                            def generate_data():
                                cpu = os.getenv("CPU")
                                memory = os.getenv("MEMORY")
                                resources = dict(cpu=cpu, memory=memory)
                                print(f"Running the function with CPU={cpu} and MEMORY={memory}")
                                return resources


                            @app.local_entrypoint()
                            def main():
                                output = generate_data.remote()
                                print(f"hello from main function - output is: {output}")
                    """
            }
        ),
        @Example(
            title = "Execute a Python script from Git on a cloud VM using [Modal](https://modal.com/).",
            full = true,
            code = {
                """
                    id: modal_git
                    namespace: company.team

                    tasks:
                      - id: repository
                        type: io.kestra.plugin.core.flow.WorkingDirectory
                        tasks:
                          - id: clone
                            type: io.kestra.plugin.git.Clone
                            branch: main
                            url: https://github.com/kestra-io/scripts

                          - id: modal_cli
                            type: io.kestra.plugin.modal.cli.ModalCLI
                            commands:
                              - modal run modal/getting_started.py
                            env:
                              MODAL_TOKEN_ID: "{{ secret('MODAL_TOKEN_ID') }}"
                              MODAL_TOKEN_SECRET: "{{ secret('MODAL_TOKEN_SECRET') }}"
                    """
            }
        ),
        @Example(
            title = "Execute a Python script on a GPU-powered instance in the cloud using [Modal](https://modal.com/). First, add [the script](https://github.com/kestra-io/scripts/blob/main/modal/gpu.py) that you want to orchestrate as a Namespace File in the Editor and point to it in the `commands` section.",
            full = true,
            code = {
                """
                    id: modal
                    namespace: company.team

                    tasks:
                      - id: modal_cli
                        type: io.kestra.plugin.modal.cli.ModalCLI
                        namespaceFiles:
                          enabled: true
                        commands:
                          - modal run gpu.py
                        env:
                          MODAL_TOKEN_ID: "{{ secret('MODAL_TOKEN_ID') }}"
                          MODAL_TOKEN_SECRET: "{{ secret('MODAL_TOKEN_SECRET') }}"
                    """
            }
        )
    }
)
public class ModalCLI extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    private static final String DEFAULT_IMAGE = "ghcr.io/kestra-io/modal";

    @Schema(
        title = "Modal CLI commands",
        description = "Required list of CLI lines executed through the `interpreter` (`/bin/sh -c` by default) inside the task runner container."
    )
    @NotNull
    @PluginProperty(group = "main")
    protected Property<List<String>> commands;

    // Kept here: core has no docker property from 2.0, flows using it would fail validation
    @Schema(
        title = "Deprecated, use 'taskRunner' instead"
    )
    @PluginProperty(group = "deprecated")
    @Deprecated
    private DockerOptions docker;

    @Schema(
        title = "The task runner to use",
        description = "Task runners are provided by plugins, each have their own properties."
    )
    @PluginProperty(group = "execution")
    @Builder.Default
    @Valid
    private TaskRunner<?> taskRunner = Docker.instance();

    @Schema(
        title = "Task runner container image",
        description = "Container image used when the task runner is container-based; defaults to ghcr.io/kestra-io/modal."
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    private Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Fail fast on the first failing command",
        description = """
            If true, `set -e` is prepended to `beforeCommands`, so the first failing line stops the run.
            Defaults to false, so a failing `beforeCommands` line does not stop the run.
            """
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    protected Property<Boolean> failFast = Property.ofValue(false);

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        return this.commands(runContext)
            .withInterpreter(this.getInterpreter())
            .withBeforeCommands(this.getBeforeCommands())
            .withBeforeCommandsWithOptions(true)
            .withCommands(this.commands)
            .run();
    }

    @Override
    protected DockerOptions injectDefaults(RunContext runContext, DockerOptions original) {
        if (original == null) {
            return null;
        }

        var builder = original.toBuilder();
        if (original.getImage() == null) {
            builder.image(DEFAULT_IMAGE);
        }
        if (original.getEntryPoint() == null || original.getEntryPoint().isEmpty()) {
            builder.entryPoint(List.of(""));
        }

        return builder.build();
    }
}
