# Neri for Rider

Neri source highlighting and Run/Build/Check configurations for JetBrains Rider 2026.2.

The plugin registers the **Neri** file type for `*.hk`. It distinguishes class
and function declarations, type references in signatures and construction,
parameters, fields, function calls, keywords, strings and escapes, numbers,
annotations, comments, and native operations. Colors follow the editor theme. Type references are identified from syntax,
including lowercase class names; capitalization alone does not classify a value
as a class. Declaration modifiers use forms such as `abstract class`,
`override readonly def`, and `static unsafe def`. Contextual words are highlighted
by their role when used as names. Line comments use `#`; parentheses,
brackets, and double quotes have editor pairing rules.

The plugin uses Rider's TextMate Bundles support and does not require a Neri
compiler for highlighting. Execution configurations require an installed Neri
compiler. Live diagnostics use the compiler's editor-independent language server.
Semantic features are supplied by the selected compiler's language server;
the plugin does not implement a separate type system. Debug sessions reuse
Rider's DAP client and an installed LLDB adapter. The server belongs with the Neri compiler;
see [the integration boundary](docs/LANGUAGE-SERVER.md).

## Run, build and check

**Run → Edit Configurations → + → Neri** configures the compiler executable,
source files (quote paths containing spaces), working directory and action:

- `run`: compile and execute, forwarding program arguments after `--`.
- `build`: produce an executable, optionally with an explicit output path.
- `check`: run the compiler's validation command and display its diagnostics.

**Release optimization** enables optimized builds. The toolbar's **Run ▶** executes
the selected action; it does not implement Rider's global CMake Build action.
Right-clicking a `.hk` file can create a Run configuration for that file. For
programs spread across multiple files, use `--project manifest.json` in the source
arguments field, optionally followed by `--unit <name>`, or list explicit
sources. The working directory resolves relative paths. Spaces are supported
in directory names, but `.hk` file names must not contain whitespace.
The project compiler setting is **Settings → Languages & Frameworks → Neri**.
The default is `~/.neri/bin/neri` when present, otherwise `neri` from PATH.
This setting controls the language server and new execution configurations;
existing Run configurations retain their explicitly selected compiler.
**Show symbol documentation** controls explanatory text in hover and completion
for compatible language servers. Disabling it preserves types, signatures and
navigation; applying the setting restarts the language service.
Commands execute directly without a shell. Console input/output and process
termination use Rider's process runner. **Debug** builds a non-release executable
and launches it through the standard `lldb-dap` protocol. Rider provides source
breakpoints, stepping, stack frames, scopes, variables, and the debug console.
Set an LLDB DAP executable in the Run configuration when `lldb-dap` is not on
PATH; on macOS the plugin also discovers the active Xcode toolchain via `xcrun`.
Expression evaluation follows LLDB's C debug-information model; it does not
implement Neri expression or function-call semantics.

