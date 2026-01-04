// System Monitor JS Plugin
// Demonstrates plin.useAtom() for reactive state management
// Shows real-time system statistics that update automatically

const SystemMonitorPage = ({ bootApi, plin }) => {
    // ==========================================================================
    // REACTIVE STATE with useAtom
    // ==========================================================================
    // plin.useAtom() subscribes to a CLJS atom and re-renders when it changes.
    // This is the KEY to building reactive UIs that respond to system state.
    
    const stateAtom = plin.get(bootApi, "state");
    const systemState = plin.useAtom(stateAtom);
    
    // ==========================================================================
    // LOCAL REACT STATE for UI interactions
    // ==========================================================================
    const [filter, setFilter] = React.useState("");
    const [sortBy, setSortBy] = React.useState("id"); // "id" or "status"
    const [showDisabledOnly, setShowDisabledOnly] = React.useState(false);
    
    // ==========================================================================
    // EXTRACT DATA using plin utilities
    // ==========================================================================
    const allPlugins = plin.toArray(plin.get(systemState, "all-plugins") || []);
    const disabledIds = plin.get(systemState, "disabled-ids") || new Set();
    const lastError = plin.get(systemState, "last-error");
    
    // ==========================================================================
    // COMPUTED VALUES
    // ==========================================================================
    const disabledCount = plin.isSet(disabledIds) ? disabledIds.size : 0;
    const enabledCount = allPlugins.length - disabledCount;
    
    const getPluginId = (plugin) => {
        const id = plin.get(plugin, "id");
        return id ? String(id) : "unknown";
    };
    
    const getPluginDoc = (plugin) => {
        return plin.get(plugin, "doc") || "No description available";
    };
    
    const isDisabled = (plugin) => {
        const id = getPluginId(plugin);
        if (plin.isSet(disabledIds)) {
            return disabledIds.has(id) || disabledIds.has(":" + id);
        }
        return false;
    };
    
    const isSystemPlugin = (plugin) => {
        const id = getPluginId(plugin);
        return id.includes("plin.boot") || id === "system-api";
    };
    
    // Filter and sort plugins
    const filteredPlugins = allPlugins
        .filter(plugin => {
            const id = getPluginId(plugin);
            const doc = getPluginDoc(plugin);
            const matchesFilter = filter === "" || 
                id.toLowerCase().includes(filter.toLowerCase()) ||
                doc.toLowerCase().includes(filter.toLowerCase());
            const matchesDisabledFilter = !showDisabledOnly || isDisabled(plugin);
            return matchesFilter && matchesDisabledFilter;
        })
        .sort((a, b) => {
            if (sortBy === "status") {
                const aDisabled = isDisabled(a);
                const bDisabled = isDisabled(b);
                if (aDisabled !== bDisabled) return aDisabled ? 1 : -1;
            }
            return getPluginId(a).localeCompare(getPluginId(b));
        });
    
    // ==========================================================================
    // ACTIONS - Interact with the system
    // ==========================================================================
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
    
    const togglePlugin = (plugin) => {
        if (isSystemPlugin(plugin)) return;
        
        const id = getPluginId(plugin);
        const disabled = isDisabled(plugin);
        const keywordId = toClojureKeyword(id);
        
        const enableFn = plin.get(bootApi, "enable-plugin-no-reload!");
        const disableFn = plin.get(bootApi, "disable-plugin-no-reload!");
        const reloadFn = plin.get(bootApi, "reload!");
        
        if (disabled && enableFn) {
            enableFn(keywordId);
        } else if (!disabled && disableFn) {
            disableFn(keywordId);
        }
        
        // Reload triggers state change, useAtom will auto-update UI!
        if (reloadFn) {
            reloadFn().catch(err => console.error("Reload failed:", err));
        }
    };
    
    // ==========================================================================
    // RENDER
    // ==========================================================================
    return React.createElement("div", 
        { className: "p-6 max-w-6xl mx-auto" },
        
        // Header
        React.createElement("div", { className: "mb-8" },
            React.createElement("h1", 
                { className: "text-3xl font-bold text-gray-900 mb-2" },
                "🖥️ System Monitor"
            ),
            React.createElement("p", 
                { className: "text-gray-600" },
                "Real-time plugin monitoring powered by plin.useAtom() - UI updates automatically when system state changes!"
            )
        ),
        
        // Stats Cards
        React.createElement("div", 
            { className: "grid grid-cols-1 md:grid-cols-4 gap-4 mb-8" },
            
            // Total Plugins
            React.createElement("div", 
                { className: "bg-white rounded-xl shadow-md p-6 border-l-4 border-blue-500" },
                React.createElement("div", { className: "text-sm text-gray-500 uppercase tracking-wide" }, "Total Plugins"),
                React.createElement("div", { className: "text-3xl font-bold text-gray-900 mt-1" }, allPlugins.length)
            ),
            
            // Enabled
            React.createElement("div", 
                { className: "bg-white rounded-xl shadow-md p-6 border-l-4 border-green-500" },
                React.createElement("div", { className: "text-sm text-gray-500 uppercase tracking-wide" }, "Enabled"),
                React.createElement("div", { className: "text-3xl font-bold text-green-600 mt-1" }, enabledCount)
            ),
            
            // Disabled
            React.createElement("div", 
                { className: "bg-white rounded-xl shadow-md p-6 border-l-4 border-red-500" },
                React.createElement("div", { className: "text-sm text-gray-500 uppercase tracking-wide" }, "Disabled"),
                React.createElement("div", { className: "text-3xl font-bold text-red-600 mt-1" }, disabledCount)
            ),
            
            // Health Status
            React.createElement("div", 
                { className: `bg-white rounded-xl shadow-md p-6 border-l-4 ${lastError ? 'border-yellow-500' : 'border-green-500'}` },
                React.createElement("div", { className: "text-sm text-gray-500 uppercase tracking-wide" }, "Health"),
                React.createElement("div", 
                    { className: `text-xl font-bold mt-1 ${lastError ? 'text-yellow-600' : 'text-green-600'}` },
                    lastError ? "⚠️ Error" : "✅ Healthy"
                )
            )
        ),
        
        // useAtom Demo Box
        React.createElement("div", 
            { className: "bg-gradient-to-r from-purple-50 to-blue-50 border border-purple-200 rounded-xl p-4 mb-6" },
            React.createElement("div", { className: "flex items-center gap-2 mb-2" },
                React.createElement("span", { className: "text-2xl" }, "⚡"),
                React.createElement("span", { className: "font-semibold text-purple-800" }, "Reactive Updates Demo")
            ),
            React.createElement("p", { className: "text-sm text-purple-700" },
                "This page uses ", 
                React.createElement("code", { className: "bg-purple-100 px-1 rounded" }, "plin.useAtom()"),
                " to subscribe to system state. Try enabling/disabling plugins below - the stats above update instantly without any manual refresh!"
            )
        ),
        
        // Filters
        React.createElement("div", 
            { className: "bg-white rounded-xl shadow-md p-4 mb-6" },
            React.createElement("div", { className: "flex flex-wrap gap-4 items-center" },
                
                // Search
                React.createElement("div", { className: "flex-1 min-w-[200px]" },
                    React.createElement("input", {
                        type: "text",
                        placeholder: "🔍 Search plugins...",
                        className: "w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent",
                        value: filter,
                        onChange: (e) => setFilter(e.target.value)
                    })
                ),
                
                // Sort
                React.createElement("select", {
                    className: "px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500",
                    value: sortBy,
                    onChange: (e) => setSortBy(e.target.value)
                },
                    React.createElement("option", { value: "id" }, "Sort by Name"),
                    React.createElement("option", { value: "status" }, "Sort by Status")
                ),
                
                // Show disabled only
                React.createElement("label", { className: "flex items-center gap-2 cursor-pointer" },
                    React.createElement("input", {
                        type: "checkbox",
                        className: "w-4 h-4 text-blue-600 rounded focus:ring-blue-500",
                        checked: showDisabledOnly,
                        onChange: (e) => setShowDisabledOnly(e.target.checked)
                    }),
                    React.createElement("span", { className: "text-gray-700" }, "Disabled only")
                )
            )
        ),
        
        // Plugin List
        React.createElement("div", 
            { className: "bg-white rounded-xl shadow-md overflow-hidden" },
            React.createElement("div", 
                { className: "px-6 py-4 bg-gray-50 border-b border-gray-200" },
                React.createElement("h2", { className: "font-semibold text-gray-800" },
                    `Plugins (${filteredPlugins.length} of ${allPlugins.length})`
                )
            ),
            React.createElement("div", 
                { className: "divide-y divide-gray-100 max-h-[500px] overflow-y-auto" },
                filteredPlugins.map((plugin, index) => {
                    const pluginId = getPluginId(plugin);
                    const disabled = isDisabled(plugin);
                    const isSystem = isSystemPlugin(plugin);
                    const doc = getPluginDoc(plugin);
                    
                    return React.createElement("div", 
                        { 
                            key: index,
                            className: `px-6 py-4 flex items-center justify-between hover:bg-gray-50 transition-colors ${disabled ? 'bg-gray-50' : ''}`
                        },
                        React.createElement("div", { className: "flex-1 min-w-0" },
                            React.createElement("div", { className: "flex items-center gap-2" },
                                React.createElement("span", 
                                    { className: `w-2 h-2 rounded-full ${disabled ? 'bg-red-400' : 'bg-green-400'}` }
                                ),
                                React.createElement("span", 
                                    { className: `font-mono text-sm ${disabled ? 'text-gray-400 line-through' : 'text-gray-800'}` },
                                    pluginId
                                ),
                                isSystem && React.createElement("span", 
                                    { className: "text-xs bg-blue-100 text-blue-700 px-2 py-0.5 rounded-full" },
                                    "System"
                                )
                            ),
                            React.createElement("p", 
                                { className: "text-sm text-gray-500 mt-1 truncate" },
                                doc
                            )
                        ),
                        React.createElement("div", { className: "ml-4" },
                            !isSystem && React.createElement("button", {
                                className: `px-4 py-2 rounded-lg font-medium text-sm transition-all ${
                                    disabled 
                                        ? 'bg-green-100 text-green-700 hover:bg-green-200' 
                                        : 'bg-red-100 text-red-700 hover:bg-red-200'
                                }`,
                                onClick: () => togglePlugin(plugin)
                            }, disabled ? "Enable" : "Disable")
                        )
                    );
                })
            )
        ),
        
        // Footer info
        React.createElement("div", 
            { className: "mt-6 text-center text-sm text-gray-500" },
            React.createElement("p", null,
                "Built with JavaScript • Uses ",
                React.createElement("code", { className: "bg-gray-100 px-1 rounded" }, "plin.useAtom()"),
                " for reactive state"
            )
        )
    );
};

