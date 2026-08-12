import { RSocketBinding } from "@ifx/rpc-client-rsocket";
import {
  type IfxOperationDescription,
  type IfxServiceCatalog,
  type IfxServiceDescription,
  type IfxTypeDescription,
  type IfxTypeReference,
  type IfxUnionVariantDescription,
} from "@ifx/rpc-client";

const styles = `
  :root { font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: #17211c; background: #f4f6f2; }
  * { box-sizing: border-box; }
  body { margin: 0; min-width: 320px; }
  button, input, select, textarea { font: inherit; }
  button { cursor: pointer; }
  .shell { min-height: 100vh; }
  .topbar { height: 68px; display: flex; align-items: center; justify-content: space-between; padding: 0 32px; background: #14261d; color: white; }
  .brand { display: flex; align-items: center; gap: 13px; font-weight: 720; letter-spacing: -.02em; }
  .mark { display: grid; place-items: center; width: 32px; height: 32px; border: 1px solid #8cc09d; color: #bde6c9; font-size: 12px; font-family: ui-monospace, monospace; }
  .host-name { color: #b9c8bf; font-size: 13px; }
  main { width: min(1320px, calc(100% - 40px)); margin: 0 auto; padding: 54px 0 80px; }
  .eyebrow { color: #568164; font: 700 11px/1.2 ui-monospace, monospace; letter-spacing: .14em; text-transform: uppercase; }
  h1 { margin: 9px 0 12px; font-size: clamp(34px, 5vw, 58px); line-height: .98; letter-spacing: -.055em; font-weight: 650; }
  .lede { max-width: 660px; margin: 0; color: #5f6c64; font-size: 17px; line-height: 1.55; }
  .architecture { display: grid; grid-template-columns: minmax(0, 1fr) 230px; grid-template-areas: "clients clients" "business utilities" "access utilities" "resources resources"; margin-top: 46px; border: 1px solid #cfd5d0; background: #fff; box-shadow: 0 10px 34px rgba(23, 41, 31, .04); }
  .architecture-layer { display: grid; grid-template-columns: 150px minmax(0, 1fr); min-height: 176px; }
  .client-layer { grid-area: clients; min-height: 138px; border-bottom: 1px dashed #89928c; }
  .business-layer { grid-area: business; border-bottom: 1px dashed #89928c; }
  .access-layer { grid-area: access; }
  .resources-layer { grid-area: resources; min-height: 166px; border-top: 1px dashed #89928c; }
  .unclassified-layer { grid-column: 1 / -1; border-top: 1px dashed #89928c; }
  .layer-label { display: flex; align-items: center; padding: 28px 26px; font-size: 17px; font-weight: 760; line-height: 1.12; letter-spacing: -.02em; }
  .layer-content { display: grid; align-content: center; gap: 18px; padding: 28px 32px; min-width: 0; }
  .service-row { display: flex; flex-wrap: wrap; gap: 16px; }
  .layer-placeholder { justify-self: center; align-self: center; color: #929b94; font-size: 15px; text-align: center; }
  .utilities-layer { grid-area: utilities; display: flex; flex-direction: column; min-width: 0; padding: 26px 22px; border-left: 1px dashed #89928c; }
  .utilities-label { margin-bottom: 22px; font-size: 17px; font-weight: 760; letter-spacing: -.02em; text-align: center; }
  .utilities-content { display: grid; place-items: center; gap: 14px; flex: 1; }
  .service-card { position: relative; display: grid; place-items: center; width: 218px; min-height: 104px; padding: 16px; color: #1c211e; text-decoration: none; border: 1px solid #353a37; border-radius: 6px; box-shadow: 0 2px 0 rgba(23, 41, 31, .09); transition: transform .16s ease, box-shadow .16s ease, filter .16s ease; }
  .service-card.manager { background: #ffdc73; }
  .service-card.engine { background: #ff9635; }
  .service-card.access { background: #e3e4e3; }
  .service-card.utility { width: 100%; min-width: 0; min-height: 52px; padding: 10px 72px 10px 12px; background: #dec6e8; }
  .service-card.utility .service-operation-count { display: none; }
  .service-card.unclassified { background: #dec6e8; }
  .service-card:hover, .service-card:focus-visible { transform: translateY(-3px); box-shadow: 0 7px 18px rgba(23, 41, 31, .16); filter: saturate(1.05); outline: none; }
  .service-card.transition-source { view-transition-name: service-surface; }
  .stereotype { color: #6d7a72; font: 11px ui-monospace, monospace; }
  .service-status { position: absolute; top: 8px; right: 8px; display: inline-flex; align-items: center; gap: 4px; padding: 3px 5px; border: 1px solid rgba(53, 58, 55, .24); border-radius: 999px; background: rgba(255, 255, 255, .72); color: #6b776f; font-size: 8px; font-weight: 750; letter-spacing: .02em; }
  .service-status::before { content: ""; width: 5px; height: 5px; border-radius: 50%; background: currentColor; }
  .service-status.ready { color: #2d7040; }
  .service-status.starting { color: #8a6613; }
  .service-status.unavailable { color: #a04735; }
  .service-card h2 { margin: 0; font-size: 17px; line-height: 1.08; letter-spacing: -.025em; text-align: center; }
  .service-operation-count { position: absolute; top: calc(50% + 24px); left: 12px; right: 12px; color: rgba(28, 33, 30, .6); font-size: 10px; line-height: 1; text-align: center; }
  .address { overflow-wrap: anywhere; color: #718078; font: 12px/1.5 ui-monospace, monospace; }
  .empty { margin-top: 42px; padding: 28px; background: #fff; border: 1px solid #d6ddd7; color: #68766d; }
  .back { display: inline-flex; gap: 8px; align-items: center; color: #426d4d; text-decoration: none; font-size: 13px; font-weight: 700; }
  .service-page.manager { --service-accent: #d5a619; --service-tint: #fff1b8; }
  .service-page.engine { --service-accent: #e97014; --service-tint: #ffe0bf; }
  .service-page.access { --service-accent: #858b87; --service-tint: #eceeed; }
  .service-page.utility { --service-accent: #9b68aa; --service-tint: #f0e2f4; }
  .service-page.unclassified { --service-accent: #9b68aa; --service-tint: #f0e2f4; }
  .service-surface { overflow: hidden; margin-top: 30px; border: 1px solid #4c524e; border-top: 7px solid var(--service-accent); border-radius: 9px; background: var(--service-tint); view-transition-name: service-surface; }
  .service-head { display: flex; justify-content: space-between; align-items: end; gap: 24px; padding: 30px 32px; }
  .service-head h1 { font-size: clamp(34px, 4vw, 52px); }
  .service-meta { max-width: 420px; text-align: right; }
  .service-meta .address { display: block; margin-top: 6px; }
  .service-contract { padding: 0 32px 32px; border-top: 1px solid color-mix(in srgb, var(--service-accent) 40%, transparent); background: color-mix(in srgb, var(--service-tint) 88%, #59635d); }
  .service-logs { padding: 29px 32px 32px; border-top: 1px solid #35443b; background: #111914; color: #dce7df; }
  .logs-intro { display: flex; align-items: end; justify-content: space-between; gap: 24px; }
  .logs-intro h2 { margin: 7px 0 0; font-size: 25px; letter-spacing: -.035em; }
  .logs-intro .eyebrow { color: #78a887; }
  .logs-controls { display: flex; align-items: center; gap: 12px; }
  .logs-status { display: inline-flex; align-items: center; gap: 6px; color: #9aada0; font: 11px ui-monospace, monospace; }
  .logs-status::before { content: ""; width: 6px; height: 6px; border-radius: 50%; background: #87958c; }
  .logs-status.live::before { background: #68ce83; box-shadow: 0 0 0 3px rgba(104, 206, 131, .12); }
  .logs-status.reconnecting::before { background: #d4a849; }
  .logs-refresh { border: 1px solid #526158; padding: 7px 10px; background: #1d2921; color: #dce7df; font-size: 11px; }
  .logs-refresh:hover { border-color: #799083; background: #26352b; }
  .log-stream { max-height: 430px; min-height: 118px; overflow: auto; margin-top: 18px; border: 1px solid #334038; background: #0b110d; font: 11px/1.55 ui-monospace, SFMono-Regular, Consolas, monospace; }
  .log-empty { display: grid; min-height: 116px; place-items: center; padding: 24px; color: #708078; text-align: center; }
  .log-entry { display: grid; grid-template-columns: 92px 58px minmax(150px, 220px) minmax(0, 1fr); gap: 11px; align-items: baseline; padding: 8px 12px; border-bottom: 1px solid #1b261f; }
  .log-entry:last-child { border-bottom: 0; }
  .log-time { color: #718079; }
  .log-severity { font-weight: 750; text-transform: uppercase; }
  .log-severity.verbose, .log-severity.debug { color: #81928a; }
  .log-severity.info { color: #76c98d; }
  .log-severity.warn { color: #e2b95c; }
  .log-severity.error, .log-severity.assert { color: #ec7e68; }
  .log-tag { overflow: hidden; color: #89a997; text-overflow: ellipsis; white-space: nowrap; }
  .log-message { min-width: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
  .log-throwable { grid-column: 4; margin: 4px 0 2px; color: #e39a8d; white-space: pre-wrap; overflow-wrap: anywhere; }
  .operations-intro { display: flex; align-items: end; justify-content: space-between; gap: 24px; padding-top: 27px; }
  .operations-intro h2 { margin: 7px 0 0; font-size: 25px; letter-spacing: -.035em; }
  .operations-intro p { margin: 0; color: #727e76; font-size: 13px; }
  .operations { display: grid; gap: 10px; margin-top: 17px; }
  .operations .empty { margin-top: 0; }
  .operation { overflow: hidden; background: #fff; border: 1px solid #cfd7d1; }
  .operation[open] { border-color: #aeb9b1; box-shadow: 0 8px 24px rgba(23, 41, 31, .06); }
  .operation-head { display: flex; align-items: center; gap: 13px; padding: 17px 20px; cursor: pointer; list-style: none; user-select: none; }
  .operation-head::-webkit-details-marker { display: none; }
  .operation[open] > .operation-head { border-bottom: 1px solid #e2e7e3; }
  .operation-head:hover { background: #fafbf9; }
  .operation-head:focus-visible { outline: 3px solid color-mix(in srgb, var(--service-accent) 28%, transparent); outline-offset: -3px; }
  .operation-signature { min-width: 0; overflow-wrap: anywhere; }
  .operation-name { font-size: 17px; font-weight: 720; letter-spacing: -.02em; }
  .signature-punctuation { color: #7a877f; font-size: 14px; }
  .signature-type { color: #53665a; font: 12px ui-monospace, monospace; }
  .interaction-label { flex: 0 0 auto; margin-left: auto; padding: 4px 7px; border: 1px solid color-mix(in srgb, var(--service-accent) 42%, transparent); border-radius: 999px; background: var(--service-tint); color: #59665e; font: 700 9px ui-monospace, monospace; letter-spacing: .07em; text-transform: uppercase; }
  .operation-body { display: grid; grid-template-columns: minmax(0, 1fr) minmax(320px, .85fr); min-height: var(--response-height, 250px); }
  .request, .response { min-width: 0; min-height: 0; padding: 24px; }
  .response { position: relative; display: flex; flex-direction: column; overflow: hidden; contain: size; padding-bottom: 0; border-left: 1px solid #d2dbd4; background: #eef2ef; color: #33483a; }
  .response::after { content: ""; position: absolute; z-index: 2; right: 0; bottom: 0; left: 0; height: 10px; pointer-events: none; box-shadow: inset 0 -9px 9px -9px rgb(48 65 53 / 32%); }
  .panel-title { display: flex; justify-content: space-between; margin-bottom: 18px; color: #6b786f; font: 700 10px ui-monospace, monospace; letter-spacing: .12em; text-transform: uppercase; }
  .response .panel-title { flex: 0 0 auto; color: #697a6e; }
  .type-label { letter-spacing: 0; text-transform: none; font-weight: 500; }
  .json-editor { min-height: 145px; margin-bottom: 18px; padding: 13px 15px; overflow: auto; border: 1px solid #d2ddd5; border-left: 3px solid #8da996; background: #f0f5f1; color: #526159; white-space: pre; font: 12px/1.75 ui-monospace, monospace; }
  .json-editor:focus-within { border-color: #91aa98; border-left-color: #4e7c5a; background: #edf4ef; }
  .json-composite { display: inline-block; min-width: 0; vertical-align: top; }
  .json-indent { display: block; padding-left: 19px; }
  .json-property { display: grid; grid-template-columns: auto minmax(0, 1fr); min-width: 0; }
  .json-array-item { display: flex; align-items: flex-start; min-width: 0; }
  .json-property-key { flex: 0 0 auto; color: #326b91; }
  .json-property-value { min-width: 0; }
  .json-token-input, .json-enum-select { min-width: 2ch; max-width: 34ch; margin: 0; border: 0; border-bottom: 1px dotted transparent; outline: 0; background: transparent; font: inherit; }
  .json-token-input:hover, .json-token-input:focus, .json-enum-select:hover, .json-enum-select:focus { border-bottom-color: currentColor; }
  .json-token-input.string { color: #9a542f; }
  .json-token-input.number { color: #7250a0; }
  .json-enum-select { width: auto; padding: 0 15px 0 0; color: #9a542f; cursor: pointer; }
  .json-boolean-select { padding-right: 15px; color: #18766c; }
  .json-null-button { margin: 0; border: 0; border-bottom: 1px dotted #8b5970; padding: 0; background: transparent; color: #8b5970; font: inherit; cursor: pointer; }
  .json-null-active { display: inline-flex; align-items: flex-start; gap: 6px; }
  .json-null-checkbox { width: 11px; height: 11px; margin: 5px 0 0; accent-color: #8b5970; cursor: pointer; }
  .json-array-item { gap: 7px; }
  .json-array-item > .json-property-value { flex: 0 1 auto; }
  .json-array-action { margin: 0; border: 0; padding: 0 3px; background: transparent; color: #829087; font: 11px/1.75 ui-monospace, monospace; cursor: pointer; }
  .json-array-action:hover, .json-array-action:focus-visible { color: #426d4d; outline: none; }
  .json-raw-input { display: block; width: min(100%, 48ch); min-height: 70px; border: 0; border-left: 1px solid #cbd6ce; padding: 5px 9px; outline: 0; resize: vertical; background: rgba(255, 255, 255, .42); color: #33483a; font: inherit; }
  .invoke { display: inline-flex; align-items: center; gap: 9px; border: 0; padding: 11px 17px; background: #29633a; color: #fff; font-weight: 750; font-size: 13px; }
  .invoke:hover { background: #1d502d; }
  .invoke.cancel-stream { background: #a04735; }
  .invoke.cancel-stream:hover { background: #843728; }
  .invoke:disabled { cursor: wait; opacity: .6; }
  .invoke-controls { display: flex; align-items: center; gap: 11px; }
  .result { flex: 1 1 145px; min-height: 0; overflow: auto; overscroll-behavior: contain; white-space: pre-wrap; overflow-wrap: anywhere; color: #33483a; font: 12px/1.65 ui-monospace, monospace; }
  .result.muted { color: #718077; }
  .result.error { color: #a04432; }
  .response-value { min-width: 0; margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; color: inherit; font: inherit; }
  .json-key { color: #326b91; }
  .json-string { color: #9a542f; }
  .json-number { color: #7250a0; }
  .json-boolean { color: #18766c; }
  .json-null { color: #8b5970; }
  .stream-event { display: grid; grid-template-columns: 48px minmax(0, 1fr); gap: 12px; align-items: start; }
  .stream-event + .stream-event { margin-top: 16px; padding-top: 16px; border-top: 1px solid #d5ddd7; }
  .stream-index { align-self: stretch; padding-right: 10px; border-right: 1px solid #c8d3cb; color: #829087; text-align: right; user-select: none; }
  .status { color: #718278; font-size: 11px; text-transform: none; letter-spacing: 0; }
  ::view-transition-group(service-surface) { animation-duration: 460ms; animation-timing-function: cubic-bezier(.22, 1, .36, 1); }
  ::view-transition-old(root) { animation: 150ms ease-out both fade-away; }
  ::view-transition-new(root) { animation: 280ms 100ms ease-out both fade-in; }
  @keyframes fade-away { to { opacity: 0; } }
  @keyframes fade-in { from { opacity: 0; } }
  @media (prefers-reduced-motion: reduce) { ::view-transition-group(*), ::view-transition-old(root), ::view-transition-new(root) { animation-duration: .001ms; animation-delay: 0ms; } }
  @media (max-width: 760px) { .topbar { padding: 0 20px; } main { width: min(100% - 28px, 1320px); padding-top: 36px; } .architecture { display: block; } .architecture-layer { display: block; } .client-layer, .business-layer, .access-layer, .resources-layer, .unclassified-layer { border-top: 0; border-bottom: 1px dashed #89928c; } .layer-label { padding: 17px 20px; border-right: 0; border-bottom: 1px dashed #89928c; } .layer-content { padding: 20px; } .utilities-layer { padding: 20px; border-left: 0; border-bottom: 1px dashed #89928c; } .utilities-label { text-align: left; } .service-card { width: 100%; } .service-head { display: block; padding: 24px 22px; } .service-contract { padding: 0 22px 24px; } .service-meta { margin-top: 18px; text-align: left; } .operations-intro { display: block; padding-top: 22px; } .operations-intro p { margin-top: 9px; } .operation-body { grid-template-columns: 1fr; } .response { border-top: 1px solid #d2dbd4; border-left: 0; } .service-logs { padding: 24px 22px; } .logs-intro { display: block; } .logs-controls { margin-top: 14px; justify-content: space-between; } .log-entry { grid-template-columns: 82px 54px minmax(0, 1fr); } .log-message, .log-throwable { grid-column: 1 / -1; } }
`;

