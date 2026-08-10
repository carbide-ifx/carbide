import { JsonRpcClient } from "@ifx/rpc-client-jsonrpc";
import { RSocketClient } from "@ifx/rpc-client-rsocket";
import {
  type Bike,
  type Car,
  IProductAccessClient,
} from "../build/generated/test.test-system/main/resources/ksp/access/product/contract/IProductAccess";

const dependencyDtoShapes: [Bike, Car] = [
  { id: "bike-1", numGears: 12 },
  { id: "car-1", brand: "Volvo", color: "blue" },
];

async function chooseProtocol(): Promise<void> {
  const rsocket = await RSocketClient.connect(IProductAccessClient, "ws://localhost:7000");
  const jsonRpc = await JsonRpcClient.connect(IProductAccessClient, "http://localhost:7001");

  rsocket.close();
  jsonRpc.close();
}

void chooseProtocol;
void dependencyDtoShapes;
