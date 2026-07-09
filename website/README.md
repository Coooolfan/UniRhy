# unirhy-site

This template should help get you started developing with Vue 3 in Vite.

## Recommended IDE Setup

[VS Code](https://code.visualstudio.com/) + [Vue (Official)](https://marketplace.visualstudio.com/items?itemName=Vue.volar) (and disable Vetur).

## Recommended Browser Setup

- Chromium-based browsers (Chrome, Edge, Brave, etc.):
  - [Vue.js devtools](https://chromewebstore.google.com/detail/vuejs-devtools/nhdogjmejiglipccpnnnanhbledajbpd)
  - [Turn on Custom Object Formatter in Chrome DevTools](http://bit.ly/object-formatters)
- Firefox:
  - [Vue.js devtools](https://addons.mozilla.org/en-US/firefox/addon/vue-js-devtools/)
  - [Turn on Custom Object Formatter in Firefox DevTools](https://fxdx.dev/firefox-devtools-custom-object-formatters/)

## Type Support for `.vue` Imports in TS

TypeScript cannot handle type information for `.vue` imports by default. This project uses `oxlint --type-check` for CLI-level type diagnostics and [Volar](https://marketplace.visualstudio.com/items?itemName=Vue.volar) for editor-level Vue type awareness.

## Customize configuration

See [Vite Configuration Reference](https://vite.dev/config/).

## Project Setup

```sh
yarn
```

### Compile and Hot-Reload for Development

```sh
yarn dev
```

### Type-Check, Compile and Minify for Production

```sh
yarn build
```

### Lint and Type Check

```sh
yarn lint          # Run linter
yarn lint:fix      # Run linter with auto-fix
yarn typecheck     # Run type checker
yarn verify        # Run both lint and typecheck
```

### Format Code

```sh
yarn format        # Format code
yarn format:check  # Check code formatting
```

### Preview Production Build

```sh
yarn preview
```

### Individual Build Steps

```sh
yarn build:client      # Build client-side only
yarn build:server      # Build SSR server only
yarn build:prerender   # Run prerender only
```