const I_SERVICE_OPERATIONS = new Set(["status", "init", "isReady", "isLive"]);
interface JsonInput { readonly element: HTMLElement; readonly read: () => unknown }
interface SchemaContext { readonly definitions: ReadonlyMap<string, IfxTypeDescription>; readonly parameters: ReadonlyMap<string, IfxTypeReference> }
interface JsonObjectMembers { readonly element: HTMLElement; readonly count: number; readonly read: () => Record<string, unknown> }
interface ResolvedObject { readonly definition: Extract<IfxTypeDescription, { type: "object" }>; readonly context: SchemaContext }
type ServiceKind = "manager" | "engine" | "access" | "utility" | "unclassified";
interface CatalogService { readonly service: IfxServiceDescription; readonly index: number; readonly kind: ServiceKind }
interface LogTailEntry {
  readonly sequence: number;
  readonly timestampEpochMilliseconds: number;
  readonly serviceInterface: string;
  readonly serviceClassName: string | null;
  readonly path: readonly string[];
  readonly severity: string;
  readonly message: string;
  readonly throwable: string | null;
}

const I_ACTUATOR_ADDRESS = "ifx.actuator.IActuator";

class ActuatorClient {
  private constructor(private readonly binding: RSocketBinding) {}

  static async connect(): Promise<ActuatorClient> {
    const scheme = location.protocol === "https:" ? "wss:" : "ws:";
    const binding = await RSocketBinding.connect({
      url: RSocketBinding.serviceUrl(`${scheme}//${location.host}`, I_ACTUATOR_ADDRESS),
    });
    return new ActuatorClient(binding);
  }

