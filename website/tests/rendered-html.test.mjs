import assert from "node:assert/strict";
import test from "node:test";

async function render(path = "/") {
  const workerUrl = new URL("../dist/server/index.js", import.meta.url);
  workerUrl.searchParams.set("test", `${process.pid}-${Date.now()}-${path}`);
  const { default: worker } = await import(workerUrl.href);

  return worker.fetch(
    new Request(`http://localhost${path}`, { headers: { accept: "text/html" } }),
    { ASSETS: { fetch: async () => new Response("Not found", { status: 404 }) } },
    { waitUntil() {}, passThroughOnException() {} },
  );
}

async function htmlFor(path) {
  const response = await render(path);
  assert.equal(response.status, 200, `${path} should render successfully`);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);
  return response.text();
}

function metaContent(html, attribute, value) {
  const tags = html.match(/<meta\b[^>]*>/gi) ?? [];
  const tag = tags.find((candidate) => candidate.includes(`${attribute}="${value}"`));
  return tag?.match(/content="([^"]*)"/i)?.[1];
}

test("server-renders the home page as the four-page entry point", async () => {
  const html = await htmlFor("/");

  assert.match(html, /<title>Carbide — Kotlin service infrastructure<\/title>/i);
  assert.match(html, /<span>Carbide<\/span>/i);
  assert.equal(metaContent(html, "property", "og:title"), "Carbide — Kotlin service infrastructure");
  assert.equal(metaContent(html, "name", "twitter:title"), "Carbide — Kotlin service infrastructure");
  assert.match(html, /Write the/);
  assert.match(html, /A shorter path to the part you need/);
  assert.match(html, /href="\/infrastructure"/);
  assert.match(html, /href="\/architecture"/);
  assert.match(html, /href="\/get-started"/);
  assert.match(html, /\/og\.png/);
  assert.doesNotMatch(html, /The infrastructure around your services/);
  assert.doesNotMatch(html, /codex-preview|SkeletonPreview|react-loading-skeleton/);
});

const detailPages = [
  {
    path: "/infrastructure",
    title: "Infrastructure — Carbide",
    heading: "The infrastructure around your services",
    current: "Infrastructure",
    description: "Hosting, transport, generated clients, call policies, observability, and public delivery—supplied around the business code.",
  },
  {
    path: "/architecture",
    title: "Architecture — Carbide",
    heading: "One boundary",
    current: "Architecture",
    description: "See how pure Kotlin contracts connect to compile-time generation and a composable runtime.",
  },
  {
    path: "/get-started",
    title: "Get started — Carbide",
    heading: "One interface. One implementation",
    current: "Get started",
    description: "Register a service once, then use the generated descriptor to connect both sides.",
  },
];

for (const detail of detailPages) {
  test(`server-renders ${detail.path} with route-specific content and metadata`, async () => {
    const html = await htmlFor(detail.path);

    assert.match(html, new RegExp(`<title>${detail.title}<\\/title>`, "i"));
    assert.match(html, new RegExp(detail.heading.replaceAll(".", "\\."), "i"));
    assert.match(html, new RegExp(`aria-current="page"[^>]*>${detail.current}`, "i"));
    assert.equal(metaContent(html, "name", "description"), detail.description);
    assert.equal(metaContent(html, "property", "og:title"), detail.title);
    assert.equal(metaContent(html, "property", "og:description"), detail.description);
    assert.equal(metaContent(html, "name", "twitter:title"), detail.title);
    assert.equal(metaContent(html, "name", "twitter:description"), detail.description);
    assert.doesNotMatch(html, /\/og\.png/);
    assert.doesNotMatch(html, /codex-preview|SkeletonPreview|react-loading-skeleton/);
  });
}
