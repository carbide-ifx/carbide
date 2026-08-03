import {
  RSocketBinding,
  type IfxOperationDescription,
  type IfxServiceCatalog,
  type IfxServiceDescription,
  type IfxTypeDescription,
  type IfxTypeReference,
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
  main { width: min(1180px, calc(100% - 40px)); margin: 0 auto; padding: 54px 0 80px; }
  .eyebrow { color: #568164; font: 700 11px/1.2 ui-monospace, monospace; letter-spacing: .14em; text-transform: uppercase; }
  h1 { margin: 9px 0 12px; font-size: clamp(34px, 5vw, 58px); line-height: .98; letter-spacing: -.055em; font-weight: 650; }
  .lede { max-width: 660px; margin: 0; color: #5f6c64; font-size: 17px; line-height: 1.55; }
  .catalog { margin-top: 42px; display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 18px; }
  .service-card { position: relative; display: block; min-height: 190px; padding: 25px; color: inherit; text-decoration: none; background: #fff; border: 1px solid #d6ddd7; box-shadow: 0 8px 28px rgba(23, 41, 31, .04); transition: transform .18s ease, border-color .18s ease, box-shadow .18s ease; }
  .service-card:hover, .service-card:focus-visible { transform: translateY(-3px); border-color: #6d9b79; box-shadow: 0 14px 34px rgba(23, 41, 31, .09); outline: none; }
  .stereotype { color: #6d7a72; font: 11px ui-monospace, monospace; }
  .service-status { position: absolute; top: 20px; right: 20px; display: inline-flex; align-items: center; gap: 8px; padding: 5px 9px; border: 1px solid #d1d9d3; border-radius: 999px; background: #f5f7f4; font-size: 10px; font-weight: 750; letter-spacing: .035em; }
  .service-status-part { display: inline-flex; align-items: center; gap: 5px; color: #6b776f; white-space: nowrap; }
  .service-status-part + .service-status-part { padding-left: 8px; border-left: 1px solid #d8dfda; }
  .service-status-part::before { content: ""; width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
  .service-status-part.positive { color: #2d7040; }
  .service-status-part.negative { color: #a04735; }
  .service-card h2 { margin: 28px 0 7px; font-size: 22px; letter-spacing: -.025em; }
  .address { overflow-wrap: anywhere; color: #718078; font: 12px/1.5 ui-monospace, monospace; }
  .operation-count { position: absolute; left: 25px; bottom: 22px; color: #3f6e4d; font-size: 12px; font-weight: 700; }
  .arrow { position: absolute; right: 24px; bottom: 20px; color: #3f6e4d; font-size: 22px; }
  .empty { margin-top: 42px; padding: 28px; background: #fff; border: 1px solid #d6ddd7; color: #68766d; }
  .back { display: inline-flex; gap: 8px; align-items: center; color: #426d4d; text-decoration: none; font-size: 13px; font-weight: 700; }
  .service-head { display: flex; justify-content: space-between; align-items: end; gap: 24px; margin-top: 30px; padding-bottom: 30px; border-bottom: 1px solid #d4dbd5; }
  .service-head h1 { font-size: clamp(34px, 4vw, 52px); }
  .service-meta { max-width: 420px; text-align: right; }
  .service-meta .address { display: block; margin-top: 6px; }
  .operations { display: grid; gap: 18px; margin-top: 28px; }
  .operation { background: #fff; border: 1px solid #d5ddd7; }
  .operation-head { display: flex; align-items: center; gap: 13px; padding: 20px 24px; border-bottom: 1px solid #e2e7e3; }
  .verb { padding: 6px 9px; background: #e9f2eb; color: #315f3e; font: 700 10px ui-monospace, monospace; text-transform: uppercase; letter-spacing: .08em; }
  .operation h2 { margin: 0; font-size: 18px; letter-spacing: -.02em; }
  .signature { margin-left: auto; color: #7a877f; font: 11px ui-monospace, monospace; }
  .operation-body { display: grid; grid-template-columns: minmax(0, 1fr) minmax(320px, .85fr); min-height: 250px; }
  .request, .response { padding: 24px; min-width: 0; }
  .response { background: #17231c; color: #dce9df; }
  .panel-title { display: flex; justify-content: space-between; margin-bottom: 18px; color: #6b786f; font: 700 10px ui-monospace, monospace; letter-spacing: .12em; text-transform: uppercase; }
  .response .panel-title { color: #8fac97; }
  .type-label { letter-spacing: 0; text-transform: none; font-weight: 500; }
  .field { display: grid; gap: 7px; margin-bottom: 14px; }
  .field > label, .legend { color: #45534a; font-size: 12px; font-weight: 700; }
  .required { color: #a34e3a; }
  input[type=text], input[type=number], select, textarea { width: 100%; border: 1px solid #cbd4cd; border-radius: 0; padding: 10px 11px; color: #17211c; background: #fbfcfa; outline: none; }
  input:focus, select:focus, textarea:focus { border-color: #4e7c5a; box-shadow: 0 0 0 3px rgba(78, 124, 90, .11); }
  textarea { min-height: 90px; resize: vertical; font: 12px/1.5 ui-monospace, monospace; }
  fieldset { min-width: 0; margin: 0 0 14px; padding: 16px; border: 1px solid #dde3de; }
  legend { padding: 0 7px; }
  .toggle { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; color: #56645b; font-size: 12px; }
  .array-items { display: grid; gap: 10px; }
  .array-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; align-items: start; }
  .small-button { border: 1px solid #c7d2c9; padding: 8px 10px; background: white; color: #456350; font-size: 12px; }
  .invoke { display: inline-flex; align-items: center; gap: 9px; border: 0; padding: 11px 17px; background: #29633a; color: #fff; font-weight: 750; font-size: 13px; }
  .invoke:hover { background: #1d502d; }
  .invoke:disabled { cursor: wait; opacity: .6; }
  .result { margin: 0; min-height: 145px; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; color: #cae0cf; font: 12px/1.65 ui-monospace, monospace; }
  .result.muted { color: #7e9585; }
  .result.error { color: #ffb7a6; }
  .response-tools { display: flex; gap: 8px; align-items: center; }
  .cancel { border: 1px solid #4e6255; padding: 5px 8px; background: transparent; color: #b9cabe; font-size: 11px; }
  .status { color: #91a597; font-size: 11px; text-transform: none; letter-spacing: 0; }
  @media (max-width: 760px) { .topbar { padding: 0 20px; } main { width: min(100% - 28px, 1180px); padding-top: 36px; } .service-head { display: block; } .service-meta { margin-top: 18px; text-align: left; } .operation-body { grid-template-columns: 1fr; } .signature { display: none; } }
`;

const OMIT = Symbol("omit");
const I_SERVICE_OPERATIONS = new Set(["status", "init", "isReady", "isLive"]);
type ReadValue = () => unknown | typeof OMIT;
interface Control { readonly element: HTMLElement; readonly read: ReadValue }
interface SchemaContext { readonly definitions: ReadonlyMap<string, IfxTypeDescription>; readonly parameters: ReadonlyMap<string, IfxTypeReference> }

const appElement = document.querySelector<HTMLElement>("#app");
if (!appElement) throw new Error("Missing #app element");
const app: HTMLElement = appElement;
document.head.append(Object.assign(document.createElement("style"), { textContent: styles }));

void start();

async function start(): Promise<void> {
  try {
    const response = await fetch("/ifx/services");
    if (!response.ok) throw new Error(`Service catalog returned HTTP ${response.status}`);
    const catalog = await response.json() as IfxServiceCatalog;
    const render = () => renderRoute(catalog);
    window.addEventListener("hashchange", render);
    render();
  } catch (error) {
    app.innerHTML = `<div class="shell"><header class="topbar"><div class="brand"><span class="mark">iFX</span> Service Explorer</div></header><main><div class="empty">${escapeHtml(messageOf(error))}</div></main></div>`;
  }
}

function renderRoute(catalog: IfxServiceCatalog): void {
  const match = /^#\/services\/(.+)$/.exec(location.hash);
  const service = match && catalog.services.find((item) => item.address === decodeURIComponent(match[1]));
  if (service) renderService(catalog, service);
  else renderCatalog(catalog);
}

function chrome(catalog: IfxServiceCatalog, body: string): void {
  app.innerHTML = `<div class="shell"><header class="topbar"><div class="brand"><span class="mark">iFX</span> Service Explorer</div><div class="host-name">${escapeHtml(catalog.name)}</div></header><main>${body}</main></div>`;
}

function renderCatalog(catalog: IfxServiceCatalog): void {
  const cards = catalog.services.map((service, index) => {
    const operationCount = visibleOperations(service).length;
    return `
    <a class="service-card" data-service-index="${index}" href="#/services/${encodeURIComponent(service.address)}">
      <div class="stereotype">&laquo;service component&raquo;</div>
      <span class="service-status"><span class="service-status-part">Ready …</span><span class="service-status-part">Live …</span></span>
      <h2>${escapeHtml(service.name)}</h2>
      <div class="address">${escapeHtml(service.address)}</div>
      <div class="operation-count">${operationCount} operation${operationCount === 1 ? "" : "s"}</div>
      <div class="arrow">→</div>
    </a>`;
  }).join("");
  chrome(catalog, `
    <div class="eyebrow">System composition</div>
    <h1>${escapeHtml(catalog.name)}</h1>
    <p class="lede">Inspect the service boundary, compose a valid request from its contract, and invoke the running system directly.</p>
    ${cards ? `<section class="catalog" aria-label="Service components">${cards}</section>` : `<div class="empty">No services are registered on this host.</div>`}`);

  for (const card of app.querySelectorAll<HTMLElement>(".service-card[data-service-index]")) {
    const service = catalog.services[Number(card.dataset.serviceIndex)];
    const pill = card.querySelector<HTMLElement>(".service-status");
    if (service && pill) void loadServiceStatus(service, pill);
  }
}

function renderService(catalog: IfxServiceCatalog, service: IfxServiceDescription): void {
  chrome(catalog, `
    <a class="back" href="#/">← All service components</a>
    <section class="service-head">
      <div><div class="eyebrow">Service component</div><h1>${escapeHtml(service.name)}</h1></div>
      <div class="service-meta"><span class="stereotype">RSocket endpoint</span><span class="address">${escapeHtml(service.address)}</span></div>
    </section>
    <section class="operations" aria-label="Operations"></section>`);

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

function visibleOperations(service: IfxServiceDescription): readonly IfxOperationDescription[] {
  return service.operations.filter((operation) => !I_SERVICE_OPERATIONS.has(operation.name));
}

async function loadServiceStatus(service: IfxServiceDescription, pill: HTMLElement): Promise<void> {
  const operation = service.operations.find((candidate) => candidate.name === "status");
  if (!operation) {
    setServiceStatus(pill, null, null);
    return;
  }

  let binding: RSocketBinding | undefined;
  try {
    const scheme = location.protocol === "https:" ? "wss:" : "ws:";
    binding = await RSocketBinding.connect({
      url: RSocketBinding.serviceUrl(`${scheme}//${location.host}`, service.address),
    });
    const status = await binding.requestResponse<{ readonly ready: boolean; readonly live: boolean }>(operation.route);
    setServiceStatus(pill, status.ready, status.live);
  } catch {
    setServiceStatus(pill, null, null);
  } finally {
    binding?.close();
  }
}

function setServiceStatus(pill: HTMLElement, ready: boolean | null, live: boolean | null): void {
  pill.replaceChildren(statusPart("Ready", ready), statusPart("Live", live));
}

function statusPart(label: string, value: boolean | null): HTMLSpanElement {
  const part = document.createElement("span");
  part.className = `service-status-part ${value === null ? "" : value ? "positive" : "negative"}`.trim();
  part.textContent = value === null ? `${label} ?` : value ? label : `Not ${label.toLowerCase()}`;
  return part;
}

function renderOperation(
  service: IfxServiceDescription,
  operation: IfxOperationDescription,
  definitions: ReadonlyMap<string, IfxTypeDescription>,
): HTMLElement {
  const article = document.createElement("article");
  article.className = "operation";
  article.innerHTML = `
    <header class="operation-head"><span class="verb">${interactionLabel(operation.interaction)}</span><h2>${escapeHtml(operation.name)}</h2><span class="signature">${escapeHtml(operation.route)}</span></header>
    <div class="operation-body">
      <section class="request"><div class="panel-title"><span>Request</span><span class="type-label">${escapeHtml(typeLabel(operation.request))}</span></div><div class="form"></div><button class="invoke" type="button">Invoke <span>→</span></button></section>
      <section class="response"><div class="panel-title"><span>Response</span><span class="response-tools"><span class="status">Not invoked</span><button class="cancel" type="button" hidden>Cancel stream</button></span></div><pre class="result muted">The response will appear here.</pre></section>
    </div>`;

  const form = article.querySelector<HTMLElement>(".form")!;
  const button = article.querySelector<HTMLButtonElement>(".invoke")!;
  const cancel = article.querySelector<HTMLButtonElement>(".cancel")!;
  const result = article.querySelector<HTMLElement>(".result")!;
  const status = article.querySelector<HTMLElement>(".status")!;
  const context: SchemaContext = { definitions, parameters: new Map() };
  const control = createControl(operation.request, context, operation.parameterName ?? "request");
  if (operation.parameterName) form.append(control.element);
  else form.innerHTML = `<p class="address">This operation has no request body.</p>`;

  let binding: RSocketBinding | undefined;
  let invocation = 0;
  cancel.addEventListener("click", () => {
    invocation += 1;
    binding?.close();
    binding = undefined;
    button.disabled = false;
    cancel.hidden = true;
    status.textContent = "Cancelled";
  });
  button.addEventListener("click", async () => {
    const current = ++invocation;
    let callBinding: RSocketBinding | undefined;
    button.disabled = true;
    cancel.hidden = operation.interaction !== "requestStream";
    result.className = "result muted";
    result.textContent = "Connecting…";
    status.textContent = "Running";
    try {
      const request = control.read();
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
        result.textContent = pretty(value);
      } else {
        result.textContent = "";
        let count = 0;
        const stream = operation.parameterName
          ? callBinding.requestStream(operation.route, request)
          : callBinding.requestStream(operation.route);
        for await (const value of stream) {
          if (current !== invocation) break;
          result.textContent += `${count ? "\n\n" : ""}[${++count}] ${pretty(value)}`;
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
        button.disabled = false;
        cancel.hidden = true;
      }
    }
  });
  return article;
}

function createControl(reference: IfxTypeReference, context: SchemaContext, label: string, optional = false): Control {
  const core = createRequiredControl(reference, context, label);
  if (!optional) return core;
  const wrapper = document.createElement("div");
  const toggle = document.createElement("label");
  toggle.className = "toggle";
  const include = Object.assign(document.createElement("input"), { type: "checkbox" });
  toggle.append(include, document.createTextNode(` Include optional ${label}`));
  core.element.hidden = true;
  include.addEventListener("change", () => { core.element.hidden = !include.checked; });
  wrapper.append(toggle, core.element);
  return { element: wrapper, read: () => include.checked ? core.read() : OMIT };
}

function createRequiredControl(reference: IfxTypeReference, context: SchemaContext, label: string): Control {
  if (reference.type === "nullable") {
    const wrapper = document.createElement("div");
    const toggle = document.createElement("label");
    toggle.className = "toggle";
    const sendNull = Object.assign(document.createElement("input"), { type: "checkbox" });
    toggle.append(sendNull, document.createTextNode(` Send ${label} as null`));
    const inner = createRequiredControl(reference.value, context, label);
    sendNull.addEventListener("change", () => { inner.element.hidden = sendNull.checked; });
    wrapper.append(toggle, inner.element);
    return { element: wrapper, read: () => sendNull.checked ? null : inner.read() };
  }
  if (reference.type === "parameter") {
    return createRequiredControl(context.parameters.get(reference.name) ?? { type: "string" }, context, label);
  }
  if (reference.type === "string" || reference.type === "number") {
    const wrapper = field(label);
    const input = document.createElement("input");
    input.type = reference.type === "number" ? "number" : "text";
    if (reference.type === "number") input.step = "any";
    wrapper.append(input);
    return { element: wrapper, read: () => reference.type === "number" ? requiredNumber(input, label) : input.value };
  }
  if (reference.type === "boolean") {
    const wrapper = field(label);
    const select = document.createElement("select");
    select.append(new Option("true", "true"), new Option("false", "false"));
    wrapper.append(select);
    return { element: wrapper, read: () => select.value === "true" };
  }
  if (reference.type === "void") return { element: document.createElement("div"), read: () => undefined };
  if (reference.type === "record") {
    const wrapper = field(label);
    const textarea = document.createElement("textarea");
    textarea.value = "{}";
    wrapper.append(textarea);
    return { element: wrapper, read: () => parseJson(textarea.value, label) };
  }
  if (reference.type === "array") return arrayControl(reference.element, context, label);

  const definition = context.definitions.get(reference.name);
  if (!definition) {
    const wrapper = field(label);
    const textarea = document.createElement("textarea");
    textarea.value = "{}";
    wrapper.append(textarea);
    return { element: wrapper, read: () => parseJson(textarea.value, label) };
  }
  const parameters = new Map(context.parameters);
  definition.typeParameters.forEach((name, index) => parameters.set(name, reference.arguments[index] ?? { type: "string" }));
  const nested = { definitions: context.definitions, parameters };
  if (definition.type === "alias") return createRequiredControl(definition.target, nested, label);
  if (definition.type === "stringUnion") {
    const wrapper = field(label);
    const select = document.createElement("select");
    for (const value of definition.values) select.append(new Option(value, value));
    wrapper.append(select);
    return { element: wrapper, read: () => select.value };
  }
  if (definition.type === "sealedUnion") return unionControl(definition, nested, label);
  return objectControl(definition, nested, label);
}

function objectControl(definition: Extract<IfxTypeDescription, { type: "object" }>, context: SchemaContext, label: string): Control {
  const group = document.createElement("fieldset");
  group.innerHTML = `<legend class="legend">${escapeHtml(label)} <span class="address">${escapeHtml(simpleName(definition.name))}</span></legend>`;
  const controls = definition.properties.map((property) => {
    const control = createControl(property.type, context, property.name, property.optional);
    group.append(control.element);
    return [property.name, control] as const;
  });
  return {
    element: group,
    read: () => Object.fromEntries(controls.flatMap(([name, control]) => {
      const value = control.read();
      return value === OMIT ? [] : [[name, value]];
    })),
  };
}

function unionControl(definition: Extract<IfxTypeDescription, { type: "sealedUnion" }>, context: SchemaContext, label: string): Control {
  const group = document.createElement("fieldset");
  group.innerHTML = `<legend class="legend">${escapeHtml(label)} <span class="address">${escapeHtml(simpleName(definition.name))}</span></legend>`;
  const selectorField = field("Variant");
  const select = document.createElement("select");
  definition.variants.forEach((variant, index) => select.append(new Option(simpleName(variant.serialName), String(index))));
  selectorField.append(select);
  const slot = document.createElement("div");
  group.append(selectorField, slot);
  let active = createRequiredControl(definition.variants[0]?.type ?? { type: "record", value: { type: "string" } }, context, "Fields");
  const refresh = () => {
    const variant = definition.variants[Number(select.value)];
    active = createRequiredControl(variant.type, context, "Fields");
    slot.replaceChildren(active.element);
  };
  select.addEventListener("change", refresh);
  refresh();
  return {
    element: group,
    read: () => {
      const variant = definition.variants[Number(select.value)];
      return { [definition.discriminator]: variant.serialName, ...(active.read() as object) };
    },
  };
}

function arrayControl(elementType: IfxTypeReference, context: SchemaContext, label: string): Control {
  const group = document.createElement("fieldset");
  group.innerHTML = `<legend class="legend">${escapeHtml(label)} <span class="address">array</span></legend>`;
  const rows = document.createElement("div");
  rows.className = "array-items";
  const controls: Control[] = [];
  const add = document.createElement("button");
  add.type = "button";
  add.className = "small-button";
  add.textContent = "+ Add item";
  add.addEventListener("click", () => {
    const row = document.createElement("div");
    row.className = "array-row";
    const control = createRequiredControl(elementType, context, `Item ${controls.length + 1}`);
    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "small-button";
    remove.textContent = "Remove";
    remove.addEventListener("click", () => { controls.splice(controls.indexOf(control), 1); row.remove(); });
    controls.push(control);
    row.append(control.element, remove);
    rows.append(row);
  });
  group.append(rows, add);
  return { element: group, read: () => controls.map((control) => control.read()) };
}

function field(label: string): HTMLDivElement {
  const wrapper = document.createElement("div");
  wrapper.className = "field";
  const element = document.createElement("label");
  element.textContent = label;
  wrapper.append(element);
  return wrapper;
}

function requiredNumber(input: HTMLInputElement, label: string): number {
  if (input.value.trim() === "") throw new Error(`${label} is required`);
  const value = Number(input.value);
  if (!Number.isFinite(value)) throw new Error(`${label} must be a number`);
  return value;
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
    case "named": return simpleName(reference.name);
    case "array": return `${typeLabel(reference.element)}[]`;
    case "record": return `Record<string, ${typeLabel(reference.value)}>`;
    case "nullable": return `${typeLabel(reference.value)} | null`;
    case "parameter": return reference.name;
    default: return reference.type;
  }
}

function simpleName(value: string): string { return value.split(".").at(-1) ?? value; }
function pretty(value: unknown): string { return value === undefined ? "void" : JSON.stringify(value, null, 2); }
function messageOf(error: unknown): string { return error instanceof Error ? error.message : String(error); }
function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[character]!);
}