  catalog(): Promise<IfxServiceCatalog> {
    return this.binding.requestResponse<IfxServiceCatalog>("catalog()");
  }

  logTail(serviceInterface: string): AsyncIterable<LogTailEntry> {
    return this.binding.requestStream<LogTailEntry>("logTail(kotlin.String)", serviceInterface);
  }

  close(): void {
    this.binding.close();
  }
}

const appElement = document.querySelector<HTMLElement>("#app");
if (!appElement) throw new Error("Missing #app element");
const app: HTMLElement = appElement;
let activeLogClient: ActuatorClient | undefined;
let logConnectionGeneration = 0;
document.head.append(Object.assign(document.createElement("style"), { textContent: styles }));

void start();

async function start(): Promise<void> {
  let actuator: ActuatorClient | undefined;
  try {
    actuator = await ActuatorClient.connect();
    const catalog = await actuator.catalog();
    const render = () => renderRoute(catalog);
    window.addEventListener("hashchange", render);
    app.addEventListener("click", (event) => navigateFromServiceCard(event, render));
    render();
  } catch (error) {
    app.innerHTML = `<div class="shell"><header class="topbar"><div class="brand"><span class="mark">iFX</span> Service Explorer</div></header><main><div class="empty">${escapeHtml(messageOf(error))}</div></main></div>`;
  } finally {
    actuator?.close();
  }
}

