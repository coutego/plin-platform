#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// Parse arguments
const args = process.argv.slice(2);
const flags = args.filter(a => a.startsWith('--'));
const positional = args.filter(a => !a.startsWith('--'));

const appName = positional[0];
const withJsx = flags.includes('--jsx') || flags.includes('--typescript') || flags.includes('--tsx');

if (!appName) {
  console.error('Please provide an application name:');
  console.error('  npx @coutego/create-plin-app my-app');
  console.error('');
  console.error('Options:');
  console.error('  --jsx, --tsx, --typescript   Include JSX/TSX plugin infrastructure');
  process.exit(1);
}

const root = path.resolve(appName);
const srcDir = path.join(root, 'src', appName.replace(/-/g, '_'));

// Resolve the path to @coutego/plin-platform package (works both when run from repo and from npm)
function getPlinPlatformRoot() {
  // First check if we're running from the plin-platform repo itself
  const repoCheck = path.join(__dirname, '..', '..', '..', 'package.json');
  if (fs.existsSync(repoCheck)) {
    try {
      const pkg = JSON.parse(fs.readFileSync(repoCheck, 'utf8'));
      if (pkg.name === '@coutego/plin-platform') {
        return path.join(__dirname, '..', '..', '..');
      }
    } catch (e) {}
  }
  
  // Otherwise, try to find it in node_modules
  try {
    return path.dirname(require.resolve('@coutego/plin-platform/package.json'));
  } catch (e) {
    // Fallback: assume we're in the repo structure
    return path.join(__dirname, '..', '..', '..');
  }
}

console.log(`Creating a new PLIN app in ${root}...`);
if (withJsx) {
  console.log('Including JSX/TSX plugin infrastructure...');
}

