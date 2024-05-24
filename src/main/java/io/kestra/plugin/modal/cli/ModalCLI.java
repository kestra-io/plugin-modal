package io.kestra.plugin.modal.cli;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.*;
import io.kestra.core.models.tasks.runners.ScriptService;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.RunnerType;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.runners.RunContext;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute Modal commands from the Command Line Interface"
)
@Plugin(
    examples = {
        @Example(
            title = "Execute a Python script on a GPU-powered instance in the cloud using [Modal](https://modal.com/). Make sure to add [the script](https://github.com/kestra-io/scripts/blob/main/modal/gpu.py) that you want to orchestrate as a Namespace File in the Editor and point to it in the `commands` section.",
            full = true,
            code = {
                """
                id: modal

                namespace: dev
                
                tasks:
                  - id: modal_cli
                    type: io.kestra.plugin.modal.cli.ModalCLI
                    namespaceFiles:
                      enabled: true
                    commands:
                      - modal run scripts/gpu.py
                    env:
                      MODAL_TOKEN_ID: "{{ secret('MODAL_TOKEN_ID') }}"
                      MODAL_TOKEN_SECRET: "{{ secret('MODAL_TOKEN_SECRET') }}"
                """
            }
        ),
        @Example(
            title = "Execute a Python script from Git on a cloud VM using [Modal](https://modal.com/).",
            full = true,
            code = {
                """
                id: modal_git
                namespace: dev

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
        )        
    }
)
public class ModalCLI extends Task implements RunnableTask<ScriptOutput>, NamespaceFilesInterface, InputFilesInterface, OutputFilesInterface {
    private static final String DEFAULT_IMAGE = "ghcr.io/kestra-io/modal";

    @Schema(
        title = "The commands to execute before the main list of commands"
    )
    @PluginProperty(dynamic = true)
    protected List<String> beforeCommands;

    @Schema(
        title = "The commands to run"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    @NotEmpty
    protected List<String> commands;

    @Schema(
        title = "Additional environment variables for the current process."
    )
    @PluginProperty(
        additionalProperties = String.class,
        dynamic = true
    )
    protected Map<String, String> env;

    @Schema(
        title = "Docker options when for using `DOCKER` runner",
        defaultValue = "{image=" + DEFAULT_IMAGE + ", pullPolicy=ALWAYS}"
    )
    @PluginProperty
    @Builder.Default
    protected DockerOptions docker = DockerOptions.builder().build();

    private NamespaceFiles namespaceFiles;

    private Object inputFiles;

    private List<String> outputFiles;

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        return new CommandsWrapper(runContext)
            .withWarningOnStdErr(true)
            .withRunnerType(RunnerType.DOCKER)
            .withDockerOptions(injectDefaults(getDocker()))
            .withEnv(Optional.ofNullable(env).orElse(new HashMap<>()))
            .withNamespaceFiles(namespaceFiles)
            .withInputFiles(inputFiles)
            .withOutputFiles(outputFiles)
            .withCommands(
                ScriptService.scriptCommands(
                    List.of("/bin/sh", "-c"),
                    Optional.ofNullable(this.beforeCommands).map(throwFunction(runContext::render)).orElse(null),
                    runContext.render(this.commands)
                )
            )
            .run();
    }

    private DockerOptions injectDefaults(DockerOptions original) {
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
