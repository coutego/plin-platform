# Overview

JavaScript plugins allow you to extend the PLIN platform without writing
ClojureScript. They are loaded dynamically via `fetch`{.verbatim} +
`eval`{.verbatim} and wrapped into CLJS plugin structures automatically.

# Basic Structure

A JS plugin file must **return** a plugin definition object. The file is
wrapped in an IIFE (Immediately Invoked Function Expression), so use
`return`{.verbatim} at the end:

``` javascript
// Your component definitions here...

const MyPage = () => {
    return React.createElement("div", { className: "p-6" },
        React.createElement("h1", null, "Hello World")
    );
};

// Plugin definition - MUST be returned
return {
    doc: "Description of your plugin",

    deps: ["plinpt.i-application", "plinpt.i-js-utils"],

    contributions: {
        "plinpt.i-application/nav-items": [{
            id: "my-page",
            label: "My Page",
            route: "/my-page",
            icon: "icon",
            component: "page",
            order: 100
        }]
    },

    beans: {
        "page": {
            doc: "My page component",
            type: "react-component",
            value: MyPage
        },
        "icon": {
            doc: "Navigation icon",
            type: "hiccup",
            value: ["svg", { /* attrs */ }, ["path", { /* attrs */ }]]
        }
    }
};
```

# Plugin Definition Fields

## `doc`{.verbatim} (string, optional)

A description of what your plugin does.

## `deps`{.verbatim} (array of strings, optional)

List of plugin namespaces this plugin depends on. Common dependencies:

- `"plinpt.i-application"`{.verbatim} - Required for adding navigation
  items
- `"plinpt.i-js-utils"`{.verbatim} - Required for plin utilities API
- `"plin.boot"`{.verbatim} - Access to the boot API for system
  introspection

## `beans`{.verbatim} (object)

Map of bean definitions. Each bean has a local name (key) and a
definition object.

## `contributions`{.verbatim} (object)

Map of contributions to extension points defined by other plugins.

## `extensions`{.verbatim} (array, optional)

Define your own extension points for other plugins to contribute to.

# Bean Types

## React Components (`type: "react-component"`{.verbatim})

Use for React components that render UI:

``` javascript
const MyComponent = () => {
    const [count, setCount] = React.useState(0);
    return React.createElement("button", 
        { onClick: () => setCount(c => c + 1) },
        `Clicked ${count} times`
    );
};

// In beans:
"my-component": {
    doc: "A clickable button component",
    type: "react-component",
    value: MyComponent
}
```

**Important:** React components are automatically wrapped for Reagent
compatibility. You can use React hooks (`useState`{.verbatim},
`useEffect`{.verbatim}, etc.) normally.

## Hiccup Data (`type: "hiccup"`{.verbatim})

Use for static data structures like icons. Hiccup is an array format
that Reagent renders:

``` javascript
// Icon as Hiccup - NOT a React component!
const MyIcon = ["svg", {
    className: "w-5 h-5",
    fill: "none",
    stroke: "currentColor",
    viewBox: "0 0 24 24",
    xmlns: "http://www.w3.org/2000/svg"
}, ["path", {
    strokeLinecap: "round",
    strokeLinejoin: "round",
    strokeWidth: 2,
    d: "M12 4v16m8-8H4"  // Plus icon
}]];

// In beans:
"icon": {
    doc: "Navigation icon as Hiccup data",
    type: "hiccup",
    value: MyIcon
}
```

**Key difference:**

- `react-component`{.verbatim}: A function that returns React elements
- `hiccup`{.verbatim}: Plain data (arrays/objects) that Reagent will
  render

## Default Type

Any value without a specific type is stored as-is:

``` javascript
"config": {
    doc: "Configuration object",
    value: { theme: "dark", maxItems: 10 }
}
```

# Plin Utilities API

The `plinpt.i-js-utils/api` bean provides helper functions for working with
ClojureScript data structures. **Always inject this when working with CLJS data.**

## Injecting Plin Utilities

