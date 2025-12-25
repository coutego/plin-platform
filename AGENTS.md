# Architecture and Coding Conventions

This document outlines the architectural patterns and coding standards for the Pluggable/Injectable system (PLIN).

## 1. Architecture: Interface vs. Implementation

We strictly separate the **API definition** from the **Logic implementation** to ensure loose coupling and testability.

### Interface Plugin (`plinpt.ixxx`)
*   **Naming**: Prefix with `i` (e.g., `plinpt.i-app-shell`).
*   **Purpose**: Defines the **Contract**. It is the public API of the module.
*   **Contents**:
    *   **Extensions**: Defines the extension points (hooks) available for other plugins.
    *   **Bean Keys**: Declares the beans that this module exposes.
    *   **Defaults**: Provides lightweight default values (stubs, no-ops, or empty structures) so the system can load without the implementation.
*   **Dependencies**: Minimal. Should not depend on heavy libraries (like Reagent) or implementation details.
*   **Usage**: Other plugins depend on this interface to inject dependencies.

### Implementation Plugin (`plinpt.xxx`)
*   **Naming**: Matches the interface name without the `i` (e.g., `plinpt.p-app-shell`).
*   **Purpose**: Provides the **Logic**.
*   **Contents**:
    *   **Bean Overrides**: Replaces the default beans defined in the interface with actual implementations.
    *   **Components/Logic**: Contains the actual code (Reagent components, business logic).
*   **Dependencies**: Depends on `plinpt.ixxx`.

## 2. Plugin Registration & Manifest

The system uses a `public/manifest.edn` file to determine which plugins to load based on the current execution mode.

### Adding a New Plugin
To register a new plugin, you must add an entry to `public/manifest.edn`. Note the use of `libs/plinpt` path and `plinpt` namespace prefix:

    {:id :my-feature
     :tags [:ui :shared]
     :files ["libs/plinpt/my_feature/core.cljs"
             "libs/plinpt/my_feature.cljs"]
     :entry "plinpt.my-feature"}

### Tags and Modes
Plugins are loaded based on **Tags**. The application runs in different modes (defined in `src/main.cljs` and `src/server.cljs`), and each mode activates specific tags.

*   **`shared`**: Core interfaces and utilities needed everywhere (Client, Server, Demo).
*   **`ui`**: Plugins containing Reagent components and frontend logic.
*   **`demo`**: Implementations that run entirely in the browser (e.g., in-memory DB, mock services).
*   **`client`**: Frontend implementations that connect to a remote server.
*   **`server`**: Backend implementations running in Node.js.

**Crucial**: Ensure your plugin has the correct tags. If a plugin is tagged `[:ui]`, it will not be loaded in `server` mode.

## 3. Service Portability & Dependency Injection

A core requirement of this architecture is that **Business Services** must be portable. They may run in the Browser (Demo mode) or on the Server (Node.js).

### Rules for Business Logic
1.  **No Direct Namespace Dependencies**: A service implementation (e.g., `p-invoicing`) must **never** directly `(:require ...)` another service implementation (e.g., `p-inventory`).
    *   **Bad**: `(:require [plinpt.p-inventory :as inventory])`
    *   **Good**: `(:require [plinpt.i-inventory :as i-inventory])` and inject the bean.

2.  **Environment Agnostic**:
    *   Do not use `js/window`, `js/document`, or DOM APIs in business logic.
    *   Do not use `reagent.core` atoms for business state if that state needs to exist on the server. Use standard Clojure atoms or the provided shim.

3.  **Injection Only**:
    *   All dependencies (Database, Logger, Other Services) must be injected via the container.
    *   This allows the system to swap `p-alasql-db` (Browser) for `p-postgres-db` (Server) without changing a single line of business logic.

## 4. Naming Conventions: Fully Qualified Keys

To prevent collisions in the global Dependency Injection (DI) container, **all keys must be fully qualified**.

