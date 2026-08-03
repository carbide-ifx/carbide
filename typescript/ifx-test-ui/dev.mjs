import { context } from "esbuild";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const build = await context({
  entryPoints: [resolve(here, "src/main.ts")],
  outfile: resolve(here, "dist/test-ui.js"),
  bundle: true,
  format: "iife",
  platform: "browser",
  sourcemap: "inline",
  target: "es2022",
});

await build.watch();
console.log("Watching the iFX test UI. The host page reloads automatically after changes.");