function navigateFromServiceCard(event: MouseEvent, render: () => void): void {
  if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
  if (!(event.target instanceof Element)) return;
  const card = event.target.closest<HTMLAnchorElement>(".service-card[href]");
  const destination = card?.getAttribute("href");
  if (!card || !destination || typeof document.startViewTransition !== "function") return;

  event.preventDefault();
  card.classList.add("transition-source");
  document.startViewTransition(() => {
    history.pushState(null, "", destination);
    render();
  });
}

function renderRoute(catalog: IfxServiceCatalog): void {
  logConnectionGeneration += 1;
  activeLogClient?.close();
  activeLogClient = undefined;
  const match = /^#\/services\/(.+)$/.exec(location.hash);
  const service = match && catalog.services.find((item) => item.address === decodeURIComponent(match[1]));
  if (service) renderService(catalog, service);
  else renderCatalog(catalog);
}

function chrome(catalog: IfxServiceCatalog, body: string): void {
  app.innerHTML = `<div class="shell"><header class="topbar"><div class="brand"><span class="mark">iFX</span> Service Explorer</div><div class="host-name">${escapeHtml(catalog.name)}</div></header><main>${body}</main></div>`;
}

function renderCatalog(catalog: IfxServiceCatalog): void {
  const services = catalog.services.map((service, index): CatalogService => ({
    service,
    index,
    kind: serviceKind(service),
  }));
  const managers = services.filter(({ kind }) => kind === "manager");
  const engines = services.filter(({ kind }) => kind === "engine");
  const access = services.filter(({ kind }) => kind === "access");
  const utilities = services.filter(({ kind }) => kind === "utility");
  const unclassified = services.filter(({ kind }) => kind === "unclassified");
  const architecture = `
    ${renderArchitectureLayer("Client / UI", [], "client-layer", "Client applications will appear here")}
    ${renderArchitectureLayer("Business<br>Logic", [managers, engines], "business-layer", "No managers or engines registered")}
    ${renderArchitectureLayer("Resource<br>Access", [access], "access-layer", "No access services registered")}
    <aside class="utilities-layer">
      <div class="utilities-label">Utilities</div>
      <div class="utilities-content">${utilities.length > 0
        ? utilities.map(renderServiceCard).join("")
        : `<div class="layer-placeholder">Utilities will appear here</div>`}</div>
    </aside>
    ${renderArchitectureLayer("Resources", [], "resources-layer", "Resources will appear here")}
    ${unclassified.length > 0 ? renderArchitectureLayer("Unclassified", [unclassified], "unclassified-layer", "") : ""}`;

  chrome(catalog, `
    <div class="eyebrow">Static architecture</div>
    <h1>${escapeHtml(catalog.name)}</h1>
    <p class="lede">Explore the running system by layer. Select a service component to inspect its contract and invoke its operations.</p>
    <section class="architecture" aria-label="Service architecture">${architecture}</section>`);

  for (const card of app.querySelectorAll<HTMLElement>(".service-card[data-service-index]")) {
    const service = catalog.services[Number(card.dataset.serviceIndex)];
    const pill = card.querySelector<HTMLElement>(".service-status");
    if (service && pill) void loadServiceStatus(service, pill);
  }
}

function renderArchitectureLayer(
  label: string,
  rows: readonly CatalogService[][],
  className: string,
  placeholder: string,
): string {
  const populatedRows = rows.filter((row) => row.length > 0);
  return `
    <section class="architecture-layer ${className}">
      <div class="layer-label">${label}</div>
      <div class="layer-content">${populatedRows.length > 0 ? populatedRows.map((row) => `
        <div class="service-row">${row.map(renderServiceCard).join("")}</div>`).join("") : `
        <div class="layer-placeholder">${escapeHtml(placeholder)}</div>`}
      </div>
    </section>`;
}

function renderServiceCard({ service, index, kind }: CatalogService): string {
  const operationCount = visibleOperations(service).length;
  return `
    <a class="service-card ${kind}" data-service-index="${index}" href="#/services/${encodeURIComponent(service.address)}" title="${escapeHtml(service.address)}">
      <h2>${escapeHtml(service.name)}</h2>
      <span class="service-operation-count">${operationCount} operation${operationCount === 1 ? "" : "s"}</span>
      <span class="service-status">Checking</span>
    </a>`;
}

function serviceKind(service: IfxServiceDescription): ServiceKind {
  if (service.kind === "utility") return "utility";
  const name = service.name.replace(/^I(?=[A-Z])/, "");
  if (/Manager$/i.test(name)) return "manager";
  if (/Engine$/i.test(name)) return "engine";
  if (/Access$/i.test(name)) return "access";
  return "unclassified";
}

function serviceTypeLabel(kind: ServiceKind): string {
  switch (kind) {
    case "manager": return "Business Logic · Manager";
    case "engine": return "Business Logic · Engine";
    case "access": return "Resource Access";
    case "utility": return "Utility";
    case "unclassified": return "Unclassified Service";
  }
}

function renderService(catalog: IfxServiceCatalog, service: IfxServiceDescription): void {
  const kind = serviceKind(service);
  chrome(catalog, `
    <div class="service-page ${kind}">
      <a class="back" href="#/">← All service components</a>
      <section class="service-surface">
        <header class="service-head">
          <div><div class="eyebrow">${escapeHtml(serviceTypeLabel(kind))}</div><h1>${escapeHtml(service.name)}</h1></div>
          <div class="service-meta"><span class="stereotype">RSocket endpoint</span><span class="address">${escapeHtml(service.address)}</span></div>
        </header>
        <div class="service-contract">
          <div class="operations-intro">
            <div><div class="eyebrow">Contract</div><h2>Operations</h2></div>
            <p>Select an operation to configure and invoke it.</p>
          </div>
          <section class="operations" aria-label="Operations"></section>
        </div>
        <section class="service-logs" aria-label="Application logs">
          <div class="logs-intro">
            <div><div class="eyebrow">Actuator</div><h2>Application logs</h2></div>
            <div class="logs-controls"><span class="logs-status">Connecting</span><button class="logs-refresh" type="button">Reconnect</button></div>
          </div>
          <div class="log-stream" role="log" aria-live="polite"><div class="log-empty">Connecting to retained service logs…</div></div>
        </section>
      </section>
    </div>`);

  connectServiceLogs(service);
  const container = app.querySelector<HTMLElement>(".operations");
  if (!container) return;
  const definitions = new Map(service.types.map((definition) => [definition.name, definition]));
  const operations = visibleOperations(service);
  if (operations.length === 0) {
    container.innerHTML = `<div class="empty">This service does not declare any operations beyond the IService lifecycle.</div>`;
    return;
  }
  for (const operation of operations) container.append(renderOperation(service, operation, definitions));
}