async function main() {
  try {
    // 1. Create Directories
    if (!fs.existsSync(root)) {
      fs.mkdirSync(root);
    } else {
      console.error(`Directory ${appName} already exists.`);
      process.exit(1);
    }
    fs.mkdirSync(path.join(root, 'src'));
    fs.mkdirSync(srcDir);

    // Create JSX/TSX directories if requested
    if (withJsx) {
      fs.mkdirSync(path.join(root, 'plugins-src'));
      fs.mkdirSync(path.join(root, 'public', 'plugins'), { recursive: true });
    }

    // 2. Create package.json (npm dependency)
    const packageJson = {
      name: appName,
      version: '0.1.0',
      private: true,
      dependencies: {
        "@coutego/plin-platform": "^0.5.0",
        "nbb": "^1.3.205",
        "react": "^18.2.0"
      },
      scripts: {
        start: withJsx 
          ? "npm run build:plugins && node node_modules/@coutego/plin-platform/bin/generate-server-boot.js --run"
          : "node node_modules/@coutego/plin-platform/bin/generate-server-boot.js --run",
        "start:dev": "nbb -m plin-platform.server",
        build: "nbb -m plin-platform.build-single-file",
        "generate-boot": "node node_modules/@coutego/plin-platform/bin/generate-server-boot.js"
      }
    };

    // Add JSX/TSX scripts and dependencies
    if (withJsx) {
      packageJson.scripts["build:plugins"] = "node esbuild.plugins.js";
      packageJson.scripts["watch:plugins"] = "node esbuild.plugins.js --watch";
      packageJson.devDependencies = {
        "@types/react": "^18.2.0",
        "esbuild": "^0.20.0",
        "typescript": "^5.3.0"
      };
    }

    fs.writeFileSync(
      path.join(root, 'package.json'),
      JSON.stringify(packageJson, null, 2)
    );

    // 3. Create nbb.edn (references node_modules)
    const nbbEdn = `{:paths ["src" 
         "target"
         "node_modules/@coutego/plin-platform/src"
         "node_modules/@coutego/plin-platform/libs"]}`;

    fs.writeFileSync(path.join(root, 'nbb.edn'), nbbEdn);

    // 4. Create plin.edn (plugin manifest)
    let plinEdn = `[;; =============================================================================
 ;; ${appName} Plugins
 ;; =============================================================================

 ;; Main CLJS plugin
 {:id :${appName}
  :files ["src/${appName.replace(/-/g, '_')}/core.cljs"]
  :entry "${appName.replace(/-/g, '_')}.core"}`;

    // Add example TSX plugin entry if JSX enabled
    if (withJsx) {
      plinEdn += `

 ;; Example TSX plugin (compiled from plugins-src/)
 {:id :${appName}.example-tsx
  :type :js
  :envs [:browser]
  :files ["public/plugins/example-tsx-plugin.js"]}`;
    }

    plinEdn += `]
`;

    fs.writeFileSync(path.join(root, 'plin.edn'), plinEdn);

    // 5. Create Source File
    const coreCljs = `(ns ${appName.replace(/-/g, '_')}.core
  (:require [plin.core :as plin]
            [plinpt.i-application :as iapp]))

;; 1. Define a UI Component
(defn hello-page []
  [:div {:class "p-10"}
   [:h1 {:class "text-3xl font-bold text-blue-600"} "Hello! Welcome to ${appName}"]
   [:p {:class "mt-4 text-gray-600"} "Built with PLIN Platform."]
   [:p {:class "mt-2 text-sm text-gray-500"} "Edit src/${appName.replace(/-/g, '_')}/core.cljs to get started."]])

;; 2. Define the Plugin
(def plugin
  (plin/plugin
   {:doc "My Application Hello Plugin"
    :deps [iapp/plugin]
    
    :contributions
    {;; Register navigation item and route
     ::iapp/nav-items [{:id :hello
                        :label "Hello"
                        :description "Welcome page for ${appName}"
                        :route "/hello"
                        :icon [:svg {:class "h-5 w-5" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
                               [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" 
                                       :d "M7 8h10M7 12h4m1 8l-4-4H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-3l-4 4z"}]]
                        :component hello-page
                        :order 10}]}}))
`;

    fs.writeFileSync(path.join(srcDir, 'core.cljs'), coreCljs);

    // 6. Copy documentation files from plin-platform (always)
    const plinPlatformRoot = getPlinPlatformRoot();
    
    // Always copy README.org if it exists
    const readmeSource = path.join(plinPlatformRoot, 'README.org');
    if (fs.existsSync(readmeSource)) {
      fs.copyFileSync(readmeSource, path.join(root, 'README.org'));
      console.log('Copied README.org');
    }
    
    // Always copy AGENTS.md if it exists (for AI assistants)
    const agentsSource = path.join(plinPlatformRoot, 'AGENTS.md');
    if (fs.existsSync(agentsSource)) {
      fs.copyFileSync(agentsSource, path.join(root, 'AGENTS.md'));
      console.log('Copied AGENTS.md');
    }

    // 7. Create JSX/TSX infrastructure if requested
    if (withJsx) {
      // esbuild config
      const esbuildConfig = `/**
 * esbuild configuration for building JSX/TSX plugins
 * 
 * This script compiles all .tsx/.jsx files in plugins-src/ to public/plugins/
 * The output format is compatible with PLIN's JS plugin loader.
 * 
 * Usage:
 *   node esbuild.plugins.js          # Build once
 *   node esbuild.plugins.js --watch  # Watch mode
 */

const esbuild = require('esbuild');
const fs = require('fs');
const path = require('path');

const pluginsDir = './plugins-src';
const outDir = './public/plugins';

// Ensure output directory exists
if (!fs.existsSync(outDir)) {
  fs.mkdirSync(outDir, { recursive: true });
}

// Find all .tsx/.jsx/.ts files in plugins-src
const entries = fs.existsSync(pluginsDir) 
  ? fs.readdirSync(pluginsDir)
      .filter(f => /\\.(tsx?|jsx)$/.test(f) && !f.endsWith('.d.ts'))
      .map(f => path.join(pluginsDir, f))
  : [];

if (entries.length === 0) {
  console.log('No JSX/TSX plugins found in', pluginsDir);
  process.exit(0);
}

console.log('Building JSX/TSX plugins:', entries.map(e => path.basename(e)).join(', '));

// Custom plugin to transform the output for PLIN compatibility
const plinPlugin = {
  name: 'plin-transform',
  setup(build) {
    build.onEnd(result => {
      if (result.errors.length > 0) return;
      
      // Post-process each output file to add the return statement
      const outFiles = fs.readdirSync(outDir).filter(f => f.endsWith('.js'));
      
      for (const file of outFiles) {
        const filePath = path.join(outDir, file);
        let content = fs.readFileSync(filePath, 'utf8');
        
        // Check if we already processed this file
        if (!content.includes('return __PLIN_PLUGIN__')) {
          content += '\\nreturn __PLIN_PLUGIN__.default;\\n';
          fs.writeFileSync(filePath, content);
        }
      }
      
      console.log('✓ Plugins built successfully');
    });
  }
};

// Check for watch mode
const watchMode = process.argv.includes('--watch');

// Build configuration
const buildOptions = {
  entryPoints: entries,
  bundle: true,
  outdir: outDir,
  format: 'iife',
  globalName: '__PLIN_PLUGIN__',
  // For TypeScript/JSX
  loader: {
    '.tsx': 'tsx',
    '.ts': 'ts',
    '.jsx': 'jsx'
  },
  // Use the classic JSX transform that uses React.createElement
  // This works with the global React object
  jsx: 'transform',
  jsxFactory: 'React.createElement',
  jsxFragment: 'React.Fragment',
  // Generate readable output for debugging
  minify: false,
  sourcemap: false,
  // Target modern browsers
  target: ['es2020'],
  plugins: [plinPlugin],
  // Define process.env.NODE_ENV
  define: {
    'process.env.NODE_ENV': '"production"'
  },
  // Banner to inject React from global scope
  banner: {
    js: 'var React = window.React;'
  }
};

if (watchMode) {
  // Watch mode
  esbuild.context(buildOptions).then(ctx => {
    ctx.watch();
    console.log('Watching for changes in', pluginsDir, '...');
    console.log('Press Ctrl+C to stop.');
  }).catch((err) => {
    console.error('Watch setup failed:', err);
    process.exit(1);
  });
} else {
  // Single build
  esbuild.build(buildOptions).then(() => {
    console.log('Output written to', outDir);
  }).catch((err) => {
    console.error('Build failed:', err);
    process.exit(1);
  });
}
`;
      fs.writeFileSync(path.join(root, 'esbuild.plugins.js'), esbuildConfig);

      // tsconfig.json for plugins-src
      const tsconfig = {
        compilerOptions: {
          target: "ES2020",
          lib: ["ES2020", "DOM", "DOM.Iterable"],
          module: "ESNext",
          moduleResolution: "bundler",
          jsx: "react",
          strict: true,
          noEmit: true,
          isolatedModules: true,
          esModuleInterop: true,
          skipLibCheck: true,
          forceConsistentCasingInFileNames: true,
          resolveJsonModule: true
        },
        include: ["plugins-src/**/*.ts", "plugins-src/**/*.tsx"],
        exclude: ["node_modules"]
      };
      fs.writeFileSync(path.join(root, 'tsconfig.json'), JSON.stringify(tsconfig, null, 2));

      // Example TSX plugin - using classic JSX (no import React needed at runtime)
      const exampleTsx = `/**
 * Example TSX Plugin for ${appName}
 * 
 * This demonstrates how to write plugins using JSX/TSX syntax.
 * The file is compiled by esbuild and loaded by PLIN's JS plugin loader.
 * 
 * Note: React is available globally, so imports are for TypeScript types only.
 */

// React is available globally via window.React (injected by esbuild banner)
// These imports are for TypeScript type checking only
declare const React: typeof import('react');

const { useState, useEffect } = React;

// =============================================================================
// Components
// =============================================================================

interface CounterProps {
  initialValue?: number;
}

const Counter: React.FC<CounterProps> = ({ initialValue = 0 }) => {
  const [count, setCount] = useState(initialValue);
  
  return (
    <div className="flex items-center gap-4">
      <button 
        className="px-4 py-2 bg-blue-500 hover:bg-blue-600 text-white rounded-lg transition-colors"
        onClick={() => setCount(c => c - 1)}
      >
        -
      </button>
      <span className="text-2xl font-bold text-gray-800 w-16 text-center">
        {count}
      </span>
      <button 
        className="px-4 py-2 bg-blue-500 hover:bg-blue-600 text-white rounded-lg transition-colors"
        onClick={() => setCount(c => c + 1)}
      >
        +
      </button>
    </div>
  );
};

const ExamplePage: React.FC = () => {
  const [mounted, setMounted] = useState(false);
  
  useEffect(() => {
    setMounted(true);
  }, []);
  
  return (
    <div className="p-6 max-w-2xl mx-auto">
      <div className="bg-white rounded-xl shadow-lg p-8">
        <h1 className="text-3xl font-bold text-gray-900 mb-2">
          Hello from TSX! 
        </h1>
        <p className="text-gray-600 mb-6">
          This plugin is written in TypeScript with JSX syntax and compiled with esbuild.
          To modify this plugin, go to plugins-src/example-tsx-plugin.tsx
        </p>
        
        {/* Feature highlights */}
        <div className="grid gap-4 mb-8">
          <div className="p-4 bg-blue-50 rounded-lg border border-blue-200">
            <h3 className="font-semibold text-blue-800 mb-1">✨ Full TypeScript Support</h3>
            <p className="text-sm text-blue-700">
              Type checking, interfaces, and all TypeScript features work out of the box.
            </p>
          </div>
          
          <div className="p-4 bg-green-50 rounded-lg border border-green-200">
            <h3 className="font-semibold text-green-800 mb-1">⚛️ Modern React</h3>
            <p className="text-sm text-green-700">
              Use hooks, functional components, and the latest React patterns.
            </p>
          </div>
          
          <div className="p-4 bg-purple-50 rounded-lg border border-purple-200">
            <h3 className="font-semibold text-purple-800 mb-1">🚀 Fast Builds</h3>
            <p className="text-sm text-purple-700">
              esbuild compiles your plugins in milliseconds.
            </p>
          </div>
        </div>
        
        {/* Interactive demo */}
        <div className="border-t border-gray-200 pt-6">
          <h2 className="text-lg font-semibold text-gray-800 mb-4">
            Interactive Counter Demo
          </h2>
          <Counter initialValue={0} />
        </div>
        
        {/* Mount status */}
        <div className="mt-6 text-sm text-gray-500">
          Component mounted: {mounted ? '✅ Yes' : '⏳ Loading...'}
        </div>
      </div>
    </div>
  );
};

// =============================================================================
// Icon (Hiccup format - NOT JSX)
// =============================================================================

// Icons must be in Hiccup format (arrays) for the sidebar
// This is because they're rendered by Reagent, not React directly
const ExampleIcon = ["svg", {
  className: "w-5 h-5",
  fill: "none",
  stroke: "currentColor",
  viewBox: "0 0 24 24",
  xmlns: "http://www.w3.org/2000/svg"
}, ["path", {
  strokeLinecap: "round",
  strokeLinejoin: "round",
  strokeWidth: 2,
  d: "M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4"
}]];

// =============================================================================
// Plugin Definition (default export)
// =============================================================================

export default {
  doc: "Example TSX Plugin - demonstrates TypeScript/JSX plugin development",
  
  deps: ["plinpt.i-application"],
  
  contributions: {
    "plinpt.i-application/homepage": "/example-tsx",

    "plinpt.i-application/nav-items": [{
      id: "example-tsx",
      label: "TSX Example",
      description: "Example plugin written in TypeScript with JSX",
      route: "/example-tsx",
      icon: "icon",
      component: "page",
      order: 15
    }]
  },
  
  beans: {
    "page": {
      doc: "Example TSX page component",
      type: "react-component",
      value: ExamplePage
    },
    "icon": {
      doc: "Navigation icon as Hiccup data",
      type: "hiccup", 
      value: ExampleIcon
    }
  }
};
`;
      fs.writeFileSync(path.join(root, 'plugins-src', 'example-tsx-plugin.tsx'), exampleTsx);

      // Copy JS-specific documentation files
      const readmeJsSource = path.join(plinPlatformRoot, 'README-JS.org');
      if (fs.existsSync(readmeJsSource)) {
        fs.copyFileSync(readmeJsSource, path.join(root, 'README-JS.org'));
        console.log('Copied README-JS.org');
      }
      
      const agentsJsSource = path.join(plinPlatformRoot, 'AGENTS-JS.md');
      if (fs.existsSync(agentsJsSource)) {
        fs.copyFileSync(agentsJsSource, path.join(root, 'AGENTS-JS.md'));
        console.log('Copied AGENTS-JS.md');
      }

      // .gitignore entry for compiled plugins
      fs.writeFileSync(path.join(root, '.gitignore'), `node_modules/
target/
public/plugins/*.js
.nbb/
`);
    } else {
      // Basic .gitignore without JSX
      fs.writeFileSync(path.join(root, '.gitignore'), `node_modules/
target/
.nbb/
`);
    }

    // 8. Install Dependencies
    console.log('Installing dependencies (this might take a minute)...');
    try {
      execSync('npm install', { cwd: root, stdio: 'inherit' });
    } catch (e) {
      console.error('Failed to install dependencies.');
      process.exit(1);
    }

    console.log('');
    console.log('Success! Created', appName, 'at', root);
    console.log('Inside that directory, you can run:');
    console.log('');
    console.log('  npm start');
    console.log('    Starts the development server.');
    console.log('');
    if (withJsx) {
      console.log('  npm run build:plugins');
      console.log('    Compiles JSX/TSX plugins to public/plugins/');
      console.log('');
      console.log('  npm run watch:plugins');
      console.log('    Watches and recompiles JSX/TSX plugins on change.');
      console.log('');
    }
    console.log('  npm run build');
    console.log('    Bundles the app into a single HTML file.');
    console.log('');
    console.log('Documentation:');
    console.log('  README.org     - Main platform documentation');
    console.log('  AGENTS.md      - Quick reference for AI coding assistants');
    if (withJsx) {
      console.log('  README-JS.org  - JavaScript/TypeScript plugin guide');
      console.log('  AGENTS-JS.md   - JS plugin reference for AI assistants');
    }
    console.log('');
    console.log('We suggest that you begin by typing:');
    console.log('');
    console.log(`  cd ${appName}`);
    if (withJsx) {
      console.log('  npm run build:plugins  # Build TSX plugins first');
    }
    console.log('  npm start');
    console.log('');

  } catch (error) {
    console.error('Unexpected error:', error);
    process.exit(1);
  }
}

main();
