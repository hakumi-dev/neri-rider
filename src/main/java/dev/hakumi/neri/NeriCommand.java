package dev.hakumi.neri;

import com.intellij.util.execution.ParametersListUtil;
import java.util.ArrayList;
import java.util.List;

final class NeriCommand {
    static List<String> arguments(String compiler, String mode, String sources,
                                  boolean release, String output, String programArguments) {
        List<String> command = new ArrayList<>(List.of(compiler, mode));
        command.addAll(ParametersListUtil.parse(sources));
        if (release) command.add("--release");
        if (mode.equals("build") && !output.isBlank()) command.addAll(List.of("--output", output));
        if (mode.equals("run") && !programArguments.isBlank()) {
            command.add("--");
            command.addAll(ParametersListUtil.parse(programArguments));
        }
        return command;
    }
}
