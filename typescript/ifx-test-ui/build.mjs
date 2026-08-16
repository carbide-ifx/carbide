import { build } from "esbuild";
import { copyFile, mkdir } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const outputDirectory = resolve(here, "dist");

await mkdir(outputDirectory, { recursive: true });
await Promise.all([
  copyFile(resolve(here, "index.html"), resolve(outputDirectory, "index.html")),
  build({
    entryPoints: [resolve(here, "src/main.ts")],
    outfile: resolve(outputDirectory, "test-ui.js"),
    bundle: true,
    format: "iife",
    minify: true,
    platform: "browser",
    target: "es2022",
  }),
]);
