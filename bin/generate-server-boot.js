#!/usr/bin/env node

/**
 * Generate Server Boot Script
 * 
 * This script reads the manifest (plin.edn) files and generates a server_boot.cljs
 * file with hardcoded requires for all plugins that should be loaded in server mode.
 * 
 * This is necessary because nbb cannot dynamically require namespaces at runtime.
 * 
 * New filtering logic:
 * - :envs - Optional. Absent means "all environments". Load if :node is in envs.
 * - :modes - Optional. Absent means "all modes". For server, we don't filter by mode.
 * - :enabled - Optional. If false, plugin starts disabled but is still loaded.
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const userRoot = process.cwd();
const currentEnv = 'node';

// --- EDN Parsing (Simple) ---

function parseEdnManual(ednString) {
  const results = [];
  
  // Remove comments
  const lines = ednString.split('\n')
    .filter(line => !line.trim().startsWith(';;'))
    .join('\n');
  
  // Match each map in the vector
  const mapRegex = /\{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}/g;
  const maps = lines.match(mapRegex) || [];
  
  for (const mapStr of maps) {
    const item = {};
    
    // Extract :id
    const idMatch = mapStr.match(/:id\s+:([^\s\]]+)/);
    if (idMatch) item.id = idMatch[1];
    
    // Extract :envs (new)
    const envsMatch = mapStr.match(/:envs\s+\[([^\]]*)\]/);
    if (envsMatch) {
      item.envs = envsMatch[1].split(/\s+/)
        .filter(t => t.startsWith(':'))
        .map(t => t.substring(1));
    }
    
    // Extract :modes (new)
    const modesMatch = mapStr.match(/:modes\s+\[([^\]]*)\]/);
    if (modesMatch) {
      item.modes = modesMatch[1].split(/\s+/)
        .filter(t => t.startsWith(':'))
        .map(t => t.substring(1));
    }
    
    // Extract :enabled (new)
    const enabledMatch = mapStr.match(/:enabled\s+(true|false)/);
    if (enabledMatch) {
      item.enabled = enabledMatch[1] === 'true';
    }
    
    // Extract :files
    const filesMatch = mapStr.match(/:files\s+\[([^\]]*)\]/);
    if (filesMatch) {
      item.files = filesMatch[1].match(/"[^"]+"/g)?.map(f => f.replace(/"/g, '')) || [];
    }
    
    // Extract :entry
    const entryMatch = mapStr.match(/:entry\s+"([^"]+)"/);
    if (entryMatch) item.entry = entryMatch[1];
    
    // Extract :type
    const typeMatch = mapStr.match(/:type\s+:([^\s\]]+)/);
    if (typeMatch) item.type = typeMatch[1];
    
    // Extract :config (for opt-out detection)
    if (mapStr.includes(':config')) {
      item.config = {};
      if (mapStr.includes(':include-platform?') && mapStr.includes('false')) {
        item.config['include-platform?'] = false;
      }
    }
    
    if (item.id || item.entry || item.config) {
      results.push(item);
    }
  }
  
  return results;
}

function readEdnFile(filePath) {
  if (fs.existsSync(filePath)) {
    return fs.readFileSync(filePath, 'utf8');
  }
  return null;
}

// --- Path Resolution ---

function findUserManifestPath() {
  const candidates = [
    path.join(userRoot, 'plin.edn'),
    path.join(userRoot, 'manifest.edn'),
    path.join(userRoot, 'public/plin.edn'),
    path.join(userRoot, 'public/manifest.edn')
  ];
  return candidates.find(p => fs.existsSync(p));
}

function findPlatformManifestPath() {
  const repoSrcPath = path.join(userRoot, 'src/plinpt/plin.edn');
  if (fs.existsSync(repoSrcPath)) return repoSrcPath;
  
  const repoLibsPath = path.join(userRoot, 'libs/plinpt/plin.edn');
  if (fs.existsSync(repoLibsPath)) return repoLibsPath;
  
  const depSrcPath = path.join(userRoot, 'node_modules/plin-platform/src/plinpt/plin.edn');
  if (fs.existsSync(depSrcPath)) return depSrcPath;
  
  const depLibsPath = path.join(userRoot, 'node_modules/plin-platform/libs/plinpt/plin.edn');
  if (fs.existsSync(depLibsPath)) return depLibsPath;
  
  return null;
}

// --- Manifest Processing ---

function getManifest() {
  const userManifestPath = findUserManifestPath();
  const userManifestEdn = userManifestPath ? readEdnFile(userManifestPath) : null;
  const userManifest = userManifestEdn ? parseEdnManual(userManifestEdn) : [];
  
  const configEntry = userManifest.find(item => item.config);
  const includePlatform = !(configEntry?.config?.['include-platform?'] === false);
  
  if (userManifestPath) {
    console.log('User manifest:', userManifestPath);
  }
  
  if (includePlatform) {
    const platformPath = findPlatformManifestPath();
    if (platformPath) {
      console.log('Platform manifest:', platformPath);
      const platformEdn = readEdnFile(platformPath);
      const platformManifest = platformEdn ? parseEdnManual(platformEdn) : [];
      return [...platformManifest, ...userManifest];
    } else {
      console.warn('Warning: Platform manifest not found');
    }
  }
  
  return userManifest;
}

/**
 * New filtering logic:
 * Load if: (envs absent OR currentEnv in envs)
 * Note: For server, we don't filter by modes - server loads all node-compatible plugins
 */
