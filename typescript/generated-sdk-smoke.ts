import { JsonRpcSdk } from "@ifx/rpc-sdk-jsonrpc";
import { RSocketSdk } from "@ifx/rpc-sdk-rsocket";
import {
  type Bike,
  type Car,
  type FindByIdRequest,
  type IProductAccess,
  type ProductId,
  IProductAccessSdk,
} from "../build/generated/test.test-system/main/resources/ksp/access/product/contract/IProductAccess";

type Assert<T extends true> = T;
type IsExact<Left, Right> =
  (<Value>() => Value extends Left ? 1 : 2) extends
  (<Value>() => Value extends Right ? 1 : 2) ? true : false;

type ProductIdUsesItsSerializedWireType = Assert<IsExact<ProductId, string>>;
type OperationRequestPreservesDtoType = Assert<IsExact<IProductAccess.FindByIdRequest, FindByIdRequest>>;

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
const productIdUsesItsSerializedWireType: ProductIdUsesItsSerializedWireType = true;
void productIdUsesItsSerializedWireType;
const operationRequestPreservesDtoType: OperationRequestPreservesDtoType = true;
void operationRequestPreservesDtoType;
