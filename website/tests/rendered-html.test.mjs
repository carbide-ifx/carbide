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

test("server-renders the home page as the four-page entry point", async () => {
  const html = await htmlFor("/");

  assert.match(html, /<title>Write the business code\. Get the infrastructure\.<\/title>/i);
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
    title: "Infrastructure — Kotlin service infrastructure",
    heading: "The infrastructure around your services",
    current: "Infrastructure",
  },
  {
    path: "/architecture",
    title: "Architecture — Kotlin service infrastructure",
    heading: "One boundary",
    current: "Architecture",
  },
  {
    path: "/get-started",
    title: "Get started — Kotlin service infrastructure",
    heading: "One interface. One implementation",
    current: "Get started",
  },
];

for (const detail of detailPages) {
  test(`server-renders ${detail.path} with route-specific content and metadata`, async () => {
    const html = await htmlFor(detail.path);

    assert.match(html, new RegExp(`<title>${detail.title}<\\/title>`, "i"));
    assert.match(html, new RegExp(detail.heading.replaceAll(".", "\\."), "i"));
    assert.match(html, new RegExp(`aria-current="page"[^>]*>${detail.current}`, "i"));
    assert.doesNotMatch(html, /\/og\.png/);
    assert.doesNotMatch(html, /codex-preview|SkeletonPreview|react-loading-skeleton/);
  });
}
