# @coutego/create-plin-app

Create a new PLIN Platform application with a single command.

## Usage

```bash
npx @coutego/create-plin-app my-app
```

### With JSX/TSX Support

```bash
npx @coutego/create-plin-app my-app --jsx
```

## Options

- `--jsx`, `--tsx`, `--typescript` - Include JSX/TSX plugin infrastructure with esbuild

## What's Created

The command creates a new directory with:

- `package.json` - npm configuration with @coutego/plin-platform dependency
- `nbb.edn` - ClojureScript runtime configuration
- `plin.edn` - Plugin manifest
- `src/` - Source directory with a sample plugin
- `.gitignore` - Git ignore file

With `--jsx` flag, additionally:

- `plugins-src/` - Directory for TSX/JSX plugins
- `public/plugins/` - Compiled plugin output
- `esbuild.plugins.js` - Build configuration
- `tsconfig.json` - TypeScript configuration
- Example TSX plugin

## After Creation

```bash
cd my-app
npm start
```

Open http://localhost:8000 to see your app.

## Documentation

- [PLIN Platform Documentation](https://github.com/coutego/plin-platform)
- [JavaScript Plugin Guide](https://github.com/coutego/plin-platform/blob/main/README-JS.org)

## License

MIT
