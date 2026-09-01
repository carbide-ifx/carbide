import type { Metadata } from "next";
import { avoidedWork, capabilities, routePages } from "../content";
import { SiteFooter, SiteHeader } from "../site-chrome";
import { siteConfig } from "../site-config";

const page = routePages.infrastructure;
const title = `${page.title} — ${siteConfig.productName}`;

export const metadata: Metadata = {
  title,
  description: page.description,
  openGraph: { title, description: page.description, images: [] },
  twitter: { title, description: page.description, images: [] },
};

export default function InfrastructurePage() {
  return (
    <main>
      <SiteHeader current="infrastructure" />

      <section className="tooling route-section" id="tooling">
        <div className="tooling-title">
          <p className="section-index">02 / INCLUDED BY DEFAULT</p>
          <div>
            <h1>{page.heading}</h1>
            <p>Implement the business behaviour. The framework supplies the common delivery and operational capabilities that every service otherwise has to rebuild.</p>
          </div>
        </div>
        <div className="capability-grid">
          {capabilities.map(([title, copy], index) => (
            <article key={title}>
              <div className="capability-label"><span>{String(index + 1).padStart(2, "0")}</span><small>included</small></div>
              <h2>{title}</h2>
              <p>{copy}</p>
            </article>
          ))}
        </div>
        <div className="avoided-work" aria-label="Boilerplate avoided by the framework">
          {avoidedWork.map((item) => <span key={item}>{item}</span>)}
        </div>
      </section>

      <nav className="next-page" aria-label="Continue reading">
        <span>Next</span>
        <a href="/architecture"><strong>Architecture</strong><i aria-hidden="true">→</i></a>
      </nav>

      <SiteFooter />
    </main>
  );
}
