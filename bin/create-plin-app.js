#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const appName = process.argv[2];

if (!appName) {
  console.error('Please provide an application name:');
  console.error('  npx create-plin-app my-app');
  process.exit(1);
}

const root = path.resolve(appName);
const srcDir = path.join(root, 'src', appName.replace(/-/g, '_'));

console.log(`Creating a new PLIN app in ${root}...`);

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

    // 2. Create package.json (npm dependency)
    const packageJson = {
      name: appName,
      version: '0.1.0',
      private: true,
      dependencies: {
        "plin-platform": "^0.3.2",
        "nbb": "^1.3.205",
        "react": "^18.2.0"
      },
      scripts: {
        start: "node node_modules/plin-platform/bin/generate-server-boot.js --run",
        "start:dev": "nbb -m plin-platform.server",
        build: "nbb -m plin-platform.build-single-file",
        "generate-boot": "node node_modules/plin-platform/bin/generate-server-boot.js"
      }
    };

    fs.writeFileSync(
      path.join(root, 'package.json'),
      JSON.stringify(packageJson, null, 2)
    );

    // 3. Create nbb.edn (references node_modules)
    const nbbEdn = `{:paths ["src" 
         "target"
         "node_modules/plin-platform/src"
         "node_modules/plin-platform/libs"]}`;

    fs.writeFileSync(path.join(root, 'nbb.edn'), nbbEdn);

    // 4. Create manifest.edn
    const manifestEdn = `[{:id :${appName}
  :tags [:ui]
  :files ["src/${appName.replace(/-/g, '_')}/core.cljs"]
  :entry "${appName}.core"}]`;

    fs.writeFileSync(path.join(root, 'manifest.edn'), manifestEdn);

    // 5. Create Source File
    const coreCljs = `(ns ${appName}.core
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
                        :order 1}]}}))
`;

    fs.writeFileSync(path.join(srcDir, 'core.cljs'), coreCljs);

    // 6. Install Dependencies
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
    console.log('  npm run build');
    console.log('    Bundles the app into a single HTML file.');
    console.log('');
    console.log('We suggest that you begin by typing:');
    console.log('');
    console.log(`  cd ${appName}`);
    console.log('  npm start');
    console.log('');

  } catch (error) {
    console.error('Unexpected error:', error);
    process.exit(1);
  }
}

main();
