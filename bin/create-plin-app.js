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
        "plin-platform": "^0.1.0",
        "nbb": "^1.3.205",
        "react": "^18.2.0"
      },
      scripts: {
        start: "nbb -m plin-platform.server",
        build: "nbb -m plin-platform.build-single-file"
      }
    };

    fs.writeFileSync(
      path.join(root, 'package.json'),
      JSON.stringify(packageJson, null, 2)
    );

    // 3. Create nbb.edn (references node_modules)
    const nbbEdn = `{:paths ["src" 
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
            [plinpt.i-nav-bar :as nav]
            [plinpt.i-app-shell :as shell]))

;; 1. Define a UI Component
(defn hello-page []
  [:div {:class "p-10"}
   [:h1 {:class "text-3xl font-bold text-blue-600"} "Hello! Welcome to ${appName}"]
   [:p {:class "mt-4"} "Built with PLIN Platform."]])

;; 2. Define the Plugin
(def plugin
  (plin/plugin
   {:doc "My Application Hello Plugin"
    :deps [nav/plugin shell/plugin]
    
    :contributions
    {;; Add a link to the Navigation Bar
     ::nav/items [{:label "Hello" :route "/hello" :order 1}]
     
     ;; Register the Route
     ::shell/routes [{:path "/hello" :component hello-page}]}}))
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
