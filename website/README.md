# Carbide website

Four-page website for Carbide, the Kotlin service infrastructure framework. Product metadata and
repository links are centralized in `app/site-config.ts`.

## Local development

Requires Node.js 22.13 or newer.

```shell
npm install
npm run dev
```

The development server runs at <http://localhost:3000>.

## Verification

```shell
npm test
```

This creates the production build and verifies all four server-rendered pages. Hosting and public URLs
remain deliberately unconfigured until the repository location and publishing setup are final.
