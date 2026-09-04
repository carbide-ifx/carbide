import { context } from "esbuild";
import { watch } from "node:fs";
import { copyFile, mkdir } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const outputDirectory = resolve(here, "dist");
const sourceHtml = resolve(here, "index.html");
const outputHtml = resolve(outputDirectory, "index.html");

await mkdir(outputDirectory, { recursive: true });
await copyFile(sourceHtml, outputHtml);
watch(sourceHtml, () => {
  void copyFile(sourceHtml, outputHtml).catch((error) => {
    console.error(`Failed to copy index.html: ${error.message}`);
  });
});

const build = await context({
  entryPoints: [resolve(here, "src/main.ts")],
  outfile: resolve(outputDirectory, "test-ui.js"),
  bundle: true,
  format: "iife",
  platform: "browser",
  sourcemap: "inline",
  target: "es2022",
});

await build.watch();
console.log("Watching the iFX Service Explorer and writing its web assets to dist/.");
