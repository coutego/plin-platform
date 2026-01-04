// Plugin Inspector JS Plugin
// Demonstrates the plin utilities API, especially useAtom for reactive state

const PluginInspectorPage = ({ bootApi, plin }) => {
    // ==========================================================================
    // useAtom DEMO: Reactive subscription to CLJS atoms
    // ==========================================================================
    // 
    // plin.useAtom() is a React hook that:
    // 1. Gets the current value of a CLJS atom
    // 2. Subscribes to changes (with debouncing)
    // 3. Re-renders the component when the atom changes
    // 4. Automatically cleans up the subscription on unmount
    //
    // This is the RECOMMENDED way to read reactive state from CLJS.
    // Without useAtom, your component won't update when the atom changes!
    
    // First, get the state atom from bootApi using plin.get for safe access
    const stateAtom = plin.get(bootApi, "state");
    
    // Now subscribe to the atom - component re-renders when state changes
    const stateValue = plin.useAtom(stateAtom);
    
    // ==========================================================================
    // Data Access DEMO: Using plin utilities for safe data extraction
    // ==========================================================================
    
    // plin.toArray() converts CLJS vectors/lists to JS arrays
    const plugins = plin.toArray(plin.get(stateValue, "all-plugins") || []);
    
    // plin.get() handles CLJS keyword-to-string conversion
    const disabledIds = plin.get(stateValue, "disabled-ids") || new Set();
    
    // ==========================================================================
    // Helper functions using plin utilities
    // ==========================================================================
    
    const getPluginId = (plugin) => {
        return plin.get(plugin, "id") || "unknown";
    };
    
    const getPluginDoc = (plugin) => {
        return plin.get(plugin, "doc") || "No description";
    };
    
    // plin.isSet() checks if a value is a JS Set (converted from CLJS set)
    const isDisabled = (plugin) => {
        const id = getPluginId(plugin);
        if (!id) return false;
        
        if (plin.isSet(disabledIds)) {
            return disabledIds.has(id);
        }
        
        if (typeof disabledIds.has === 'function') {
            return disabledIds.has(id);
        }
        
        return false;
    };
    
    const isSystemPlugin = (plugin) => {
        const id = getPluginId(plugin);
        return id === ":system-api" || id === "system-api" || 
               id === ":plin.boot/plugin" || id === "plin.boot/plugin";
    };
    
    const toClojureKeyword = (idStr) => {
        if (typeof idStr !== 'string') return idStr;
        
        const kwStr = idStr.startsWith(':') ? idStr : ':' + idStr;
        
        try {
            return window.scittle.core.eval_string(kwStr);
        } catch (e) {
            console.error("Failed to convert to keyword:", idStr, e);
            return idStr;
        }
    };
    
    // ==========================================================================
    // Plugin toggle - demonstrates calling CLJS functions from JS
    // ==========================================================================
    
    const togglePlugin = (plugin) => {
        if (!bootApi) {
            console.error("bootApi is null!");
            return;
        }
        
        const id = getPluginId(plugin);
        
        if (!id || isSystemPlugin(plugin)) {
            return;
        }
        
        const disabled = isDisabled(plugin);
        const keywordId = toClojureKeyword(id);
        
        // Get functions from bootApi using plin.get
        const enableNoReloadFn = plin.get(bootApi, "enable-plugin-no-reload!");
        const disableNoReloadFn = plin.get(bootApi, "disable-plugin-no-reload!");
        const reloadFn = plin.get(bootApi, "reload!");
        
        if (disabled && enableNoReloadFn) {
            enableNoReloadFn(keywordId);
        } else if (!disabled && disableNoReloadFn) {
            disableNoReloadFn(keywordId);
        }
        
        // After toggling, reload the system
        // Because we're using useAtom, the UI will automatically update!
        if (reloadFn) {
            const result = reloadFn();
            if (result && typeof result.then === 'function') {
                result.catch(err => {
                    console.error("Reload failed:", err);
                });
            }
        }
    };
    
    // ==========================================================================
    // Render
    // ==========================================================================
    
    const disabledCount = plin.isSet(disabledIds) ? disabledIds.size : 0;
    
    return React.createElement("div", 
        { className: "p-6 max-w-4xl mx-auto" },
        React.createElement("div", 
            { className: "bg-white rounded-xl shadow-lg p-6" },
            React.createElement("h1", 
                { className: "text-2xl font-bold text-gray-900 mb-2" },
                "Plugin Inspector"
            ),
            React.createElement("p", 
                { className: "text-gray-600 mb-6" },
                "This JS plugin demonstrates the plin utilities API. It uses useAtom for reactive updates - try enabling/disabling plugins and watch the UI update automatically!"
            ),
            
            // Info box showing plin utilities in use
            React.createElement("div", 
                { className: "mb-4 p-3 bg-blue-50 border border-blue-200 rounded-lg" },
                React.createElement("p", { className: "text-sm text-blue-800 font-medium mb-1" },
                    "🔧 Plin Utilities Demo"
                ),
                React.createElement("ul", { className: "text-sm text-blue-700 list-disc list-inside" },
                    React.createElement("li", null, "plin.useAtom() - Reactive subscription to state"),
                    React.createElement("li", null, "plin.get() - Safe data access"),
                    React.createElement("li", null, "plin.toArray() - Convert collections"),
                    React.createElement("li", null, "plin.isSet() - Type checking")
                )
            ),
            
            React.createElement("div", 
                { className: "mb-4 p-3 bg-green-50 border border-green-200 rounded-lg" },
                React.createElement("p", { className: "text-sm text-green-800" },
                    `✅ Found ${plugins.length} plugins, ${disabledCount} disabled.`
                )
            ),
            
            React.createElement("h2", 
                { className: "text-lg font-semibold text-gray-800 mb-3" },
                `Loaded Plugins (${plugins.length})`
            ),
            React.createElement("div", 
                { className: "space-y-2 max-h-96 overflow-y-auto" },
                plugins.map((plugin, index) => {
                    const pluginId = getPluginId(plugin);
                    const disabled = isDisabled(plugin);
                    const isSystem = isSystemPlugin(plugin);
                    
                    return React.createElement("div", 
                        { 
                            key: index,
                            className: `p-3 rounded-lg border ${disabled ? 'bg-gray-100 border-gray-300' : 'bg-blue-50 border-blue-200'}`
                        },
                        React.createElement("div", 
                            { className: "flex items-center justify-between" },
                            React.createElement("span", 
                                { className: `font-mono text-sm ${disabled ? 'text-gray-500 line-through' : 'text-blue-800'}` },
                                pluginId
                            ),
                            React.createElement("div",
                                { className: "flex items-center gap-2" },
                                disabled && React.createElement("span", 
                                    { className: "text-xs bg-gray-200 text-gray-600 px-2 py-1 rounded" },
                                    "Disabled"
                                ),
                                !isSystem && React.createElement("button",
                                    {
                                        className: `text-xs px-3 py-1 rounded font-medium transition-colors ${
                                            disabled 
                                                ? 'bg-green-500 hover:bg-green-600 text-white' 
                                                : 'bg-red-500 hover:bg-red-600 text-white'
                                        }`,
                                        onClick: () => togglePlugin(plugin)
                                    },
                                    disabled ? "Enable" : "Disable"
                                ),
                                isSystem && React.createElement("span",
                                    { className: "text-xs text-gray-400 italic" },
                                    "System"
                                )
                            )
                        ),
                        React.createElement("p", 
                            { className: "text-xs text-gray-600 mt-1 truncate" },
                            getPluginDoc(plugin)
                        )
                    );
                })
            )
        )
    );
};