``` javascript
// Factory function receives plin utilities
const createPageComponent = (plin) => {
    return function MyPage() {
        // Use plin utilities here
        return React.createElement("div", null, "Hello");
    };
};

// Bean definition
"page": {
    type: "react-component",
    value: createPageComponent,
    inject: ["plinpt.i-js-utils/api"]
}
```

## Available Utilities

### Atom Utilities

``` javascript
// Check if a value is an atom wrapper
plin.isAtom(value)  // returns boolean

// Safely dereference - works on atoms or plain values
plin.deref(maybeAtom)  // returns the current value (one-time read)
```

### Data Access

``` javascript
// Smart key lookup (tries multiple key formats)
plin.get(obj, "key")  // handles CLJS keyword conversion

// Nested access with array path
plin.getIn(obj, ["a", "b", "c"])

// Get all keys as array
plin.keys(obj)

// Get all values as array
plin.vals(obj)
```

### Type Checking

``` javascript
// Check if value is a JS Set (from CLJS set)
plin.isSet(value)

// Check if value is an array
plin.isArray(value)
```

### Collection Utilities

``` javascript
// Convert any collection to JS array
plin.toArray(coll)
```

### React Hooks

#### `plin.useAtom(atomWrapper)` - Reactive Atom Subscription

**This is the most important utility for reactive UIs.** It subscribes your
component to a CLJS atom and re-renders automatically when the atom changes.

``` javascript
// Subscribe to atom changes - component re-renders when atom updates
const value = plin.useAtom(atomWrapper);
```

**How it works:**
1. Gets the current value of the atom
2. Sets up a watch (subscription) on the atom
3. When the atom changes, triggers a React re-render
4. Automatically cleans up the subscription when component unmounts
5. Debounces rapid updates (~16ms) to prevent render storms

**Example - Reactive Plugin List:**

``` javascript
const PluginList = ({ bootApi, plin }) => {
    // Get the state atom from bootApi
    const stateAtom = plin.get(bootApi, "state");
    
    // Subscribe to changes - UI updates automatically when plugins change!
    const stateValue = plin.useAtom(stateAtom);
    
    // Extract data from the reactive state
    const plugins = plin.toArray(plin.get(stateValue, "all-plugins") || []);
    
    return React.createElement("div", null,
        React.createElement("h1", null, `${plugins.length} plugins loaded`),
        plugins.map((p, i) => 
            React.createElement("div", { key: i }, plin.get(p, "id"))
        )
    );
};
```

