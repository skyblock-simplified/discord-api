# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

This is a submodule of the SkyBlock-Simplified multi-module Gradle project (Java 21, Gradle 9.4+). Run commands from the monorepo root (`../`).

```bash
# Build this module
./gradlew :discord-api:build

# Run tests
./gradlew :discord-api:test

# Clean build
./gradlew :discord-api:clean :discord-api:build

# Generate SVG hierarchy diagrams
./gradlew :discord-api:generateDiagrams
```

**Required environment variables:** `DISCORD_TOKEN`, `DEVELOPER_ERROR_LOG_CHANNEL_ID`

The debug bot (`src/test/.../debug/DebugBot.java`) can be run directly to test commands in isolation.

## Architecture Overview

This module is a **framework layer on top of Discord4J** that provides a builder-driven, reactive API for building Discord bots. Entry point: `DiscordBot` (sole class in root `discordapi` package). Configuration via `DiscordConfig` (in `handler/`).

```
DiscordBot (abstract) → DiscordConfig (handler/) → initialize() → login() + connect()
    ├── CommandHandler        — registers & routes commands
    ├── EmojiHandler          — manages custom emoji upload/lookup
    ├── ExceptionHandler      — abstract base in handler/exception/
    │   ├── DiscordExceptionHandler  — formats errors into Discord embeds
    │   ├── SentryExceptionHandler   — captures to Sentry with Discord context
    │   └── CompositeExceptionHandler — chains multiple handlers in sequence
    ├── ResponseHandler       — caches active Response messages (handler/response/)
    └── ShardHandler          — gateway shard management (handler/shard/)
```

### Command System

Commands extend `DiscordCommand<C extends CommandContext<?>>` and are annotated with `@Structure(...)`:

```
DiscordCommand<SlashCommandContext>    → Slash commands (/command)
DiscordCommand<UserCommandContext>     → Right-click user commands
DiscordCommand<MessageCommandContext>  → Right-click message commands
```

- `@Structure` defines: `name`, `description`, `parent` (for subcommands), `group` (for subcommand groups), `guildId` (-1 for global), `ephemeral`, `developerOnly`, `singleton`, `botPermissions`, `userPermissions`, `integrations`, `contexts`
- `getParameters()` returns `ConcurrentUnmodifiableList<Parameter>` for slash command options
- `process(C context)` is the abstract method to implement command logic, returns `Mono<Void>`
- Commands are discovered via `Reflection.getResources().filterPackage(...).getTypesOf(DiscordCommand.class)` and registered through `CommandHandler`
- The `apply()` method in `DiscordCommand` handles permission checks, parameter validation, and error handling before calling `process()`
- Command-specific exceptions in `command/exception/`: `CommandException`, `PermissionException`, `BotPermissionException`, `DeveloperPermissionException`, `InputException`, `ExpectedInputException`, `ParameterException`, `DisabledCommandException`, `SingletonCommandException`

### Response System

`Response` is a single `final class` built via `Response.builder()`. It manages a `HistoryHandler<Page, String>` for page navigation and a `PaginationHandler` for building pagination components (buttons, select menus, modals).

Page hierarchy:
```
Page (interface)
├── TreePage  — implements Subpages<TreePage>; supports nested subpages, embeds, content
└── FormPage  — form/question pages for sequential input
```

- `Page.builder()` → `TreePage.TreePageBuilder`
- `Page.form()` → `FormPage.QuestionBuilder`
- `Response.builder()` builds the response; `Response.from()` creates a pre-filled builder from an existing response; `response.mutate()` is shorthand for `Response.from(this)`

Response features:
- Multiple `Page` instances (select menu navigation)
- `ItemHandler<T>` for paginated items with sort/filter/search
  - `EmbedItemHandler` — renders items as embed fields
  - `ComponentItemHandler` — renders items as `Section` components
- Interactive components (`Button`, `SelectMenu`, `TextInput`, `Modal`, `RadioGroup`, `Checkbox`, `CheckboxGroup`)
- Attachments, embeds, reactions
- Auto-expiration via `timeToLive` (5-300 seconds)
- Automatic Discord4J spec generation (`getD4jCreateSpec()`, `getD4jEditSpec()`, etc.)