*   **Syntax**: Use the `::keyword` syntax within the namespace.
*   **Resolution**: `::my-bean` in namespace `plinpt.my-plugin` resolves to `:plinpt.my-plugin/my-bean`.
*   **Cross-Reference**: When referring to a bean from another plugin, use the alias: `::other-plugin/their-bean`.

**Bad**:

    :beans {:routes [...]} ;; Collision risk!

**Good**:

    :beans {::routes [...]} ;; Resolves to :current.ns/routes

## 5. Bean Definition: "Easy Syntax"

Avoid verbose map-based constructors (`{:constructor [...]}`) in favor of the "Easy Syntax" provided by the framework.

*   **Function Call**: `[function-var arg1 arg2]` -> Calls `(function-var arg1 arg2)` at build time.
*   **Literal Value**: `[:= value]` -> Injects `value` as is.
*   **Literal Vector**: `[:= [1 2 3]]` -> **Crucial**: `[1 2 3]` alone is interpreted as a function call. Use `[:= ...]` for data vectors.
*   **Reagent Components**: To inject a component *function* (so it can be rendered reactively), use `[:= component-fn]` or `[identity component-fn]`.
    *   **WARNING**: `[component-fn]` calls the function at build time. This returns static Hiccup and breaks reactivity.
*   **Partial Application (Components)**: When defining Reagent components or functions that return functions (factories), use `partial`.
    
    [partial component-fn arg1 arg2]
    
    This ensures the component function is created with dependencies closed over, but not executed immediately during container build.

## 6. Plugin Definition Structure

Plugins are defined using the `plin/plugin` macro (or function in CLJS) and assigned to a var named `plugin`.

### Plugin ID and Dependencies
*   **ID**: Do **NOT** provide an `:id` key manually. The `plin/plugin` macro automatically calculates the ID based on the namespace (e.g., `:plinpt.my-plugin/plugin`).
*   **Dependencies**: All plugins must explicitly list their dependencies in the `:deps` vector using the `plugin` var of the required namespace (e.g., `[other-plugin/plugin]`). 
*   **Validation**: The system validates that any fully qualified key used in `:beans` or `:contributions` belongs to a plugin listed in `:deps`.

### Allowed Root Keys
*   `:deps` (Optional): Vector of plugin vars this plugin depends on.
*   `:doc` (Optional): String describing the plugin.
*   `:beans`: Map of internal bean definitions.
*   `:contributions`: Map of extensions contributed to *other* plugins.
*   `:extensions`: List of extension points defined *by* this plugin (usually in Interface plugins).

### Documentation Requirements
The Interface Plugin (`plinpt.ixxx`) must be the **Single Source of Truth**.

1.  **Extensions**: Every extension in `:extensions` must have a `:doc` string explaining what it collects and the expected format.
2.  **Beans**: Every bean in `:beans` (in any plugin) must have metadata attached.
    *   **`:doc`**: Required. A string describing the purpose and explicitly indicating the **Type** of the bean (Value, Function, Map, Reagent Component).
    *   **`:reagent-component`**: Required. Set to `true` if the bean is a Reagent component.

## 7. Aider/LLM Interaction Guidelines

When generating documentation or code snippets that include markdown code blocks (triple backticks) within this file or similar documentation files, special care must be taken to avoid confusing the LLM or the chat interface.

*   **Escaping**: If you need to show a code block inside a markdown file, **DO NOT** use fenced code blocks (triple backticks). Instead, use **indentation** (4 spaces) to denote code blocks. This prevents the LLM's output parser from prematurely closing the file content.
*   **Ambiguity**: Avoid leaving open code blocks.
*   **Context**: When asking the LLM to generate files, ensure the prompt clearly delimits the file content.

## 8. Common Pitfalls & Best Practices

### Extensions vs. Beans
*   **Extensions** (contributions to other plugins) must be placed under the `:contributions` key.
*   **Beans** (internal definitions) must be placed inside the `:beans` map.
*   **Mistake**: Placing an extension key inside `:beans`. This creates a local bean instead of registering the extension, causing the contribution to be ignored.
*   **Mistake**: Placing an extension key at the root of the map (deprecated).

