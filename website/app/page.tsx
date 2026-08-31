import { contract, outputs, routePages } from "./content";
import { SiteFooter, SiteHeader } from "./site-chrome";

export default function Home() {
  return (
    <main>
      <SiteHeader current="home" />

      <section className="hero" id="top">
        <div className="hero-copy">
          <p className="eyebrow"><span>01</span> Kotlin Multiplatform</p>
          <h1>Write the<br />business code.</h1>
          <p className="hero-lede">
            Implement a typed Kotlin service. Hosting, transport, clients, lifecycle,
            observability, and public API generation are handled around it.
          </p>
          <div className="hero-actions">
            <a className="button button-primary" href="/infrastructure">See what you get <span>→</span></a>
            <a className="text-link" href="/get-started">Start with a service <span>↗</span></a>
          </div>
        </div>

        <div className="contract-visual" aria-label="A Kotlin service implementation surrounded by infrastructure supplied by the framework">
          <div className="code-card">
            <div className="code-card-head">
              <span>ProductAccess.kt</span>
              <span className="status"><i /> you write this</span>
            </div>
            <pre><code>{contract}</code></pre>
          </div>
          <div className="generation-line" aria-hidden="true"><span>HANDLED AROUND IT</span></div>
          <ol className="output-list">
            {outputs.map(([number, label]) => (
              <li key={number}>
                <span>{number}</span>
                <strong>{label}</strong>
                <i aria-hidden="true">↗</i>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section className="principle-strip" aria-label="Core project principles">
        <p><span>You own</span> contracts</p>
        <p><span>You own</span> business behaviour</p>
        <p><span>You get</span> delivery infrastructure</p>
        <p><span>You get</span> operational tooling</p>
      </section>

      <section className="route-directory" aria-labelledby="route-directory-title">
        <div className="route-directory-heading">
          <p className="section-index">02 / EXPLORE</p>
          <h2 id="route-directory-title">A shorter path to the part you need.</h2>
        </div>
        <div className="route-card-grid">
          {Object.values(routePages).map((page, index) => (
            <a className="route-card" href={page.href} key={page.href}>
              <span>0{index + 1}</span>
              <h3>{page.title}</h3>
              <p>{page.description}</p>
              <i aria-hidden="true">→</i>
            </a>
          ))}
        </div>
      </section>

      <SiteFooter />
    </main>
  );
}
