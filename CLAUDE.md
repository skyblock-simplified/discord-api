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
