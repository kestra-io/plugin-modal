package io.kestra.plugin.modal.cli;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.*;
import io.kestra.core.models.tasks.runners.ScriptService;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.kestra.plugin.scripts.runner.docker.Docker;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.runners.RunContext;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.*;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
    @NoArgsConstructor
@Schema(
    title = "Execute Modal commands from the CLI."
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
                    type: BOOLEAN
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
                      condition: "{{ inputs.run_modal equals true }}"

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
public class ModalCLI extends Task implements RunnableTask<ScriptOutput>, NamespaceFilesInterface, InputFilesInterface, OutputFilesInterface {
    private static final String DEFAULT_IMAGE = "ghcr.io/kestra-io/modal";

    @Schema(
        title = "The commands to execute before the main list of commands"
    )
    protected Property<List<String>> beforeCommands;

    @Schema(
        title = "The commands to run"
    )
    @NotNull
    protected Property<List<String>> commands;

    @Schema(
        title = "Additional environment variables for the current process."
    )
    @PluginProperty(
        additionalProperties = String.class,
        dynamic = true
    )
    protected Map<String, String> env;

    @Schema(
        title = "Deprecated, use 'taskRunner' instead"
    )
    @PluginProperty
    @Deprecated
    private DockerOptions docker;

    @Schema(
        title = "The task runner to use.",
        description = "Task runners are provided by plugins, each have their own properties."
    )
    @PluginProperty
    @Builder.Default
    @Valid
    private TaskRunner<?> taskRunner = Docker.instance();

    @Schema(title = "The task runner container image, only used if the task runner is container-based.")
    @Builder.Default
    private Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    private NamespaceFiles namespaceFiles;

    private Object inputFiles;

    private Property<List<String>> outputFiles;

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        var renderedOutputFiles = runContext.render(this.outputFiles).asList(String.class);

        return new CommandsWrapper(runContext)
            .withWarningOnStdErr(true)
            .withDockerOptions(injectDefaults(getDocker()))
            .withTaskRunner(this.taskRunner)
            .withContainerImage(runContext.render(this.containerImage).as(String.class).orElse(null))
            .withEnv(Optional.ofNullable(env).orElse(new HashMap<>()))
            .withNamespaceFiles(namespaceFiles)
            .withInputFiles(inputFiles)
            .withOutputFiles(renderedOutputFiles.isEmpty() ? null : renderedOutputFiles)
            .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")))
            .withBeforeCommands(this.beforeCommands)
            .withCommands(this.commands)
            .run();
    }

    private DockerOptions injectDefaults(DockerOptions original) {
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
