import { siteConfig } from "./site-config";

export type PageKey = "home" | "infrastructure" | "architecture" | "start";

const navigation = [
  ["home", "/", "Home"],
  ["infrastructure", "/infrastructure", "Infrastructure"],
  ["architecture", "/architecture", "Architecture"],
  ["start", "/get-started", "Get started"],
] as const;

export function SiteHeader({ current }: { current: PageKey }) {
  return (
    <header className="site-header">
      <a className="wordmark" href="/" aria-label="Go to the home page">
        <span className="wordmark-mark" aria-hidden="true">[ ]</span>
        <span>{siteConfig.productName}</span>
      </a>
      <nav aria-label="Main navigation">
        {navigation.map(([key, href, label]) => (
          <a
            className={key === "start" ? "nav-cta" : undefined}
            href={href}
            aria-current={current === key ? "page" : undefined}
            key={key}
          >
            {label}{key === "start" && <span aria-hidden="true"> ↗</span>}
          </a>
        ))}
      </nav>
    </header>
  );
}

export function SiteFooter() {
  return (
    <footer>
      <div className="footer-mark" aria-hidden="true">[ ]</div>
      <p>Keep the code<br />about the business.</p>
      <div className="footer-meta"><span>Carbide</span><span>Kotlin service infrastructure</span></div>
    </footer>
  );
}