**Without useAtom (WRONG - won't update):**

``` javascript
// DON'T DO THIS - component won't re-render when state changes!
const PluginList = ({ bootApi, plin }) => {
    const stateAtom = plin.get(bootApi, "state");
    const stateValue = plin.deref(stateAtom);  // One-time read only!
    // ... this will show stale data
};
```

#### `plin.useAtomState(atomWrapper)` - useState-like Interface

Returns `[value, setValue]` similar to React's `useState`, but backed by a CLJS atom.

``` javascript
const [value, setValue] = plin.useAtomState(atomWrapper);

// Reset to new value
setValue(newValue);

// Update with function (like swap!)
setValue(currentValue => ({...currentValue, count: currentValue.count + 1}));
```

### Navigation

``` javascript
// Navigate to a route
plin.navigate("/some/path")

// Get current route path
const path = plin.currentPath()
```

## Complete useAtom Example

``` javascript
const PluginInspectorPage = ({ bootApi, plin }) => {
    // ==========================================================
    // STEP 1: Get the atom reference using plin.get
    // ==========================================================
    const stateAtom = plin.get(bootApi, "state");
    
    // ==========================================================
    // STEP 2: Subscribe to the atom with useAtom
    // This is the KEY step - without this, UI won't update!
    // ==========================================================
    const stateValue = plin.useAtom(stateAtom);
    
    // ==========================================================
    // STEP 3: Extract data using plin utilities
    // ==========================================================
    const plugins = plin.toArray(plin.get(stateValue, "all-plugins") || []);
    const disabledIds = plin.get(stateValue, "disabled-ids") || new Set();
    
    // plin.isSet() checks if it's a JS Set (from CLJS set)
    const disabledCount = plin.isSet(disabledIds) ? disabledIds.size : 0;
    
    // ==========================================================
    // STEP 4: Render - will automatically update when state changes
    // ==========================================================
    return React.createElement("div", { className: "p-6" },
        React.createElement("h1", null, `Found ${plugins.length} plugins`),
        React.createElement("p", null, `${disabledCount} disabled`),
        // ... render plugin list
    );
};

// Factory function for injection
const createPageComponent = (bootApi, plin) => {
    return function PageWrapper() {
        return React.createElement(PluginInspectorPage, { bootApi, plin });
    };
};

// Bean definition with injection
"page": {
    type: "react-component",
    value: createPageComponent,
    inject: ["plin.boot/api", "plinpt.i-js-utils/api"]
}
```

# Dependency Injection

Beans can request other beans to be injected using the
`inject`{.verbatim} array. The `value`{.verbatim} must be a **factory
function** that receives the injected dependencies and returns the
actual component or value.

## Basic Pattern

``` javascript
// Factory function that receives injected dependencies
const createPageComponent = (plin) => {
    // plin utilities are now available - return the actual component
    return function MyPage() {
        // Use plin inside the component
        return React.createElement("div", null, "Page with utilities");
    };
};

// In beans:
"page": {
    doc: "Page with injected plin utilities",
    type: "react-component",
    value: createPageComponent,  // Factory function, NOT the component itself
    inject: ["plinpt.i-js-utils/api"]    // List of bean keys to inject
}
```

## How Injected Values Are Converted

When CLJS values are injected into JS plugins, they are automatically
converted to JS-friendly formats:

  CLJS Type     JS Type          Access Pattern
  ------------- ---------------- ----------------------------------------------------
  Map           Plain Object     `obj["key"]`{.verbatim} or `obj.key`{.verbatim}
  Vector/List   Array            `arr[0]`{.verbatim}, `arr.length`{.verbatim}, etc.
  Set           JS Set           `set.has(value)`{.verbatim}, `set.size`{.verbatim}
  Keyword       String           `":ns/name"`{.verbatim} or `"name"`{.verbatim}
  Atom          Wrapper Object   See below
  Function      Function         Call directly

## Working with Atoms

CLJS atoms are converted to wrapper objects with enhanced methods:

``` javascript
// Atom wrapper structure:
{
    ___isAtom: true,           // Marker to identify atom wrappers
    value: { ... },            // Current dereferenced value (at conversion time)
    deref: function() { ... }, // Function to get fresh value
    swap: function(fn) { ... }, // Update with function (auto-converts JS<->CLJS)
    reset: function(val) { ... }, // Replace value entirely
    watch: function(cb) { ... }  // Subscribe to changes, returns unwatch fn
}

// RECOMMENDED: Use plin utilities instead of direct access
const value = plin.useAtom(atomWrapper);  // Reactive hook - BEST for UI
const current = plin.deref(atomWrapper);  // One-time read - for non-reactive code
```

## Important Notes on Injection

1.  **Factory Pattern Required**: When using `inject`{.verbatim}, the
    `value`{.verbatim} must be a factory function that returns the
    component, not the component itself.

2.  **Closure Pattern**: The factory function creates a closure over the
    injected values, making them available to the returned component.

3.  **String Keys**: CLJS keywords become strings. Use
    `obj["key-name"]`{.verbatim} syntax for keys with hyphens, or use
    `plin.get(obj, "key-name")` for safe access.

4.  **Circular References**: Deep CLJS structures may contain circular
    references. The converter handles these by replacing them with
    `"[circular reference]"`{.verbatim} strings.

5.  **Depth Limit**: Very deep structures are truncated at depth 50 with
    `"[max depth exceeded]"`{.verbatim}.

# Navigation Items

To add pages to the sidebar, contribute to
`plinpt.i-application/nav-items`{.verbatim}:

``` javascript
contributions: {
    "plinpt.i-application/nav-items": [{
        id: "my-page",           // Unique identifier
        label: "My Page",        // Display text in sidebar
        description: "...",      // Optional tooltip/description
        route: "/my-page",       // URL path (with leading /)
        icon: "icon",            // Bean name for the icon
        component: "page",       // Bean name for the page component
        order: 100,              // Sort order (lower = higher in list)
        parentId: "development"  // Optional: nest under a parent menu
    }]
}
```

**Note:** `icon`{.verbatim} and `component`{.verbatim} are bean names
(strings), not the actual values. The loader resolves these references
automatically.

# Complete Examples

## Simple Hello World

``` javascript
const HelloPage = () => {
    return React.createElement("div", 
        { className: "p-10 bg-white rounded-xl shadow-lg max-w-md mx-auto mt-10" },
        React.createElement("h1", 
            { className: "text-3xl font-bold text-blue-600 mb-4" },
            "Hello from JavaScript!"
        )
    );
};

const HelloIcon = ["svg", {
    className: "w-5 h-5",
    fill: "none",
    stroke: "currentColor",
    viewBox: "0 0 24 24"
}, ["path", {
    strokeLinecap: "round",
    strokeLinejoin: "round",
    strokeWidth: 2,
    d: "M14.828 14.828a4 4 0 01-5.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
}]];

return {
    doc: "A simple Hello World plugin",
    deps: ["plinpt.i-application"],

    contributions: {
        "plinpt.i-application/nav-items": [{
            id: "hello-js",
            label: "Hello JS",
            route: "/hello-js",
            icon: "icon",
            component: "page",
            order: 200
        }]
    },

    beans: {
        "page": {
            type: "react-component",
            value: HelloPage
        },
        "icon": {
            type: "hiccup",
            value: HelloIcon
        }
    }
};
```

## Interactive Game with Plin Utilities (2048)

See `src/plinpt_extras/showcase/game_2048.js`{.verbatim} for a complete example
of:

- Complex React state management with `useState`{.verbatim}
- Event handling with `useEffect`{.verbatim} and
  `useCallback`{.verbatim}
- Keyboard and touch input
- Using `plin.currentPath()` for route information
- Game logic implementation

## Plugin with useAtom for Reactive State

See `src/plinpt_extras/showcase/plugin_inspector.js`{.verbatim} for a complete
example demonstrating:

- **`plin.useAtom()`** - Reactive subscription to CLJS atoms
- **`plin.get()`** - Safe data access with keyword conversion
- **`plin.toArray()`** - Converting CLJS collections to JS arrays
- **`plin.isSet()`** - Type checking for CLJS sets
- Injecting multiple dependencies (`plin.boot/api` and `plinpt.i-js-utils/api`)
- Calling CLJS functions from JavaScript
- Automatic UI updates when system state changes

# Best Practices

1.  **Always inject plin utilities** - Add `"plinpt.i-js-utils"` to deps and
    inject `"plinpt.i-js-utils/api"` for any plugin that works with CLJS data
2.  **Use `plin.useAtom()` for reactive data** - This hook handles subscription
    and cleanup automatically. Without it, your UI won't update when atoms change!
3.  **Use `plin.get()` for data access** - Handles keyword-to-string conversion
4.  **Use Hiccup for icons** - Icons should be
    `type: "hiccup"`{.verbatim} (data), not React components
5.  **Keep bean names simple** - Use lowercase with hyphens:
    `"my-page"`{.verbatim}, `"nav-icon"`{.verbatim}
6.  **Use React.createElement** - JSX is not available; use
    `React.createElement`{.verbatim} directly
7.  **Declare dependencies** - List all plugin dependencies in
    `deps`{.verbatim} array
8.  **Use meaningful IDs** - Navigation item `id`{.verbatim} should be
    unique across all plugins
9.  **Factory pattern for injection** - When using `inject`{.verbatim},
    `value`{.verbatim} must be a factory function

# Manifest Entry

JS plugins are registered in the plugin manifest with their file path:

``` clojure
{:id :showcase.my-plugin
 :type :js
 :envs [:browser]
 :files ["src/plinpt_extras/showcase/my_plugin.js"]}
```

The namespace is derived from the `id`{.verbatim} field, converting
hyphens to underscores for compatibility.

# Debugging

## Console Logging

The loader outputs helpful logs:

- `"load-js-plugins: loading"`{.verbatim} - Plugin file being fetched
- `"wrap-js-plugin: created plugin"`{.verbatim} - Plugin successfully
  wrapped
- `"JS bean factory called"`{.verbatim} - Injection happening

## Common Issues

  Problem                        Likely Cause                 Solution
  ------------------------------ ---------------------------- ----------------------------------------------------------
  Plugin doesn\'t appear         Missing return statement     Add `return { ... }`{.verbatim} at end of file
  Icon not showing               Wrong bean type              Use `type: "hiccup"`{.verbatim} for icons
  Injection returns undefined    Wrong bean key               Check spelling of inject key
  \"Maximum call stack\" error   Circular reference in data   Use `plin.deref()` instead of direct `.deref()`
  Keys not found in map          Using dot notation           Use `plin.get(obj, "key-name")` for safe access
  Component not updating         Not using reactive hook      Use `plin.useAtom()` instead of `plin.deref()`

## Verifying Injection

Add logging to your factory function:

``` javascript
const createPageComponent = (plin) => {
    console.log("plin utilities received:", plin);
    console.log("available methods:", Object.keys(plin));

    return function MyPage() { /* ... */ };
};
```

# Technical Details

## How JS Plugins Are Loaded

1.  The manifest is fetched and parsed
2.  JS plugin entries (those with `.js`{.verbatim} files) are identified
3.  Each JS file is fetched via `fetch()`{.verbatim}
4.  The code is wrapped in an IIFE and executed via a
    `<script>`{.verbatim} element
5.  The returned object is captured and converted to a CLJS plugin map
6.  Bean references in contributions are resolved to qualified keywords
7.  The plugin is merged with other plugins and loaded into the
    container

## Namespace Derivation

The plugin namespace is derived from the manifest `id`{.verbatim} field:

- Hyphens are converted to underscores (for Scittle compatibility)
- Dots are preserved as namespace separators

Example: `"showcase.hello-react"`{.verbatim} becomes namespace
`showcase.hello_react`{.verbatim}

## Bean Key Qualification

Local bean names are qualified with the plugin namespace:

- Bean `"page"`{.verbatim} in namespace
  `showcase.hello_react`{.verbatim} becomes
  `:showcase.hello_react/page`{.verbatim}
- This ensures bean names don\'t conflict across plugins

## CLJS to JS Conversion Details

The `cljs->js-deep`{.verbatim} function handles conversion with:

- **Cycle detection**: Tracks visited objects to prevent infinite loops
- **Depth limiting**: Stops at depth 50 to prevent stack overflow
- **Atom wrapping**: Creates special wrapper objects with `swap`, `reset`, `watch` methods
- **Keyword stringification**: Converts `:ns/name`{.verbatim} to
  `"ns/name"`{.verbatim} strings
- **Debounced watch**: Atom watch callbacks are debounced at ~16ms to prevent render storms

This conversion happens automatically when injected values are passed to
JS factory functions.

## How useAtom Works Internally

The `plin.useAtom()` hook:

1. Calls `React.useState()` with the initial atom value
2. Calls `React.useEffect()` to set up a watch on the atom
3. The watch callback calls `setValue()` to trigger re-renders
4. Watch callbacks are debounced (~16ms) to batch rapid updates
5. Returns an unwatch function for cleanup when component unmounts

This is why `useAtom` must be called inside a React component - it uses React hooks internally.