function shouldLoadPlugin(item, env) {
  // Skip config entries
  if (item.config) return false;
  
  // Only include CLJS plugins
  const type = item.type || 'cljs';
  if (type !== 'cljs') return false;
  
  // Check envs: absent means "all environments"
  const envs = item.envs;
  const envMatch = !envs || envs.length === 0 || envs.includes(env);
  
  return envMatch;
}

function filterPlugins(manifest, env) {
  return manifest.filter(item => shouldLoadPlugin(item, env));
}

function getInitiallyDisabledIds(manifest) {
  return manifest
    .filter(item => item.enabled === false && item.id)
    .map(item => item.id);
}

function entryToRequire(entry) {
  const alias = entry.replace(/[.-]/g, '_').replace(/\//g, '_');
  return { ns: entry, alias };
}

// --- Code Generation ---

function generateServerBoot(plugins, disabledIds) {
  const requires = plugins
    .filter(p => p.entry)
    .map(p => entryToRequire(p.entry));
  
  const requireStatements = requires
    .map(r => `            [${r.ns} :as ${r.alias}]`)
    .join('\n');
  
  const pluginRefs = requires
    .map(r => `   ${r.alias}/plugin`)
    .join('\n');
  
  const disabledIdsClj = disabledIds.length > 0
    ? `#{${disabledIds.map(id => `:${id}`).join(' ')}}`
    : '#{}';
  
  return `(ns plin-platform.server-boot-generated
  "Auto-generated server bootstrap file.
   
   This file is generated by bin/generate-server-boot.js based on the manifest.
   DO NOT EDIT MANUALLY - changes will be overwritten.
   
   Generated at: ${new Date().toISOString()}"
  (:require [plin.boot :as boot]
            [clojure.string :as str]
            ["path" :as path]
            
            ;; Auto-generated requires from manifest
${requireStatements}))

;; --- Path Resolution ---

(def user-root (js/process.cwd))

(def framework-root
  (let [cwd (js/process.cwd)]
    (if (str/includes? cwd "node_modules")
      (path/resolve cwd "../..")
      cwd)))

;; --- Plugin List ---
;; Auto-generated from manifest (plugins matching env=node)

(def server-plugins
  [
${pluginRefs}])

;; --- Initially Disabled IDs ---
;; Plugins with :enabled false in manifest

(def initially-disabled-ids ${disabledIdsClj})

;; --- Main ---

(defn -main [& args]
  (println "PLIN Server Bootstrap (Generated)")
  (println "Framework root:" framework-root)
  (println "User root:" user-root)
  (println "Loading" (count server-plugins) "plugins...")
  (when (seq initially-disabled-ids)
    (println "Initially disabled:" initially-disabled-ids))
  
  (boot/bootstrap! server-plugins initially-disabled-ids))

(-main)
`;
}

// --- Main ---

function main() {
  console.log('Generating server boot script...');
  console.log('Environment:', currentEnv);
  console.log('');
  
  const manifest = getManifest();
  const serverPlugins = filterPlugins(manifest, currentEnv);
  const disabledIds = getInitiallyDisabledIds(manifest);
  
  console.log('');
  console.log('Found', serverPlugins.length, 'plugins for env=' + currentEnv + ':');
  serverPlugins.forEach(p => {
    const disabled = p.enabled === false ? ' (disabled)' : '';
    console.log('  -', p.entry || p.id, disabled);
  });
  
  if (disabledIds.length > 0) {
    console.log('');
    console.log('Initially disabled:', disabledIds);
  }
  console.log('');
  
  const bootCode = generateServerBoot(serverPlugins, disabledIds);
  
  const targetDir = path.join(userRoot, 'target');
  if (!fs.existsSync(targetDir)) {
    fs.mkdirSync(targetDir, { recursive: true });
  }
  
  const outputPath = path.join(targetDir, 'server_boot_generated.cljs');
  fs.writeFileSync(outputPath, bootCode);
  
  console.log('Generated:', outputPath);
  
  return outputPath;
}

if (require.main === module) {
  const outputPath = main();
  
  const shouldRun = process.argv.includes('--run');
  if (shouldRun) {
    console.log('');
    console.log('Starting server...');
    console.log('');
    
    try {
      execSync(`nbb ${outputPath}`, {
        cwd: userRoot,
        stdio: 'inherit'
      });
    } catch (e) {
      process.exit(e.status || 1);
    }
  }
}

module.exports = { main, generateServerBoot, getManifest, filterPlugins };
