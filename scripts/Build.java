import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;

/** Source-launch with Rider's JDK: no Python or external build system. */
class Build {
    static void run(List<String> command) throws Exception {
        int code = new ProcessBuilder(command).inheritIO().start().waitFor();
        if (code != 0) throw new IOException("Command failed (" + code + "): " + command.getFirst());
    }

    static List<Path> files(Path directory, String suffix) throws IOException {
        try (var paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(suffix)).sorted().toList();
        }
    }

    static byte[] archive(Map<String, byte[]> files) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (var item : new TreeMap<>(files).entrySet()) {
                var entry = new ZipEntry(item.getKey());
                entry.setTimeLocal(LocalDateTime.of(1980, 1, 1, 0, 0));
                zip.putNextEntry(entry);
                zip.write(item.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Build.java <Rider directory> [Neri compiler]");
        }
        Path root = Path.of("").toAbsolutePath();
        Path rider = Path.of(args[0]).toAbsolutePath();
        String product = Files.readString(rider.resolve("product-info.json"));
        if (!product.matches("(?s).*\"productCode\"\\s*:\\s*\"RD\".*") ||
                !product.matches("(?s).*\"buildNumber\"\\s*:\\s*\"262\\..*")) {
            throw new IllegalArgumentException("Rider platform 262 required");
        }
        Path javaHome = Path.of(System.getProperty("java.home"));
        String javac = javaHome.resolve("bin/javac").toString();
        String java = javaHome.resolve("bin/java").toString();
        String sdk = String.join(File.pathSeparator, rider.resolve("lib/*").toString(),
                rider.resolve("plugins/textmate-plugin/lib/*").toString(),
                rider.resolve("plugins/textmate-plugin/lib/modules/*").toString());
        Path descriptor = root.resolve("src/main/resources/META-INF/plugin.xml");
        var xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(descriptor.toFile());
        String version = xml.getElementsByTagName("version").item(0).getTextContent();
        Path build = root.resolve("build");
        Files.createDirectories(build);
        Path temp = Files.createTempDirectory(build, "java-build-");
        try {
            Path classes = Files.createDirectory(temp.resolve("classes"));
            var compile = new ArrayList<>(List.of(javac, "--release", "25", "-proc:none", "-g:none",
                    "-classpath", sdk, "-d", classes.toString()));
            files(root.resolve("src/main/java"), ".java").forEach(p -> compile.add(p.toString()));
            run(compile);
            String cp = classes + File.pathSeparator + sdk;
            for (String test : args.length == 2 ? List.of("NeriFileTypeTest", "NeriLspScopeTest", "NeriCompletionSupportTest", "NeriNavigationSupportTest", "NeriCommandTest")
                    : List.of("NeriFileTypeTest", "NeriLspScopeTest", "NeriCompletionSupportTest", "NeriNavigationSupportTest")) {
                run(List.of(javac, "--release", "25", "-proc:none", "-classpath", cp, "-d", classes.toString(),
                        root.resolve("tests/java/dev/hakumi/neri/" + test + ".java").toString()));
                var command = new ArrayList<>(List.of(java, "-classpath", cp, "dev.hakumi.neri." + test));
                if (test.equals("NeriFileTypeTest")) command.add(descriptor.toString());
                else if (test.equals("NeriCommandTest")) { command.add(Path.of(args[1]).toAbsolutePath().toString()); command.add(temp.toString()); }
                run(command);
            }
            Map<String, byte[]> jar = new TreeMap<>();
            for (Path file : files(classes, ".class")) {
                if (!file.getFileName().toString().contains("Test"))
                    jar.put(classes.relativize(file).toString().replace(File.separatorChar, '/'), Files.readAllBytes(file));
            }
            jar.put("META-INF/plugin.xml", Files.readAllBytes(descriptor));
            Map<String, byte[]> zip = new TreeMap<>();
            zip.put("neri/lib/neri.jar", archive(jar));
            for (Path file : files(root.resolve("bundles/neri"), "")) {
                zip.put("neri/" + root.relativize(file).toString().replace(File.separatorChar, '/'), Files.readAllBytes(file));
            }
            byte[] result = archive(zip);
            Path destination = build.resolve("neri-" + version + ".zip");
            Path pending = temp.resolve(destination.getFileName());
            Files.write(pending, result);
            Files.move(pending, destination, StandardCopyOption.REPLACE_EXISTING);
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(result));
            Files.writeString(build.resolve(destination.getFileName() + ".sha256"), digest + "  " + destination.getFileName() + "\n");
            System.out.println(destination);
        } finally {
            try (var paths = Files.walk(temp)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