// ==========================================================================
// Factory Pattern for Dependency Injection
// ==========================================================================
const createPageComponent = (bootApi, plin) => {
    console.log("System Monitor: Initialized with plin utilities:", Object.keys(plin));
    return function PageWrapper() {
        return React.createElement(SystemMonitorPage, { bootApi, plin });
    };
};

// Icon as Hiccup (NOT a React component)
const MonitorIcon = ["svg", {
    className: "w-5 h-5",
    fill: "none",
    stroke: "currentColor",
    viewBox: "0 0 24 24",
    xmlns: "http://www.w3.org/2000/svg"
}, ["path", {
    strokeLinecap: "round",
    strokeLinejoin: "round",
    strokeWidth: 2,
    d: "M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"
}]];

// ==========================================================================
// Plugin Definition
// ==========================================================================
return {
    doc: "System Monitor - Real-time plugin monitoring demonstrating plin.useAtom() for reactive state updates",
    
    deps: ["plinpt.i-application", "plin.boot", "plinpt.i-js-utils"],
    
    contributions: {
        "plinpt.i-application/nav-items": [{
            id: "system-monitor-js",
            parentId: "development",
            label: "System Monitor (JS)",
            description: "Real-time system monitoring with reactive state",
            route: "system-monitor",
            icon: "icon",
            component: "page",
            order: 54
        }]
    },
    
    beans: {
        "page": {
            doc: "System monitor page with reactive useAtom subscription",
            type: "react-component",
            value: createPageComponent,
            inject: ["plin.boot/api", "plinpt.i-js-utils/api"]
        },
        "icon": {
            doc: "Monitor icon as Hiccup data",
            type: "hiccup",
            value: MonitorIcon
        }
    }
};
