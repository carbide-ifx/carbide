import { Buffer as NodeBuffer } from "buffer";

// rsocket-js 1.0.0-alpha.3 expects Node's Buffer global, including in its WebSocket transport.
const bufferGlobal = globalThis as typeof globalThis & { Buffer?: typeof NodeBuffer };
bufferGlobal.Buffer ??= NodeBuffer;

export const Buffer = NodeBuffer;