// ==========================================================================
// Factory Pattern: Required for dependency injection
// ==========================================================================
// When using `inject`, the bean value must be a factory function that:
// 1. Receives the injected dependencies as arguments
// 2. Returns the actual component function
//
// This creates a closure so the component has access to the dependencies.

const createPageComponent = (bootApi, plin) => {
    // Log to help with debugging
    console.log("Plugin Inspector: Received plin utilities:", Object.keys(plin));
    
    return function PageWrapper() {
        return React.createElement(PluginInspectorPage, { bootApi: bootApi, plin: plin });
    };
};

const InspectorIcon = ["svg", {
    className: "w-5 h-5",
    fill: "none",
    stroke: "currentColor",
    viewBox: "0 0 24 24",
    xmlns: "http://www.w3.org/2000/svg"
}, ["path", {
    strokeLinecap: "round",
    strokeLinejoin: "round",
    strokeWidth: 2,
    d: "M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01"
}]];

return {
    doc: "Plugin Inspector - demonstrates plin utilities API for JS plugins, especially useAtom for reactive state",
    
    // Declare dependencies on other plugins
    deps: ["plinpt.i-application", "plin.boot", "plinpt.i-js-utils"],
    
    contributions: {
        "plinpt.i-application/nav-items": [{
            id: "plugin-inspector-js",
            label: "Plugin Inspector (JS)",
            description: "View and manage plugins using plin utilities",
            route: "plugin-inspector",
            icon: "icon",
            component: "page",
            parentId: "development",
            order: 53
        }]
    },
    
    beans: {
        "page": {
            doc: "Plugin inspector page demonstrating useAtom and other plin utilities",
            type: "react-component",
            value: createPageComponent,
            // Inject both boot API (for system state) and plin utilities
            inject: ["plin.boot/api", "plinpt.i-js-utils/api"]
        },
        "icon": {
            doc: "Navigation icon as Hiccup data",
            type: "hiccup",
            value: InspectorIcon
        }
    }
};