function connectServiceLogs(service: IfxServiceDescription): void {
  const stream = app.querySelector<HTMLElement>(".log-stream");
  const status = app.querySelector<HTMLElement>(".logs-status");
  const reconnect = app.querySelector<HTMLButtonElement>(".logs-refresh");
  if (!stream || !status || !reconnect) return;

  const entries = new Map<number, LogTailEntry>();
  const connect = async () => {
    const current = ++logConnectionGeneration;
    activeLogClient?.close();
    activeLogClient = undefined;
    entries.clear();
    stream.innerHTML = `<div class="log-empty">Connecting to retained service logs…</div>`;
    setLogStatus(status, "Connecting");
    reconnect.disabled = true;

    let actuator: ActuatorClient | undefined;
    try {
      actuator = await ActuatorClient.connect();
      if (current !== logConnectionGeneration) {
        actuator.close();
        return;
      }
      activeLogClient = actuator;
      reconnect.disabled = false;
      setLogStatus(status, "Live", "live");
      stream.innerHTML = `<div class="log-empty">Waiting for application logs from ${escapeHtml(service.name)}…</div>`;

      for await (const entry of actuator.logTail(service.address)) {
        if (current !== logConnectionGeneration) break;
        entries.set(entry.sequence, entry);
        while (entries.size > 500) entries.delete(Math.min(...entries.keys()));
        renderServiceLogs(stream, service, [...entries.values()].sort((left, right) => left.sequence - right.sequence));
      }
      if (current === logConnectionGeneration) setLogStatus(status, "Ended", "reconnecting");
    } catch (error) {
      if (current === logConnectionGeneration) {
        reconnect.disabled = false;
        setLogStatus(status, "Unavailable", "reconnecting");
        stream.innerHTML = `<div class="log-empty">${escapeHtml(messageOf(error))}</div>`;
      }
    } finally {
      actuator?.close();
      if (activeLogClient === actuator) activeLogClient = undefined;
    }
  };

  reconnect.addEventListener("click", () => void connect());
  void connect();
}

function setLogStatus(element: HTMLElement, label: string, state = ""): void {
  element.textContent = label;
  element.className = `logs-status ${state}`.trim();
}

function renderServiceLogs(
  container: HTMLElement,
  service: IfxServiceDescription,
  entries: readonly LogTailEntry[],
): void {
  container.innerHTML = entries.map((entry) => {
    const severity = entry.severity.toLowerCase();
    const implementation = entry.serviceClassName?.split(".").pop() ?? service.name;
    const tag = [implementation, ...entry.path].filter(Boolean).join(".");
    return `<div class="log-entry">
      <span class="log-time">${escapeHtml(logTimestamp(entry.timestampEpochMilliseconds))}</span>
      <span class="log-severity ${escapeHtml(severity)}">${escapeHtml(entry.severity)}</span>
      <span class="log-tag" title="${escapeHtml(tag)}">${escapeHtml(tag)}</span>
      <span class="log-message">${escapeHtml(entry.message)}</span>
      ${entry.throwable ? `<pre class="log-throwable">${escapeHtml(entry.throwable)}</pre>` : ""}
    </div>`;
  }).join("");
  container.scrollTop = container.scrollHeight;
}

function logTimestamp(epochMilliseconds: number): string {
  const date = new Date(epochMilliseconds);
  return `${date.toLocaleTimeString([], { hour12: false })}.${String(date.getMilliseconds()).padStart(3, "0")}`;
}

function visibleOperations(service: IfxServiceDescription): readonly IfxOperationDescription[] {
  return service.operations.filter((operation) => !I_SERVICE_OPERATIONS.has(operation.name));
}

async function loadServiceStatus(service: IfxServiceDescription, pill: HTMLElement): Promise<void> {
  const operation = service.operations.find((candidate) => candidate.name === "status");
  if (!operation) {
    setServiceStatus(pill, "Unknown", "unknown");
    return;
  }

  let binding: RSocketBinding | undefined;
  try {
    const scheme = location.protocol === "https:" ? "wss:" : "ws:";
    binding = await RSocketBinding.connect({
      url: RSocketBinding.serviceUrl(`${scheme}//${location.host}`, service.address),
    });
    const status = await binding.requestResponse<{ readonly ready: boolean; readonly live: boolean }>(operation.route);
    if (!status.live) setServiceStatus(pill, "Not live", "unavailable");
    else if (!status.ready) setServiceStatus(pill, "Starting", "starting");
    else setServiceStatus(pill, "Ready", "ready");
  } catch {
    setServiceStatus(pill, "Offline", "unavailable");
  } finally {
    binding?.close();
  }
}

function setServiceStatus(
  pill: HTMLElement,
  label: string,
  state: "unknown" | "ready" | "starting" | "unavailable",
): void {
  pill.textContent = label;
  pill.className = `service-status ${state}`;
}

