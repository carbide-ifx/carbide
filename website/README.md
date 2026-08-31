# Framework website preview

Name-neutral four-page website for the Kotlin service framework. Product naming and repository links
are centralized in `app/site-config.ts`; the current fallback label is intentionally descriptive.

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
remain deliberately unconfigured until the project name and repository location are final.
