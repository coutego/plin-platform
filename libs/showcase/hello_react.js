console.log("Executing hello_react.js");

// A simple React component
const HelloReact = () => {
    const [count, setCount] = React.useState(0);
    
    return React.createElement(
        "div",
        { className: "p-10 bg-white rounded-xl shadow-lg max-w-2xl mx-auto mt-10" },
        React.createElement("h1", { className: "text-3xl font-bold text-blue-600 mb-4" }, "Hello from React & JS!"),
        React.createElement("p", { className: "text-gray-600 mb-6" }, 
            "This component is written in plain JavaScript using React, loaded dynamically into the ClojureScript PLIN architecture."
        ),
        React.createElement("div", { className: "flex items-center gap-4" },
            React.createElement("button", {
                className: "px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 transition-colors",
                onClick: () => setCount(c => c + 1)
            }, "Count is: " + count),
            React.createElement("span", { className: "text-sm text-gray-400" }, "Click me!")
        )
    );
};

const ReactIcon = () => {
    return React.createElement("svg", { className: "w-6 h-6", fill: "none", stroke: "currentColor", viewBox: "0 0 24 24" },
        React.createElement("path", { strokeLinecap: "round", strokeLinejoin: "round", strokeWidth: 2, d: "M14.828 14.828a4 4 0 01-5.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" })
    );
};

// Define plugin
const pluginDef = {
    doc: "A sample plugin written in JS",
    deps: ["plinpt.i-app-shell", "plinpt.i-devtools"],
    
    contributions: {
        "plinpt.i-devtools/items": [
            { 
                title: "JS React Demo",
                description: "A sample React component loaded from JS.",
                icon: "react-icon",
                href: "/hello-react",
                "color-class": "bg-blue-600",
                order: 10
            }
        ],
        "plinpt.i-app-shell/routes": [
            { path: "/hello-react", component: "hello-page" }
        ]
    },
    
    beans: {
        "hello-page": {
            type: "react-component",
            doc: "The Hello React page component",
            value: HelloReact
        },
        "react-icon": {
            type: "react-component",
            doc: "Icon for the menu",
            value: ReactIcon
        }
    }
};

console.log("Returning plugin definition from hello_react.js", pluginDef);
return pluginDef;