function renderOperation(
  service: IfxServiceDescription,
  operation: IfxOperationDescription,
  definitions: ReadonlyMap<string, IfxTypeDescription>,
): HTMLElement {
  const article = document.createElement("details");
  article.className = "operation";
  const context: SchemaContext = { definitions, parameters: new Map() };
  const inputType = operation.parameterName ? typeLabel(operation.request) : "";
  const returnType = operation.interaction === "requestStream"
    ? `Flow<${typeLabel(operation.response)}>`
    : typeLabel(operation.response);
  const responseExample = exampleValue(operation.response, context);
  article.innerHTML = `
    <summary class="operation-head"><span class="operation-signature"><span class="operation-name">${escapeHtml(operation.name)}</span><span class="signature-punctuation">(</span><span class="signature-type">${escapeHtml(inputType)}</span><span class="signature-punctuation">) : </span><span class="signature-type">${escapeHtml(returnType)}</span></span><span class="interaction-label">${interactionLabel(operation.interaction)}</span></summary>
    <div class="operation-body">
      <section class="request"><div class="panel-title"><span>Request</span><span class="type-label">${escapeHtml(typeLabel(operation.request))}</span></div><div class="form"></div><div class="invoke-controls"><button class="invoke" type="button">Invoke <span>→</span></button><span class="status">Example</span></div></section>
      <section class="response"><div class="panel-title"><span>Response</span></div><div class="result muted"></div></section>
    </div>`;

  const form = article.querySelector<HTMLElement>(".form")!;
  const button = article.querySelector<HTMLButtonElement>(".invoke")!;
  const operationBody = article.querySelector<HTMLElement>(".operation-body")!;
  const response = article.querySelector<HTMLElement>(".response")!;
  const result = article.querySelector<HTMLElement>(".result")!;
  const status = article.querySelector<HTMLElement>(".status")!;
  if (operation.interaction === "requestStream") appendStreamEvent(result, 0, responseExample);
  else renderResponseValue(result, responseExample);
  let readRequest = (): unknown => undefined;
  if (operation.parameterName) {
    const input = createJsonInput(operation.request, context, operation.parameterName);
    form.append(input.element);
    readRequest = input.read;
  } else {
    form.innerHTML = `<p class="address">This operation has no request body.</p>`;
  }

  let responseHeightPending = false;
  const syncResponseHeight = (): void => {
    if (responseHeightPending || !article.open) return;
    responseHeightPending = true;
    requestAnimationFrame(() => {
      responseHeightPending = false;
      const title = response.querySelector<HTMLElement>(".panel-title")!;
      const responseStyle = getComputedStyle(response);
      const titleStyle = getComputedStyle(title);
      const chromeHeight = parseFloat(responseStyle.paddingTop)
        + parseFloat(responseStyle.paddingBottom)
        + title.offsetHeight
        + parseFloat(titleStyle.marginBottom);
      const naturalHeight = chromeHeight + result.scrollHeight;
      operationBody.style.setProperty("--response-height", `${Math.max(250, Math.min(560, naturalHeight))}px`);
    });
  };
  new MutationObserver(syncResponseHeight).observe(result, { childList: true, subtree: true, characterData: true });
  article.addEventListener("toggle", syncResponseHeight);

  let binding: RSocketBinding | undefined;
  let invocation = 0;
  let activeStreamInvocation: number | undefined;
  button.addEventListener("click", async () => {
    if (activeStreamInvocation !== undefined) {
      invocation += 1;
      activeStreamInvocation = undefined;
      binding?.close();
      binding = undefined;
      setInvokeButtonState(button);
      status.textContent = "Cancelled";
      return;
    }

    const current = ++invocation;
    const streaming = operation.interaction === "requestStream";
    let callBinding: RSocketBinding | undefined;
    button.disabled = !streaming;
    if (streaming) {
      activeStreamInvocation = current;
      setInvokeButtonState(button, true);
    }
    result.className = "result muted";
    result.textContent = "Connecting…";
    status.textContent = "Running";
    try {
      const request = readRequest();
      const scheme = location.protocol === "https:" ? "wss:" : "ws:";
      const baseUrl = `${scheme}//${location.host}`;
      callBinding = await RSocketBinding.connect({ url: RSocketBinding.serviceUrl(baseUrl, service.address) });
      binding = callBinding;
      if (current !== invocation) return;
      const started = performance.now();
      if (operation.interaction === "fireAndForget") {
        if (operation.parameterName) await callBinding.fireAndForget(operation.route, request);
        else await callBinding.fireAndForget(operation.route);
        result.textContent = "Request accepted (fire-and-forget).";
      } else if (operation.interaction === "requestResponse") {
        const value = operation.parameterName
          ? await callBinding.requestResponse(operation.route, request)
          : await callBinding.requestResponse(operation.route);
        renderResponseValue(result, value);
      } else {
        result.replaceChildren();
        result.className = "result";
        let count = 0;
        const stream = operation.parameterName
          ? callBinding.requestStream(operation.route, request)
          : callBinding.requestStream(operation.route);
        for await (const value of stream) {
          if (current !== invocation) break;
          appendStreamEvent(result, count, value);
          count += 1;
          status.textContent = `${count} event${count === 1 ? "" : "s"}`;
        }
        if (current === invocation && count === 0) result.textContent = "Stream completed without values.";
      }
      if (current === invocation) {
        result.className = "result";
        status.textContent = `${status.textContent === "Running" ? "Complete" : status.textContent} · ${Math.round(performance.now() - started)} ms`;
      }
    } catch (error) {
      if (current === invocation) {
        result.className = "result error";
        result.textContent = messageOf(error);
        status.textContent = "Failed";
      }
    } finally {
      callBinding?.close();
      if (binding === callBinding) binding = undefined;
      if (current === invocation) {
        activeStreamInvocation = undefined;
        button.disabled = false;
        setInvokeButtonState(button);
      }
    }
  });
  return article;
}

function setInvokeButtonState(button: HTMLButtonElement, streaming = false): void {
  button.classList.toggle("cancel-stream", streaming);
  if (streaming) {
    button.textContent = "Cancel stream";
    return;
  }
  const arrow = document.createElement("span");
  arrow.textContent = "→";
  button.replaceChildren(document.createTextNode("Invoke "), arrow);
}

function renderResponseValue(container: HTMLElement, value: unknown): void {
  const body = document.createElement("pre");
  body.className = "response-value";
  renderHighlightedJson(body, value);
  container.replaceChildren(body);
}

function appendStreamEvent(container: HTMLElement, index: number, value: unknown): void {
  const row = document.createElement("div");
  row.className = "stream-event";
  const marker = document.createElement("span");
  marker.className = "stream-index";
  marker.textContent = `[${index}]`;
  const body = document.createElement("pre");
  body.className = "response-value";
  renderHighlightedJson(body, value);
  row.append(marker, body);
  container.append(row);
}

function renderHighlightedJson(container: HTMLElement, value: unknown): void {
  renderHighlightedJsonSource(container, pretty(value));
}

function renderHighlightedJsonSource(container: HTMLElement, source: string): void {
  const tokens = /("(?:\\.|[^"\\])*")(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/g;
  let cursor = 0;
  for (const match of source.matchAll(tokens)) {
    const index = match.index;
    container.append(document.createTextNode(source.slice(cursor, index)));
    const quoted = match[1];
    const separator = match[2];
    const token = quoted ?? match[0];
    const span = document.createElement("span");
    span.className = quoted
      ? separator ? "json-key" : "json-string"
      : token === "null" ? "json-null"
      : token === "true" || token === "false" ? "json-boolean"
      : "json-number";
    span.textContent = token;
    container.append(span);
    if (separator) container.append(document.createTextNode(separator));
    cursor = index + match[0].length;
  }
  container.append(document.createTextNode(source.slice(cursor)));
}

function createJsonInput(reference: IfxTypeReference, context: SchemaContext, label: string): JsonInput {
  const wrapper = document.createElement("div");
  wrapper.className = "json-editor";
  const control = createJsonControl(reference, context, label);
  wrapper.append(control.element);
  return { element: wrapper, read: control.read };
}

