package dev.hakumi.neri;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class NeriCommandTest {
    private static String execute(List<String> command, Path directory, int expectedStatus) throws Exception {
        Path output = Files.createTempFile(directory, "process-", ".log");
        Process process = new ProcessBuilder(command).directory(directory.toFile())
            .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Process exceeded 30 seconds");
        }
        String text = Files.readString(output);
        if (process.exitValue() != expectedStatus) throw new AssertionError(text);
        return text;
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory(Path.of(args[1]), "neri runner ");
        Path source = directory.resolve("hello_world.hk");
        Files.writeString(source, "use console\nuse host\ndef main(): Void\n  let value = host.argumentAt(0)\n  if value != null\n    console.println(value)\n  end\nend\n");
        String quoted = "\"" + source + "\"";
        String run = execute(NeriCommand.arguments(args[0], "run", quoted, false, "", "\"hello world\""), directory, 0);
        if (!run.equals("hello world\n")) throw new AssertionError("Arguments did not survive Run: " + run);
        Path binary = directory.resolve("built program");
        execute(NeriCommand.arguments(args[0], "build", quoted, true, binary.toString(), ""), directory, 0);
        if (!execute(List.of(binary.toString(), "built"), directory, 0).equals("built\n")) throw new AssertionError("Build output failed");
        Files.writeString(source, "def main(): Void\n  let value: Int = \"wrong\"\nend\n");
        String checked = execute(NeriCommand.arguments(args[0], "check", quoted, false, "", ""), directory, 1);
        if (!checked.contains("error NR")) throw new AssertionError("Check did not preserve compiler diagnostics");
        System.out.println("Run, Release build and Check passed with the installed Neri compiler.");
    }
}
