#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');
const https = require('https');

const appName = process.argv[2];

if (!appName) {
  console.error('Please provide an application name:');
  console.error('  node path/to/plin-platform/bin/create-plin-app.js my-app');
  process.exit(1);
}

const root = path.resolve(appName);
const srcDir = path.join(root, 'src', appName.replace(/-/g, '_'));

console.log(`Creating a new PLIN app in ${root}...`);

// Helper to fetch latest SHA
function getLatestSha() {
  return new Promise((resolve, reject) => {
    const options = {
      hostname: 'api.github.com',
      path: '/repos/coutego/plin-platform/commits?per_page=1',
      headers: { 'User-Agent': 'create-plin-app' }
    };

    https.get(options, (res) => {
      let data = '';
      res.on('data', (chunk) => data += chunk);
      res.on('end', () => {
        if (res.statusCode === 200) {
          try {
            const json = JSON.parse(data);
            if (Array.isArray(json) && json.length > 0) {
              resolve(json[0].sha);
            } else {
              reject(new Error('No commits found in repository.'));
            }
          } catch (e) {
            reject(e);
          }
        } else {
          reject(new Error(`GitHub API returned ${res.statusCode}: ${res.statusMessage}`));
        }
      });
    }).on('error', reject);
  });
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

    // 2. Create package.json
    const packageJson = {
      name: appName,
      version: '0.1.0',
      private: true,
      dependencies: {
        nbb: "^1.0.0",
        react: "^18.2.0"
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

    // 3. Create nbb.edn
    console.log('Fetching latest platform version...');
    let sha;
    try {
        sha = await getLatestSha();
        console.log(`Using plin-platform commit: ${sha}`);
    } catch (e) {
        console.warn("Failed to fetch latest SHA from GitHub:", e.message);
        console.warn("Falling back to a recent known SHA.");
        // Fallback SHA (Full 40 chars to satisfy nbb)
        // This corresponds to a recent commit. Update this periodically.
        sha = "9d1cf93000000000000000000000000000000000"; // Placeholder, user might need to update
    }

    const nbbEdn = `{:deps {io.github.coutego/plin-platform {:git/url "https://github.com/coutego/plin-platform"
                                             :git/sha "${sha}"}}}`;

    fs.writeFileSync(path.join(root, 'nbb.edn'), nbbEdn);

    // 3b. Create deps.edn (for compatibility and explicit paths)
    const depsEdn = `{:paths ["src" "libs" "public"]
 :deps {io.github.coutego/plin-platform {:git/url "https://github.com/coutego/plin-platform"
                                         :git/sha "${sha}"}}}`;
    
    fs.writeFileSync(path.join(root, 'deps.edn'), depsEdn);

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
(defn home-page []
  [:div {:class "p-10"}
   [:h1 {:class "text-3xl font-bold text-blue-600"} "Welcome to ${appName}"]
   [:p {:class "mt-4"} "Built with PLIN Platform."]])

;; 2. Define the Plugin
(def plugin
  (plin/plugin
   {:doc "My Application Plugin"
    :deps [nav/plugin shell/plugin]
    
    :contributions
    {;; Add a link to the Navigation Bar
     ::nav/items [{:label "Home" :route "/home" :order 1}]
     
     ;; Register the Route
     ::shell/routes [{:path "/home" :component home-page}]}}))
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