### Component System (top-level `component/` package)

Components are a top-level package (`component/`), independent of `response/`. They are quality-of-life builders for their Discord4J counterparts and can be constructed independently.

```
component/                — Component (interface), TextDisplay
component/interaction/    — Button, SelectMenu, TextInput, Modal,
                            RadioGroup, Checkbox, CheckboxGroup
component/layout/         — ActionRow, Container, Section, Separator, Label
component/media/          — Attachment, FileUpload, MediaData, MediaGallery, Thumbnail
component/capability/     — application-level behavioral contracts:
    EventInteractable, ModalUpdatable, Toggleable, UserInteractable
component/scope/          — Discord placement scoping interfaces:
    ActionComponent, LayoutComponent, AccessoryComponent, ContainerComponent,
    LabelComponent, SectionComponent, TopLevelMessageComponent, TopLevelModalComponent
```

Components support Discord's Components V2 flag (`IS_COMPONENTS_V2`) — detected automatically when v2 component types are present.

### Context Hierarchy

Every event gets a typed context wrapping the Discord4J event:

```
context/                  — EventContext
context/scope/            — MessageContext, InteractionContext, DeferrableInteractionContext,
                            CommandContext, ComponentContext, ActionComponentContext
context/capability/       — ExceptionContext, TypingContext
context/command/          — SlashCommandContext, UserCommandContext,
                            MessageCommandContext, AutoCompleteContext
context/component/        — ButtonContext, SelectMenuContext, OptionContext, ModalContext,
                            CheckboxContext, CheckboxGroupContext, RadioGroupContext
context/message/          — ReactionContext
```

`ComponentContext` extends both `MessageContext` and `DeferrableInteractionContext` (diamond via interfaces).

Contexts provide: `reply()`, `edit()`, `followup()`, `presentModal()`, `deleteFollowup()`, and access to the cached `Response`/`CachedResponse`.

### Listener System

All listeners extend `DiscordListener<T extends Event>` and are auto-registered via classpath scanning of the `dev.sbs.discordapi.listener` package. Additional listeners can be registered through `DiscordConfig.Builder.withListeners()`.

```
listener/command/         — SlashCommandListener, UserCommandListener,
                            MessageCommandListener, AutoCompleteListener
listener/component/       — ComponentListener, ButtonListener, SelectMenuListener,
                            ModalListener, CheckboxListener, CheckboxGroupListener,
                            RadioGroupListener
listener/message/         — MessageCreateListener, MessageDeleteListener,
                            ReactionListener, ReactionAddListener, ReactionRemoveListener
listener/lifecycle/       — DisconnectListener, GuildCreateListener
```

### Handler Classes

**`handler/exception/`** — pluggable error handling chain:
- **`ExceptionHandler`** — abstract base class (extends `DiscordReference`)
- **`DiscordExceptionHandler`** — formats errors into Discord embeds, sends to user and developer log channel
- **`SentryExceptionHandler`** — captures exceptions to Sentry with enriched Discord context tags
- **`CompositeExceptionHandler`** — chains multiple handlers in sequence

**`handler/response/`** — active response message cache:
- **`ResponseEntry`** — interface associating a `Response` with Discord snowflake identifiers; supports dirty-checking via `isModified()`
- **`CachedResponse`** — cached entry for a primary response message, tracking lifecycle state (busy, deferred, last interaction time), followups, and per-user active modals
- **`ResponseFollowup`** — cached entry for a followup message
- **`ResponseHandler`** — manages the `ConcurrentList<CachedResponse>` cache

**`response/handler/`** — page navigation and pagination:
- **`HistoryHandler<P, I>`** — generic stack-based page navigation (sibling and child navigation via `Subpages`)
- **`PaginationHandler`** — builds pagination components (buttons, select menus, sort/filter/search modals) with emoji access
- **`OutputHandler<T>`** — interface for cache-invalidation contract
- **`ItemHandler<T>`** — interface for paginated item lists; implementations: `EmbedItemHandler` (embed fields), `ComponentItemHandler` (sections)
- **`FilterHandler`** / **`SortHandler`** / **`SearchHandler`** — item filtering, sorting, and search state
- **`Filter`** / **`Sorter`** / **`Search`** — builder-pattern definitions for filter/sort/search criteria