function createJsonControl(reference: IfxTypeReference, context: SchemaContext, label: string): JsonInput {
  if (reference.type === "nullable") return createNullableJsonControl(reference.value, context, label);
  if (reference.type === "parameter") {
    return createJsonControl(context.parameters.get(reference.name) ?? { type: "string" }, context, label);
  }
  if (reference.type === "string") return createStringJsonControl(label);
  if (reference.type === "number") return createNumberJsonControl(label);
  if (reference.type === "boolean") return createBooleanJsonControl(label);
  if (reference.type === "void") return staticJsonControl("null", undefined, "json-null");
  if (reference.type === "array") return createArrayJsonControl(reference.element, context, label);
  if (reference.type === "record") {
    return createRawJsonControl(pretty(exampleValue(reference, context)), label);
  }

  const definition = context.definitions.get(reference.name);
  if (!definition) return createRawJsonControl("{}", label);
  const parameters = new Map(context.parameters);
  definition.typeParameters.forEach((name, index) => parameters.set(name, reference.arguments[index] ?? { type: "string" }));
  const nested: SchemaContext = { definitions: context.definitions, parameters };
  if (definition.type === "alias") return createJsonControl(definition.target, nested, label);
  if (definition.type === "stringUnion") return createEnumJsonControl(definition.values, label);
  if (definition.type === "sealedUnion") return createUnionJsonControl(definition, nested, label);
  return createObjectJsonControl(definition, nested);
}

function createStringJsonControl(label: string): JsonInput {
  const wrapper = document.createElement("span");
  wrapper.className = "json-string";
  const input = document.createElement("input");
  input.className = "json-token-input string";
  input.type = "text";
  input.value = "string";
  input.setAttribute("aria-label", label);
  const resize = () => { input.style.width = `${Math.max(2, Math.min(34, input.value.length + 1))}ch`; };
  input.addEventListener("input", resize);
  resize();
  wrapper.append(document.createTextNode('"'), input, document.createTextNode('"'));
  return { element: wrapper, read: () => input.value };
}

function createNumberJsonControl(label: string): JsonInput {
  const input = document.createElement("input");
  input.className = "json-token-input number";
  input.type = "text";
  input.inputMode = "decimal";
  input.value = "0";
  input.setAttribute("aria-label", label);
  input.style.width = "4ch";
  return {
    element: input,
    read: () => {
      const value = Number(input.value);
      if (!Number.isFinite(value)) throw new Error(`${label} must be a number`);
      return value;
    },
  };
}

function createBooleanJsonControl(label: string): JsonInput {
  const select = document.createElement("select");
  select.className = "json-enum-select json-boolean-select";
  select.setAttribute("aria-label", label);
  select.append(new Option("true", "true"), new Option("false", "false"));
  return { element: select, read: () => select.value === "true" };
}

function createEnumJsonControl(values: readonly string[], label: string): JsonInput {
  const wrapper = document.createElement("span");
  wrapper.className = "json-string";
  const select = document.createElement("select");
  select.className = "json-enum-select";
  select.setAttribute("aria-label", label);
  for (const value of values) select.append(new Option(value, value));
  wrapper.append(document.createTextNode('"'), select, document.createTextNode('"'));
  return { element: wrapper, read: () => select.value };
}

function createNullableJsonControl(reference: IfxTypeReference, context: SchemaContext, label: string): JsonInput {
  const wrapper = document.createElement("span");
  let isNull = true;
  let active: JsonInput | undefined;
  const renderNull = () => {
    isNull = true;
    const button = document.createElement("button");
    button.type = "button";
    button.className = "json-null-button";
    button.textContent = "null";
    button.title = `Set ${label}`;
    button.addEventListener("click", () => {
      isNull = false;
      active = createJsonControl(reference, context, label);
      const state = document.createElement("span");
      state.className = "json-null-active";
      const checkbox = document.createElement("input");
      checkbox.type = "checkbox";
      checkbox.className = "json-null-checkbox";
      checkbox.setAttribute("aria-label", `Set ${label} to null`);
      checkbox.title = "Set to null";
      checkbox.addEventListener("change", () => { if (checkbox.checked) renderNull(); });
      state.append(checkbox, active.element);
      wrapper.replaceChildren(state);
      active.element.querySelector<HTMLElement>("input, select, textarea, button")?.focus();
    });
    wrapper.replaceChildren(button);
  };
  renderNull();
  return { element: wrapper, read: () => isNull ? null : active?.read() };
}

function createObjectJsonControl(
  definition: Extract<IfxTypeDescription, { type: "object" }>,
  context: SchemaContext,
): JsonInput {
  const wrapper = document.createElement("span");
  wrapper.className = "json-composite";
  const members = createObjectMembers(definition, context);
  wrapper.append(document.createTextNode("{"), members.element, document.createTextNode("}"));
  return { element: wrapper, read: members.read };
}

function createObjectMembers(
  definition: Extract<IfxTypeDescription, { type: "object" }>,
  context: SchemaContext,
): JsonObjectMembers {
  const container = document.createElement("span");
  container.className = "json-indent";
  const controls = definition.properties.map((property) => [
    property.name,
    createJsonControl(property.type, context, property.name),
  ] as const);
  controls.forEach(([name, control], index) => {
    const row = document.createElement("span");
    row.className = "json-property";
    const key = document.createElement("span");
    key.className = "json-property-key";
    key.textContent = `${JSON.stringify(name)}: `;
    const value = document.createElement("span");
    value.className = "json-property-value";
    value.append(control.element, document.createTextNode(index < controls.length - 1 ? "," : ""));
    row.append(key, value);
    container.append(row);
  });
  return {
    element: container,
    count: controls.length,
    read: () => Object.fromEntries(controls.map(([name, control]) => [name, control.read()])),
  };
}

function createArrayJsonControl(elementType: IfxTypeReference, context: SchemaContext, label: string): JsonInput {
  const wrapper = document.createElement("span");
  wrapper.className = "json-composite";
  const rows = document.createElement("span");
  rows.className = "json-indent";
  const controls: JsonInput[] = [createJsonControl(elementType, context, `${label} item 0`)];
  const refresh = () => {
    rows.replaceChildren();
    controls.forEach((control, index) => {
      const row = document.createElement("span");
      row.className = "json-array-item";
      const value = document.createElement("span");
      value.className = "json-property-value";
      value.append(control.element, document.createTextNode(index < controls.length - 1 ? "," : ""));
      const remove = document.createElement("button");
      remove.type = "button";
      remove.className = "json-array-action";
      remove.textContent = "×";
      remove.title = "Remove item";
      remove.addEventListener("click", () => { controls.splice(index, 1); refresh(); });
      row.append(value, remove);
      rows.append(row);
    });
    const add = document.createElement("button");
    add.type = "button";
    add.className = "json-array-action";
    add.textContent = "+ item";
    add.addEventListener("click", () => {
      controls.push(createJsonControl(elementType, context, `${label} item ${controls.length}`));
      refresh();
    });
    const addRow = document.createElement("span");
    addRow.className = "json-array-item";
    addRow.append(add);
    rows.append(addRow);
  };
  refresh();
  wrapper.append(document.createTextNode("["), rows, document.createTextNode("]"));
  return { element: wrapper, read: () => controls.map((control) => control.read()) };
}

