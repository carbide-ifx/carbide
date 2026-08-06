import { JsonRpcClient } from "@ifx/rpc-client-jsonrpc";
import { RSocketClient } from "@ifx/rpc-client-rsocket";
import { IProductAccessClient } from "../build/generated/test.test-system/main/resources/ksp/access/product/contract/IProductAccess";

async function chooseProtocol(): Promise<void> {
  const rsocket = await RSocketClient.connect(IProductAccessClient, "ws://localhost:7000");
  const jsonRpc = await JsonRpcClient.connect(IProductAccessClient, "http://localhost:7001");

  rsocket.close();
  jsonRpc.close();
}

void chooseProtocol;