## Module-Specific Patterns

- **`DiscordReference`** — base class for anything needing bot access; provides `getDiscordBot()`, `getEmoji()`, `isDeveloper()`, permission helpers.
- **`Component.Type`** enum maps to Discord's integer component type IDs and tracks which types require the Components V2 flag.
- **`api` dependency** — declared as a Maven coordinate (`dev.sbs:api:0.1.0`) in `build.gradle.kts`.

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **discord-api** (2625 symbols, 9738 relationships, 224 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## When Debugging

1. `gitnexus_query({query: "<error or symptom>"})` — find execution flows related to the issue
2. `gitnexus_context({name: "<suspect function>"})` — see all callers, callees, and process participation
3. `READ gitnexus://repo/discord-api/process/{processName}` — trace the full execution flow step by step
4. For regressions: `gitnexus_detect_changes({scope: "compare", base_ref: "main"})` — see what your branch changed

## When Refactoring

- **Renaming**: MUST use `gitnexus_rename({symbol_name: "old", new_name: "new", dry_run: true})` first. Review the preview — graph edits are safe, text_search edits need manual review. Then run with `dry_run: false`.
- **Extracting/Splitting**: MUST run `gitnexus_context({name: "target"})` to see all incoming/outgoing refs, then `gitnexus_impact({target: "target", direction: "upstream"})` to find all external callers before moving code.
- After any refactor: run `gitnexus_detect_changes({scope: "all"})` to verify only expected files changed.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Tools Quick Reference

| Tool | When to use | Command |
|------|-------------|---------|
| `query` | Find code by concept | `gitnexus_query({query: "auth validation"})` |
| `context` | 360-degree view of one symbol | `gitnexus_context({name: "validateUser"})` |
| `impact` | Blast radius before editing | `gitnexus_impact({target: "X", direction: "upstream"})` |
| `detect_changes` | Pre-commit scope check | `gitnexus_detect_changes({scope: "staged"})` |
| `rename` | Safe multi-file rename | `gitnexus_rename({symbol_name: "old", new_name: "new", dry_run: true})` |
| `cypher` | Custom graph queries | `gitnexus_cypher({query: "MATCH ..."})` |

## Impact Risk Levels

| Depth | Meaning | Action |
|-------|---------|--------|
| d=1 | WILL BREAK — direct callers/importers | MUST update these |
| d=2 | LIKELY AFFECTED — indirect deps | Should test |
| d=3 | MAY NEED TESTING — transitive | Test if critical path |

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/discord-api/context` | Codebase overview, check index freshness |
| `gitnexus://repo/discord-api/clusters` | All functional areas |
| `gitnexus://repo/discord-api/processes` | All execution flows |
| `gitnexus://repo/discord-api/process/{name}` | Step-by-step execution trace |

## Self-Check Before Finishing

Before completing any code modification task, verify:
1. `gitnexus_impact` was run for all modified symbols
2. No HIGH/CRITICAL risk warnings were ignored
3. `gitnexus_detect_changes()` confirms changes match expected scope
4. All d=1 (WILL BREAK) dependents were updated

## Keeping the Index Fresh

After committing code changes, the GitNexus index becomes stale. Re-run analyze to update it:

```bash
npx gitnexus analyze
```

If the index previously included embeddings, preserve them by adding `--embeddings`:

```bash
npx gitnexus analyze --embeddings
```

To check whether embeddings exist, inspect `.gitnexus/meta.json` — the `stats.embeddings` field shows the count (0 means no embeddings). **Running analyze without `--embeddings` will delete any previously generated embeddings.**

> Claude Code users: A PostToolUse hook handles this automatically after `git commit` and `git merge`.

## CLI

| Task | Read this skill file                                |
|------|-----------------------------------------------------|
| Understand architecture / "How does X work?" | `~/.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `~/.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `~/.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `~/.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `~/.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `~/.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->
