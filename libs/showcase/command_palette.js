const CommandPalette = ({ routes }) => {
    const [isOpen, setIsOpen] = React.useState(false);
    const [query, setQuery] = React.useState("");
    const [selectedIndex, setSelectedIndex] = React.useState(0);
    const inputRef = React.useRef(null);

    // Toggle with Ctrl+K or Cmd+K
    React.useEffect(() => {
        const handleKeyDown = (e) => {
            if ((e.ctrlKey || e.metaKey) && e.key === "k") {
                e.preventDefault();
                setIsOpen(prev => !prev);
            }
            if (e.key === "Escape") {
                setIsOpen(false);
            }
        };
        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, []);

    // Focus input when opened
    React.useEffect(() => {
        if (isOpen) {
            setQuery("");
            setSelectedIndex(0);
            setTimeout(() => inputRef.current?.focus(), 50);
        }
    }, [isOpen]);

    // Filter routes
    const filteredRoutes = React.useMemo(() => {
        if (!routes) return [];
        const q = query.toLowerCase();
        return routes
            .filter(r => r.path && (!q || r.path.toLowerCase().includes(q)))
            .slice(0, 10);
    }, [routes, query]);

    const handleNavigate = (path) => {
        if (path) {
            window.location.hash = path;
            setIsOpen(false);
        }
    };

    const handleInputKeyDown = (e) => {
        if (e.key === "ArrowDown") {
            e.preventDefault();
            setSelectedIndex(i => Math.min(i + 1, filteredRoutes.length - 1));
        } else if (e.key === "ArrowUp") {
            e.preventDefault();
            setSelectedIndex(i => Math.max(i - 1, 0));
        } else if (e.key === "Enter") {
            e.preventDefault();
            const route = filteredRoutes[selectedIndex];
            if (route) handleNavigate(route.path);
        }
    };

    if (!isOpen) return null;

    return React.createElement(
        "div",
        { 
            className: "fixed inset-0 z-[100] flex items-start justify-center pt-[15vh] font-sans",
            style: { fontFamily: "system-ui, -apple-system, sans-serif" }
        },
        // Backdrop
        React.createElement("div", { 
            className: "absolute inset-0 bg-black/60 backdrop-blur-sm",
            onClick: () => setIsOpen(false)
        }),
        
        // Modal
        React.createElement("div", { 
            className: "relative w-full max-w-xl bg-slate-800 border border-slate-700 rounded-xl shadow-2xl overflow-hidden flex flex-col"
        },
            // Input
            React.createElement("div", { className: "flex items-center border-b border-slate-700 px-4" },
                React.createElement("svg", { className: "w-5 h-5 text-slate-500 mr-3", fill: "none", stroke: "currentColor", viewBox: "0 0 24 24" },
                    React.createElement("path", { strokeLinecap: "round", strokeLinejoin: "round", strokeWidth: 2, d: "M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" })
                ),
                React.createElement("input", {
                    ref: inputRef,
                    type: "text",
                    className: "flex-1 h-14 outline-none text-white placeholder:text-slate-500 bg-transparent text-lg",
                    placeholder: "Go to...",
                    value: query,
                    onChange: (e) => {
                        setQuery(e.target.value);
                        setSelectedIndex(0);
                    },
                    onKeyDown: handleInputKeyDown
                })
            ),
            
            // Results
            React.createElement("div", { className: "max-h-[60vh] overflow-y-auto py-2" },
                filteredRoutes.length === 0 
                    ? React.createElement("div", { className: "p-8 text-center text-slate-500" }, "No matching routes found.")
                    : filteredRoutes.map((route, idx) => {
                        const isSelected = idx === selectedIndex;
                        return React.createElement("div", {
                            key: route.path,
                            className: `px-4 py-3 mx-2 rounded-lg cursor-pointer flex items-center justify-between transition-colors ${isSelected ? "bg-blue-600 text-white" : "text-slate-300 hover:bg-slate-700/50"}`,
                            onClick: () => handleNavigate(route.path),
                            onMouseMove: () => setSelectedIndex(idx)
                        },
                            React.createElement("div", { className: "flex items-center gap-3" },
                                React.createElement("span", { className: "font-mono text-sm" }, route.path)
                            ),
                            isSelected && React.createElement("span", { className: "text-xs text-blue-200 font-bold opacity-75" }, "⏎")
                        );
                    })
            ),
            
            // Footer
            React.createElement("div", { className: "bg-slate-900/50 px-4 py-2 text-xs text-slate-500 flex justify-between border-t border-slate-700" },
                React.createElement("div", { className: "flex gap-3" },
                    React.createElement("span", null, "↑↓ to navigate"),
                    React.createElement("span", null, "↵ to select"),
                    React.createElement("span", null, "esc to close")
                ),
                React.createElement("span", null, "JS Command Palette")
            )
        )
    );
};

// Return the Plugin Definition
return {
    doc: "Command Palette (JS Implementation)",
    deps: ["plinpt.i-application"],
    
    contributions: {
        "plinpt.i-application/overlay-components": [
            "ui"
        ]
    },
    
    beans: {
        "ui": {
            type: "react-component",
            doc: "Command Palette Overlay",
            value: CommandPalette,
            // Inject routes into the 'routes' prop
            deps: [
                { ref: "plinpt.i-application/all-routes", prop: "routes" }
            ]
        }
    }
};
