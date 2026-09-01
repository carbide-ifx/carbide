import type { Metadata } from "next";
import { gettingStarted, routePages } from "../content";
import { SiteFooter, SiteHeader } from "../site-chrome";
import { siteConfig } from "../site-config";

const page = routePages.start;
const title = `${page.title} — ${siteConfig.productName}`;

export const metadata: Metadata = {
  title,
  description: page.description,
  openGraph: { title, description: page.description, images: [] },
  twitter: { title, description: page.description, images: [] },
};

export default function GetStartedPage() {
  return (
    <main>
      <SiteHeader current="start" />

      <section className="start route-section" id="start">
        <div className="start-copy">
          <p className="section-index">05 / START SMALL</p>
          <h1>{page.heading}</h1>
          <p>Register the implementation once. The generated descriptor connects both sides while the runtime takes responsibility for delivery and operations.</p>
          <div className="start-facts">
            <span>JVM + macOS ARM64</span>
            <span>Kotlin Multiplatform</span>
            <span>Coroutines + Flow</span>
          </div>
        </div>
        <div className="start-code">
          <div className="code-card-head"><span>GreeterSystem.kt</span><span className="status"><i /> ready</span></div>
          <pre><code>{gettingStarted}</code></pre>
        </div>
      </section>

      <nav className="next-page" aria-label="Continue reading">
        <span>Explore</span>
        <a href="/infrastructure"><strong>What the framework supplies</strong><i aria-hidden="true">→</i></a>
      </nav>

      <SiteFooter />
    </main>
  );
}