### Reactivity and Component Injection
*   **The Issue**: Reagent components are functions. If you use the standard function call syntax `[my-component]`, the framework executes the function **once at build time**.
*   **The Symptom**: The component renders initially but never updates (loses reactivity) because the atom dereferences happened during the build, not during the render cycle.
*   **The Fix**: Inject the component function itself using `[:= my-component]` or `[identity my-component]`.

## 9. Implementation Plugin Structure Guidelines

We enforce a directory-based structure for implementation plugins (`libs/plinpt/p_name/` or `src/p_name/`) to separate wiring, logic, and presentation.

### 1. The Root File (`p_name.cljs`)
*   **Role**: The Manifest / Wiring.
*   **Responsibility**: Defines the `plin/plugin` map.
*   **Contents**:
    *   `(:require ...)`: Imports `core`, `ui`, `doc`, and interface plugins.
    *   `(def plugin ...)`: The only definition allowed.
    *   **Strict Rule**: No function definitions, no atoms, no components.
    *   **Beans**: Maps interface keys to functions imported from `core` or `ui`.

### 2. The Core File (`p_name/core.cljs`)
*   **Role**: The Brain / Business Logic.
*   **Responsibility**: State management, data transformation, API implementation.
*   **Contents**:
    *   `defonce state`: Atoms holding the plugin's state.
    *   Pure functions: Logic that doesn't depend on the DOM.
    *   Service handlers: Database or API interaction logic.
    *   **Strict Rule**: Avoid Reagent components (Hiccup) here unless they are trivial.

### 3. The UI File (`p_name/ui.cljs`)
*   **Role**: The Presentation.
*   **Responsibility**: Rendering the user interface.
*   **Contents**:
    *   Reagent components.
    *   `make-route`: The function that wraps the component for the router.
    *   **Dependency**: Requires `[...core :as core]` to trigger actions or read state.

### 4. The Doc File (`p_name/doc.cljs`)
*   **Role**: The Documentation.
*   **Responsibility**: Static text for the Developer Documentation plugin.
*   **Contents**:
    *   `def functional "..."`
    *   `def technical "..."`

## 10. Bean Documentation Standard

To support the System Debugger and provide clear contracts, every bean (especially functions and components) **MUST** include `:api` metadata.

### Format
The `:api` key in the metadata map should contain:
*   `:args`: A vector of tuples describing arguments.
    *   Tuple format: `["name" example-value :optional-type]`
    *   `name`: String name of the argument.
    *   `example-value`: A valid EDN value used by the debugger to pre-fill the "Call" input.
    *   `:optional-type`: (Optional) Keyword or string describing the type.
*   `:ret`: (Optional) String or Keyword describing the return value.

### Examples

**Function**

    {::invoke
     ^{:doc "Invokes a remote service."
       :api {:args [["endpoint" :users/list :keyword]
                    ["payload"  {:limit 10} :map]]
             :ret  "Promise"}}
     [:= (fn [endpoint payload] ...)]}

**Reagent Component**

    {::icon-button
     ^{:doc "A clickable button."
       :reagent-component true
       :api {:args [["icon" :edit :keyword]
                    ["on-click" (fn [] (js/console.log "click")) :fn]]}}
     [:= (fn [icon on-click] ...)]}

**Value**

    {::config
     ^{:doc "System configuration."
       :api {:ret :map}}
     [:= {:env :dev}]}

## 11. Global State & Side Effects (STRICT)

**NEVER** access global state atoms from implementation namespaces.

*   **Forbidden**: `(:require [plin.boot :as boot])` followed by `(swap! boot/state ...)` in a `core.cljs` file.
*   **Reason**: This creates hidden coupling, breaks the dependency graph, makes testing impossible, and violates the Inversion of Control principle.
*   **Correct Approach**:
    1.  Identify the functionality you need (e.g., `register-plugin!`).
    2.  Ensure that functionality is exposed via a **Bean** in the container (e.g., `::boot/api`).
    3.  **Inject** that bean into your component or function via the `plugin` definition.
    4.  Pass the injected dependency to your logic.
