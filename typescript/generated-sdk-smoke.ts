import { JsonRpcSdk } from "@ifx/rpc-sdk-jsonrpc";
import { RSocketSdk } from "@ifx/rpc-sdk-rsocket";
import {
  type Bike,
  type Car,
  IProductAccessSdk,
} from "../build/generated/test.test-system/main/resources/ksp/access/product/contract/IProductAccess";

const dependencyDtoShapes: [Bike, Car] = [
  { id: "bike-1", numGears: 12 },
  { id: "car-1", brand: "Volvo", color: "blue" },
];

async function chooseProtocol(): Promise<void> {
  const rsocket = await RSocketSdk.connect(IProductAccessSdk, "ws://localhost:7000");
  const jsonRpc = await JsonRpcSdk.connect(IProductAccessSdk, "http://localhost:7001");

  rsocket.close();
  jsonRpc.close();
}

void chooseProtocol;
void dependencyDtoShapes;