function createUnionJsonControl(
  definition: Extract<IfxTypeDescription, { type: "sealedUnion" }>,
  context: SchemaContext,
  label: string,
): JsonInput {
  const wrapper = document.createElement("span");
  wrapper.className = "json-composite";
  const body = document.createElement("span");
  body.className = "json-indent";
  const discriminatorRow = document.createElement("span");
  discriminatorRow.className = "json-property";
  const key = document.createElement("span");
  key.className = "json-property-key";
  key.textContent = `${JSON.stringify(definition.discriminator)}: `;
  const discriminator = createEnumJsonControl(definition.variants.map((variant) => variant.serialName), `${label} variant`);
  const discriminatorValue = document.createElement("span");
  discriminatorValue.className = "json-property-value";
  discriminatorRow.append(key, discriminatorValue);
  const slot = document.createElement("span");
  let variantRead: () => Record<string, unknown> = () => ({});
  const refresh = () => {
    const selected = String(discriminator.read());
    const variant = definition.variants.find((candidate) => candidate.serialName === selected) ?? definition.variants[0];
    const resolved = variant && resolveObjectReference(unionVariantType(variant), context);
    if (resolved) {
      const members = createObjectMembers(resolved.definition, resolved.context);
      members.element.style.paddingLeft = "0";
      variantRead = members.read;
      slot.replaceChildren(members.element);
      discriminatorValue.replaceChildren(discriminator.element, document.createTextNode(members.count > 0 ? "," : ""));
    } else {
      variantRead = () => ({});
      slot.replaceChildren();
      discriminatorValue.replaceChildren(discriminator.element);
    }
  };
  discriminator.element.querySelector("select")?.addEventListener("change", refresh);
  body.append(discriminatorRow, slot);
  wrapper.append(document.createTextNode("{"), body, document.createTextNode("}"));
  refresh();
  return {
    element: wrapper,
    read: () => ({ [definition.discriminator]: discriminator.read(), ...variantRead() }),
  };
}

function resolveObjectReference(reference: IfxTypeReference, context: SchemaContext): ResolvedObject | undefined {
  if (reference.type === "parameter") {
    return resolveObjectReference(context.parameters.get(reference.name) ?? { type: "string" }, context);
  }
  if (reference.type === "nullable") return resolveObjectReference(reference.value, context);
  if (reference.type !== "named") return undefined;
  const definition = context.definitions.get(reference.name);
  if (!definition) return undefined;
  const parameters = new Map(context.parameters);
  definition.typeParameters.forEach((name, index) => parameters.set(name, reference.arguments[index] ?? { type: "string" }));
  const nested: SchemaContext = { definitions: context.definitions, parameters };
  if (definition.type === "alias") return resolveObjectReference(definition.target, nested);
  if (definition.type !== "object") return undefined;
  return { definition, context: nested };
}

function unionVariantType(variant: IfxUnionVariantDescription): IfxTypeReference {
  const reference = variant.type as IfxUnionVariantDescription["type"] & { readonly type?: "named" };
  return reference.type === "named"
    ? reference
    : { type: "named", name: reference.name, arguments: reference.arguments };
}

function createRawJsonControl(initialValue: string, label: string): JsonInput {
  const input = document.createElement("textarea");
  input.className = "json-raw-input";
  input.spellcheck = false;
  input.value = initialValue;
  input.setAttribute("aria-label", label);
  return { element: input, read: () => parseJson(input.value, label) };
}

function staticJsonControl(text: string, value: unknown, className: string): JsonInput {
  const element = document.createElement("span");
  element.className = className;
  element.textContent = text;
  return { element, read: () => value };
}

function parseJson(value: string, label: string): unknown {
  try { return JSON.parse(value); }
  catch { throw new Error(`${label} must contain valid JSON`); }
}

function interactionLabel(interaction: IfxOperationDescription["interaction"]): string {
  return interaction === "requestStream" ? "stream" : interaction === "fireAndForget" ? "send" : "request";
}

function typeLabel(reference: IfxTypeReference): string {
  switch (reference.type) {
    case "named": return `${simpleName(reference.name)}${reference.arguments.length > 0 ? `<${reference.arguments.map(typeLabel).join(", ")}>` : ""}`;
    case "array": return `${typeLabel(reference.element)}[]`;
    case "record": return `Record<string, ${typeLabel(reference.value)}>`;
    case "nullable": return `${typeLabel(reference.value)} | null`;
    case "parameter": return reference.name;
    default: return reference.type;
  }
}

function exampleValue(
  reference: IfxTypeReference,
  context: SchemaContext,
  ancestors: ReadonlySet<string> = new Set(),
): unknown {
  switch (reference.type) {
    case "string": return "string";
    case "number": return 0;
    case "boolean": return true;
    case "void": return undefined;
    case "nullable": return exampleValue(reference.value, context, ancestors);
    case "array": return [exampleValue(reference.element, context, ancestors)];
    case "record": return { key: exampleValue(reference.value, context, ancestors) };
    case "parameter": return exampleValue(context.parameters.get(reference.name) ?? { type: "string" }, context, ancestors);
    case "named": break;
  }

  const definition = context.definitions.get(reference.name);
  if (!definition) return {};
  const identity = `${reference.name}<${reference.arguments.map(typeLabel).join(",")}>`;
  if (ancestors.has(identity)) return {};
  const nestedAncestors = new Set(ancestors).add(identity);
  const parameters = new Map(context.parameters);
  definition.typeParameters.forEach((name, index) => parameters.set(name, reference.arguments[index] ?? { type: "string" }));
  const nested: SchemaContext = { definitions: context.definitions, parameters };

  if (definition.type === "alias") return exampleValue(definition.target, nested, nestedAncestors);
  if (definition.type === "stringUnion") return definition.values[0] ?? "value";
  if (definition.type === "sealedUnion") {
    const variant = definition.variants[0];
    if (!variant) return {};
    const value = exampleValue(unionVariantType(variant), nested, nestedAncestors);
    return {
      [definition.discriminator]: variant.serialName,
      ...(typeof value === "object" && value !== null && !Array.isArray(value) ? value : { value }),
    };
  }
  return Object.fromEntries(definition.properties.map((property) => [
    property.name,
    exampleValue(property.type, nested, nestedAncestors),
  ]));
}

function simpleName(value: string): string { return value.split(".").at(-1) ?? value; }
function pretty(value: unknown): string { return value === undefined ? "void" : JSON.stringify(value, null, 2); }
function messageOf(error: unknown): string { return error instanceof Error ? error.message : String(error); }
function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[character]!);
}
