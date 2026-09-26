package dev.hakumi.neri;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.ArrayList;

/** Opt-in real-adapter contract: set NERI_DAP_ADAPTER, NERI_DAP_EXECUTABLE and NERI_DAP_SOURCE. */
public final class NeriDapProtocolTest {
    private static final Gson JSON = new Gson();

    public static void main(String[] args) throws Exception {
        String adapter = System.getenv("NERI_DAP_ADAPTER");
        String executable = System.getenv("NERI_DAP_EXECUTABLE");
        String source = System.getenv("NERI_DAP_SOURCE");
        if (adapter == null || executable == null || source == null) {
            System.out.println("Neri LLDB DAP protocol test skipped; adapter, executable and source were not provided.");
            return;
        }
        Process process = new ProcessBuilder(adapter).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        var reads = Executors.newSingleThreadExecutor();
        var mailbox = new ArrayList<JsonObject>();
        try (var input = new BufferedInputStream(process.getInputStream()); var output = process.getOutputStream()) {
            int sequence = 1;
            send(output, sequence, "initialize", "{\"adapterID\":\"neri-lldb\",\"clientID\":\"neri-rider-test\",\"linesStartAt1\":true,\"columnsStartAt1\":true,\"pathFormat\":\"path\"}");
            requireSuccess(await(reads, input, mailbox, message -> response(message, 1)));

            var launch = NeriDapLaunchArgumentsProvider.launchArguments("protocol-test", executable,
                new File(executable).getParent(), "");
            send(output, ++sequence, "launch", JSON.toJson(launch.getArguments()));
            await(reads, input, mailbox, message -> event(message, "initialized"));

            String breakpoint = "{\"source\":{\"path\":" + JSON.toJson(source)
                + "},\"breakpoints\":[{\"line\":12}],\"sourceModified\":false}";
            send(output, ++sequence, "setBreakpoints", breakpoint);
            JsonObject breakpointResponse = await(reads, input, mailbox, message -> response(message, 3));
            requireSuccess(breakpointResponse);
            require(breakpointResponse.toString().contains("\"verified\":true"), "LLDB did not verify the Neri source breakpoint");

            send(output, ++sequence, "configurationDone", "{}");
            requireSuccess(await(reads, input, mailbox, message -> response(message, 4)));
            JsonObject stopped = await(reads, input, mailbox, message -> event(message, "stopped"));
            requireSuccess(await(reads, input, mailbox, message -> response(message, 2)));
            int threadId = stopped.getAsJsonObject("body").get("threadId").getAsInt();

            send(output, ++sequence, "stackTrace", "{\"threadId\":" + threadId + "}");
            JsonObject stack = await(reads, input, mailbox, message -> response(message, 5));
            requireSuccess(stack);
            JsonArray frames = stack.getAsJsonObject("body").getAsJsonArray("stackFrames");
            require(!frames.isEmpty(), "LLDB returned no stack frames at the Neri breakpoint");
            require(source.equals(frames.get(0).getAsJsonObject().getAsJsonObject("source").get("path").getAsString()),
                "LLDB stopped in a frame that does not map to the Neri source");
            int frameId = frames.get(0).getAsJsonObject().get("id").getAsInt();

            send(output, ++sequence, "scopes", "{\"frameId\":" + frameId + "}");
            JsonObject scopes = await(reads, input, mailbox, message -> response(message, 6));
            requireSuccess(scopes);
            JsonArray scopeValues = scopes.getAsJsonObject("body").getAsJsonArray("scopes");
            require(!scopeValues.isEmpty(), "LLDB returned no scopes for the Neri frame");
            JsonObject localScope = scopeValues.asList().stream().map(JsonElement::getAsJsonObject)
                .filter(scope -> scope.get("name").getAsString().toLowerCase().contains("local"))
                .findFirst().orElse(scopeValues.get(0).getAsJsonObject());
            int variablesReference = localScope.get("variablesReference").getAsInt();

            send(output, ++sequence, "variables", "{\"variablesReference\":" + variablesReference + "}");
            JsonObject variables = await(reads, input, mailbox, message -> response(message, 7));
            requireSuccess(variables);
            boolean foundScalar = variables.getAsJsonObject("body").getAsJsonArray("variables").asList().stream()
                .map(JsonElement::getAsJsonObject)
                .anyMatch(variable -> "scalar".equals(variable.get("evaluateName").getAsString())
                    && "41".equals(variable.get("value").getAsString()));
            require(foundScalar, "LLDB did not expose the named Neri local variable scalar: " + variables);

            send(output, ++sequence, "continue", "{\"threadId\":" + threadId + "}");
            requireSuccess(await(reads, input, mailbox, message -> response(message, 8)));
            send(output, ++sequence, "disconnect", "{\"terminateDebuggee\":true}");
            requireSuccess(await(reads, input, mailbox, message -> response(message, 9)));
            System.out.println("Neri LLDB DAP initialize, launch, breakpoint, stack, scope, variable and disconnect passed.");
        } finally {
            reads.shutdownNow();
            process.destroy();
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
        }
    }

    private static void send(OutputStream output, int sequence, String command, String arguments) throws IOException {
        var request = new JsonObject();
        request.addProperty("seq", sequence);
        request.addProperty("type", "request");
        request.addProperty("command", command);
        request.add("arguments", JsonParser.parseString(arguments));
        String json = JSON.toJson(request);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        output.write(("Content-Length: " + bytes.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        output.write(bytes);
        output.flush();
    }

    private static JsonObject await(ExecutorService reads, InputStream input, ArrayList<JsonObject> mailbox,
                                    java.util.function.Predicate<JsonObject> expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        for (int index = 0; index < mailbox.size(); index++) {
            if (expected.test(mailbox.get(index))) return mailbox.remove(index);
        }
        while (System.nanoTime() < deadline) {
            Future<JsonObject> next = reads.submit(() -> read(input));
            long remaining = Math.max(1, deadline - System.nanoTime());
            JsonObject message = next.get(remaining, TimeUnit.NANOSECONDS);
            if (expected.test(message)) return message;
            mailbox.add(message);
        }
        throw new AssertionError("Timed out waiting for LLDB DAP response");
    }

    private static JsonObject read(InputStream input) throws IOException {
        int length = -1;
        for (;;) {
            String line = line(input);
            if (line.isEmpty()) break;
            if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) length = Integer.parseInt(line.substring(15).trim());
        }
        if (length < 0 || length > 8 * 1024 * 1024) throw new IOException("Invalid DAP Content-Length");
        byte[] payload = input.readNBytes(length);
        if (payload.length != length) throw new EOFException("Truncated DAP message");
        return JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String line(InputStream input) throws IOException {
        var line = new ByteArrayOutputStream();
        for (int value; (value = input.read()) >= 0;) {
            if (value == '\n') return line.toString(StandardCharsets.US_ASCII).replaceFirst("\\r$", "");
            if (line.size() >= 8192) throw new IOException("DAP header line exceeds 8192 bytes");
            line.write(value);
        }
        throw new EOFException("LLDB DAP closed its output");
    }

    private static boolean response(JsonObject message, int requestSequence) {
        return "response".equals(message.get("type").getAsString())
            && message.get("request_seq").getAsInt() == requestSequence;
    }

    private static boolean event(JsonObject message, String name) {
        return "event".equals(message.get("type").getAsString()) && name.equals(message.get("event").getAsString());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void requireSuccess(JsonObject response) {
        require(response.has("success") && response.get("success").getAsBoolean(),
            "LLDB DAP request failed: " + response);
    }
}
