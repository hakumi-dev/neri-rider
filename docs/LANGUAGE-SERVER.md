# Language-server integration

The Neri language server belongs to the compiler repository and is independent
of the editor. The Rider plugin provides configuration, process lifecycle and
presentation through the JetBrains LSP API. It launches the selected toolchain's
`neri lsp` command over stdio; protocol messages use stdout.

## Server responsibilities

The server owns document synchronization, analysis and protocol capabilities.
Diagnostics and semantic information use Neri's lexer, parser, binder, type rules
and source ranges. Syntax coloring is not a substitute for semantic analysis.
The compiler's [language-server reference](https://github.com/hakumi-dev/neri/blob/main/docs/LANGUAGE-SERVER.md)
defines supported features and limitations; clients use the capabilities returned
by initialization.

## Client responsibilities

The plugin selects a compatible compiler, launches its server subject to project
trust, and presents protocol results. Changing the project compiler restarts the
language service. Document support requires actual project content membership,
not merely a running server or a nonempty list of content roots.

Declaration navigation uses Rider 262's LSP request executor and navigation
presentation directly. The action is registered on Neri editor components with
the active keymap's Go to Declaration keyboard shortcuts because Rider routes its
global declaration action to the ReSharper backend. Keymap changes refresh these
component bindings; other editor types retain their normal declaration action.

Other LSP clients can use the same server, although their presentation and
supported features may differ. Run, Build and Check invoke the Neri CLI and use
their execution configuration's sources, arguments and working directory.
Editor content membership does not select a project unit or its sources.

For Ito applications, use a compiler with Ito editor-context support and prepare
the dependency graph with `ito install`, `ito build` or `ito test`. Open the live
package sources. `.neri/ito/sources/` contains compilation snapshots; the server
reports their original source paths instead of treating them as editable projects.

Canonical `manifest.json` files declare project units, sources and references;
legacy `neri.json` files remain supported. The server selects the unit containing
the opened source. Command-line `--unit` selection is separate from editor content
membership.

## Validation boundary

Server protocol contracts and client SDK tests validate separate boundaries.
Semantic integration contracts cover diagnostics on unsaved text, clearing after
correction, precise Unicode ranges and process recovery. Packaging and syntax
coloring validate separate behavior.

References: [JetBrains LSP API](https://plugins.jetbrains.com/docs/intellij/language-server-protocol.html)
and [run configurations](https://plugins.jetbrains.com/docs/intellij/run-configurations.html).
