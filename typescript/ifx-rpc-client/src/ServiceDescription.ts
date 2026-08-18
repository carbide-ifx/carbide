import type { IfxInteraction } from "./IfxBinding";

export interface IfxServiceCatalog {
  readonly name: string;
  readonly services: readonly IfxServiceDescription[];
  readonly listeners: readonly IfxProtocolListenerDescription[];
}

export interface IfxProtocolListenerDescription {
  readonly protocolId: string;
  readonly listenerId?: string;
  readonly host: string;
  readonly port: number;
}

export interface IfxServiceDescription {
  readonly name: string;
  readonly address: string;
  readonly kind: "service" | "utility";
  readonly operations: readonly IfxOperationDescription[];
  readonly types: readonly IfxTypeDescription[];
}

export interface IfxOperationDescription {
  readonly name: string;
  readonly route: string;
  readonly parameterName: string | null;
  readonly request: IfxTypeReference;
  readonly response: IfxTypeReference;
  readonly interaction: IfxInteraction;
}

export type IfxTypeReference =
  | { readonly type: "string" }
  | { readonly type: "number" }
  | { readonly type: "boolean" }
  | { readonly type: "void" }
  | { readonly type: "parameter"; readonly name: string }
  | { readonly type: "named"; readonly name: string; readonly arguments: readonly IfxTypeReference[] }
  | { readonly type: "array"; readonly element: IfxTypeReference }
  | { readonly type: "record"; readonly value: IfxTypeReference }
  | { readonly type: "nullable"; readonly value: IfxTypeReference };

export type IfxTypeDescription =
  | {
      readonly type: "object";
      readonly name: string;
      readonly typeParameters: readonly string[];
      readonly properties: readonly IfxPropertyDescription[];
    }
  | {
      readonly type: "stringUnion";
      readonly name: string;
      readonly typeParameters: readonly string[];
      readonly values: readonly string[];
    }
  | {
      readonly type: "sealedUnion";
      readonly name: string;
      readonly typeParameters: readonly string[];
      readonly discriminator: string;
      readonly variants: readonly IfxUnionVariantDescription[];
    }
  | {
      readonly type: "alias";
      readonly name: string;
      readonly typeParameters: readonly string[];
      readonly target: IfxTypeReference;
    };

export interface IfxPropertyDescription {
  readonly name: string;
  readonly type: IfxTypeReference;
  readonly optional: boolean;
}

export interface IfxUnionVariantDescription {
  readonly serialName: string;
  readonly type: Extract<IfxTypeReference, { readonly type: "named" }>;
}
