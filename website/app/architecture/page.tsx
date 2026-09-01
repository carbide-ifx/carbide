import type { Metadata } from "next";
import { planes, routePages } from "../content";
import { SiteFooter, SiteHeader } from "../site-chrome";
import { siteConfig } from "../site-config";

const page = routePages.architecture;
const title = `${page.title} — ${siteConfig.productName}`;

export const metadata: Metadata = {
  title,
  description: page.description,
  openGraph: { title, description: page.description, images: [] },
  twitter: { title, description: page.description, images: [] },
};

export default function ArchitecturePage() {
  return (
    <main>
      <SiteHeader current="architecture" />

      <section className="model-intro route-section" id="model">
        <div className="section-heading">
          <p className="section-index">03 / THE MODEL</p>
          <h1>One boundary.<br />Three planes.</h1>
          <p>Your domain stays in the contract and implementation. Generated code connects it to build-time tooling and a composable runtime.</p>
        </div>
        <div className="plane-grid">
          {planes.map((plane) => (
            <article className="plane-card" key={plane.title}>
              <div className="plane-card-head"><span>{plane.number}</span><span>{plane.kicker}</span></div>
              <h2>{plane.title}</h2>
              <p>{plane.copy}</p>
              <ul>{plane.tags.map((tag) => <li key={tag}>{tag}</li>)}</ul>
            </article>
          ))}
        </div>
      </section>

      <section className="runtime" id="runtime">
        <div className="section-heading section-heading-light">
          <p className="section-index">04 / THE CALL PATH</p>
          <h2>Your code at<br />both ends.</h2>
          <p>The generated and operational machinery owns everything in between. Protocol and policy remain composition choices, not business concerns.</p>
        </div>

        <div className="call-path" aria-label="Call flow from application code to service implementation">
          <div className="call-node call-node-emphasis"><span>01</span><strong>Caller</strong><small>your code</small></div>
          <i aria-hidden="true">→</i>
          <div className="call-node"><span>02</span><strong>Proxy</strong><small>generated</small></div>
          <i aria-hidden="true">→</i>
          <div className="call-node"><span>03</span><strong>Policies</strong><small>interceptors</small></div>
          <i aria-hidden="true">→</i>
          <div className="call-node"><span>04</span><strong>Protocol</strong><small>RSocket / JSON-RPC</small></div>
          <i aria-hidden="true">→</i>
          <div className="call-node call-node-emphasis"><span>05</span><strong>Service</strong><small>your business code</small></div>
        </div>

        <div className="runtime-notes">
          <article><span>Three interaction shapes</span><p>Fire-and-forget, request/response, and request stream are derived directly from the Kotlin signature.</p></article>
          <article><span>One pivot abstraction</span><p><code>IBinding</code> is the common seam for proxies, transports, gateways, interceptors, and generated server dispatch.</p></article>
        </div>
      </section>

      <nav className="next-page next-page-dark" aria-label="Continue reading">
        <span>Next</span>
        <a href="/get-started"><strong>Get started</strong><i aria-hidden="true">→</i></a>
      </nav>

      <SiteFooter />
    </main>
  );
}