On native Windows x86-64, `pwsh -File scripts/build.ps1 install` from the Neri
checkout installs the compiler using PowerShell 7, the Visual Studio C++
workload and Windows SDK. The installer adds `%USERPROFILE%\.neri\bin` to the
per-user `PATH`. Rider can select `%USERPROFILE%\.neri\bin\neri.exe` explicitly;
restart Rider after installation. The [Windows build reference](https://github.com/hakumi-dev/neri/blob/main/docs/WINDOWS.md)
defines the pinned LLVM, GitHub CLI and validation requirements.

The ZIP targets Rider 2026.2.1. Command integration tests accept a Neri
installation to validate execution against the selected compiler.

## Installation reference

[GitHub releases](https://github.com/hakumi-dev/neri-rider/releases) publish the
plugin ZIP and SHA-256 checksum. Rider requires **TextMate Bundles** enabled in
**Settings → Plugins → Installed**. **Install Plugin from Disk** in that page
accepts the ZIP. Pre-release packages provide the capabilities described here;
semantic coverage depends on the selected compiler.

The plugin refreshes TextMate after dynamic installation. **Tools → Neri → Reload
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
Rider SDK and writes `build/neri-<version>.zip` with a SHA-256 sidecar. Rider's binaries
are not redistributed in the plugin.

Grammar tests exercise token scopes, Unicode names, escaped strings, comment
boundaries, recovery while editing incomplete strings, and file conventions.
They run without opening an IDE.

Every build also instantiates the file type against the Rider SDK and checks
its XML language registration. Secondary TextMate file types must omit the
`language` attribute. Debug integration tests use Rider's descriptor reader to
check the required DAP module and extension names, and construct each registered
debug provider and breakpoint type through the SDK's constructor lookup pattern.

## Editor contracts

Live diagnostics require a Neri build supporting `neri lsp`; older compilers
still run programs but cannot start the language server. Opening `.hk` starts
the server through JetBrains' LSP API. Changing the project compiler restarts it.
The compiler is installed separately; this ZIP does not bundle it.
The server uses `manifest.json` at the project root to resolve compilation units, including
unsaved dependencies and standard libraries. Without matching source membership,
documents are analyzed independently. The selected toolchain advertises its
semantic capabilities, including diagnostics, completion and navigation; the
compiler's language-server documentation defines their coverage and limitations.
After replacing the compiler, use **Tools → Neri → Restart Language Server**.

Enter after a complete block header indents the body and inserts a missing `end`
when the compiler advertises newline formatting. The compiler recognizes block
syntax; the plugin keeps the caret inside the body and discards stale responses.
This follows the editor's **Insert pair bracket** setting and uses the standard
LSP `textDocument/onTypeFormatting` request. Abstract method signatures remain
bodyless. A busy or stopped server leaves ordinary Enter available.

Units declare source directories or files and explicit library references.
Open a common parent folder in Rider when sources and referenced projects must
all be edited and watched together. The compiler can resolve explicit references
outside the opened project root, but Rider only forwards closed-file and manifest
changes detected below that root; it does not add a global watcher for external
references. The client forwards source and manifest creation, edits, moves and
deletion within the opened project. Generated output directories are excluded
from these notifications.

Declaration navigation uses the active keymap's mouse shortcut (Ctrl+click in
the default Linux keymap) and the same compiler-backed action as **Go to
Declaration**. The shortcut is limited to project Neri sources. Ctrl+hover
underlining is not provided by this integration.

The plugin handles `.hk` editor events independently of CMake initialization.
If an opened project source is not in content, it creates a `neri-language` content module
under `.idea`, excluding `build`, `.bootstrap`, `.git` and `.idea`. Existing
content roots are not changed. An unrelated root does not skip Neri support.
Editor membership does not select a compilation unit. **Tools → Neri → Language Server Status** reports server and file status.
JetBrains' trusted-project guard controls process launch.

If the server runs but diagnostics are absent, use the language-service status
action to inspect the actual source file's content membership, exclusions, module,
PSI and highlighting availability. An existing content root need not include that
file. Rider backend analysis tools and frontend LSP presentation are separate;
a backend tool rejecting a source does not establish whether the LSP handles it.

## Source map

- `bundles/neri`: language association, TextMate grammar, and editor conventions.
- `src/main/java`: Rider registration, LSP lifecycle, navigation, execution and DAP integration.
- `src/main/resources/META-INF/plugin.xml`: plugin identity and compatibility.
- `tests`: grammar, Rider SDK, CLI and language-server integration contracts.
- `scripts/Build.java`: compilation and ZIP packaging using the Rider JDK; no Python.

The plugin targets platform build `262.*`. Other Rider releases require a
compatibility check before changing that range.

The integration uses the platform's
[TextMate bundle provider extension](https://plugins.jetbrains.com/docs/intellij/intellij-community-plugins-extension-point-list.html)
and Rider's [local plugin installation](https://www.jetbrains.com/help/rider/Managing_Plugins.html).
Token categories follow the standard [TextMate scopes](https://macromates.com/manual/en/language_grammars#naming_conventions).
