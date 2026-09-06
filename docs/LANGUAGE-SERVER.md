# Language-server integration

The Neri language server belongs to the compiler repository and is independent
of the editor. The Rider plugin provides configuration, process lifecycle and
presentation through the JetBrains LSP API. It launches the selected toolchain's
`neri lsp` command over stdio; protocol messages use stdout.

## Server responsibilities

The server owns document synchronization, analysis and protocol capabilities.
Diagnostics and semantic information use Neri's lexer, parser, binder, type rules
and source ranges. Syntax coloring is not a substitute for semantic analysis.
The compiler repository's `docs/LANGUAGE-SERVER.md` defines supported features
and limitations; clients use the capabilities returned by initialization.

## Client responsibilities

The plugin selects a compatible compiler, launches its server subject to project
trust, and presents protocol results. Changing the project compiler restarts the
language service. Document support requires actual project content membership,
not merely a running server or a nonempty list of content roots.

Other LSP clients can use the same server, although their presentation and
supported features may differ. Run, Build and Check invoke the Neri CLI and use
their execution configuration's sources, arguments and working directory.
Editor content membership does not define a compilation unit.

## Validation

Server protocol contracts and client SDK tests validate separate boundaries.
Editor integration requires visible diagnostics on unsaved text, clearing after
correction, precise Unicode ranges and process recovery. Successful packaging
or syntax coloring alone does not establish semantic functionality.

References: [JetBrains LSP API](https://plugins.jetbrains.com/docs/intellij/language-server-protocol.html)
and [run configurations](https://plugins.jetbrains.com/docs/intellij/run-configurations.html).
