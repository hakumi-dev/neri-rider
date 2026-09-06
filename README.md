# Neri for Rider

Neri source highlighting and Run/Build/Check configurations for JetBrains Rider 2026.2.

The plugin registers the **Neri** file type for `*.hk`. It distinguishes class
and function declarations, type references in signatures and construction,
parameters, fields, function calls, keywords, strings and escapes, numbers,
annotations, comments, and native operations. Colors follow the editor theme. Type references are identified from syntax,
including lowercase class names; capitalization alone does not classify a value
as a class. Line comments use `#`; parentheses,
brackets, and double quotes have editor pairing rules.

The plugin uses Rider's TextMate Bundles support and does not require a Neri
compiler for highlighting. Execution configurations require an installed Neri
compiler. Live diagnostics use the compiler's editor-independent language server.
Semantic completion and a debugger are not implemented. The server belongs with the Neri compiler;
see [the integration boundary](docs/LANGUAGE-SERVER.md).

## Run, build and check

Open **Run → Edit Configurations → + → Neri**. Select the compiler executable,
source files (quote paths containing spaces), working directory and action:

- `run`: compile and execute, forwarding program arguments after `--`.
- `build`: produce an executable, optionally with an explicit output path.
- `check`: run the compiler's validation command and display its diagnostics.

Select **Release optimization** when needed. The toolbar's **Run ▶** executes
the selected action; it does not implement Rider's global CMake Build action.
Right-clicking a `.hk` file can create a Run configuration for that file. For
programs spread across multiple files, list all sources in the configuration.
Configure the project compiler in **Settings → Languages & Frameworks → Neri**.
The default is `~/.neri/bin/neri` when present, otherwise `neri` from PATH.
This setting controls the language server and new execution configurations;
existing Run configurations retain their explicitly selected compiler.
Commands execute directly without a shell. Console input/output and process
termination use Rider's process runner. Debug is deliberately unavailable.

The ZIP targets Rider 2026.2.1. Command integration tests accept a Neri
installation to validate execution against the selected compiler.

## Install

Download the ZIP and its SHA-256 checksum from
[GitHub releases](https://github.com/hakumi-dev/neri-rider/releases).
Pre-release packages provide the capabilities described here, not complete
semantic language support.

1. Enable **TextMate Bundles** in **Settings → Plugins → Installed**.
2. Open **Settings → Plugins → gear menu → Install Plugin from Disk**.
3. Select `neri-0.3.2.zip` and accept the installation.
4. Open a `.hk` source file.

The plugin refreshes TextMate after dynamic installation. **Tools → Reload Neri
Highlighting** reloads the grammar and reports registration status in the status
bar and `idea.log`. Use this action if an existing editor remains unhighlighted.
If Rider requests a restart while updating plugins, follow that prompt.

Under **Settings → Editor → File Types → Neri**, `*.hk` is registered automatically.
If an existing manual association takes precedence, assign `*.hk` to **Neri**
in that settings page. For an individual file override, use **Override File
Type** and select **Neri**.

The bundle in `bundles/neri` can also be added directly under **Settings → Editor
→ TextMate Bundles**. Use either the installed plugin or the manual bundle,
not both.

## Build and test

Requirements: Node.js 22 or newer for grammar tests, and a Rider 2026.2 installation with
its bundled JDK 25 and TextMate plugin.

```sh
npm ci --ignore-scripts
npm test
/path/to/Rider/jbr/bin/java scripts/Build.java /path/to/Rider
/path/to/Rider/jbr/bin/java scripts/Build.java /path/to/Rider /path/to/neri
```

On macOS, pass the application's `Contents` directory. The build uses the local
Rider SDK and writes `build/neri-0.3.2.zip` with a SHA-256 sidecar. Rider's binaries
are not redistributed in the plugin.

Grammar tests exercise token scopes, Unicode names, escaped strings, comment
boundaries, recovery while editing incomplete strings, and file conventions.
They run without opening an IDE.

Every build also instantiates the file type against the Rider SDK and checks
its XML language registration. Secondary TextMate file types must omit the
`language` attribute.

## Structure

Live diagnostics require a Neri build supporting `neri lsp`; older compilers
still run programs but cannot start the language server. Opening `.hk` starts
the server through JetBrains' LSP API. Changing the project compiler restarts it.
The compiler is installed separately; this ZIP does not bundle it. A compiler
without the `lsp` command can still provide syntax highlighting and execution
actions, but not live diagnostics.
The server analyzes each document independently, including unsaved text,
and loads its standard library. Cross-file user declarations, completion and
navigation are unsupported. The compiler's language-server documentation defines
the supported protocol capabilities.

The plugin handles `.hk` editor events independently of CMake initialization.
If an opened project source is not in content, it creates a `neri-language` content module
under `.idea`, excluding `build`, `.bootstrap`, `.git` and `.idea`. Existing
content roots are not changed. An unrelated root does not skip Neri support.
This is editor membership, not a compilation
source set. **Tools → Neri Language Service** reports state and offers restart.
JetBrains' trusted-project guard controls process launch.

If the server runs but diagnostics are absent, use the language-service status
action to inspect the actual source file's content membership, exclusions, module,
PSI and highlighting availability. An existing content root need not include that
file. Rider backend analysis tools and frontend LSP presentation are separate;
a backend tool rejecting a source does not establish whether the LSP handles it.

- `bundles/neri`: language association, TextMate grammar, and editor conventions.
- `src/main/java`: registration of the bundled Neri grammar with Rider.
- `src/main/resources/META-INF/plugin.xml`: plugin identity and compatibility.
- `tests`: grammar contracts.
- `scripts/Build.java`: compilation and ZIP packaging using the Rider JDK; no Python.

The plugin targets platform build `262.*`. Other Rider releases require a
compatibility check before changing that range.

The integration uses the platform's
[TextMate bundle provider extension](https://plugins.jetbrains.com/docs/intellij/intellij-community-plugins-extension-point-list.html)
and Rider's [local plugin installation](https://www.jetbrains.com/help/rider/Managing_Plugins.html).
