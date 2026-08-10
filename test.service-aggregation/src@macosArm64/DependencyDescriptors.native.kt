package test.service.aggregation

import ifx.generated.TestServiceAggregationServiceDescriptors
import ifx.protocol.contract.ServiceDescriptorRegistry

/** Compile-time proof that dependency contracts are aggregated for Native. */
val dependencyServiceDescriptors: ServiceDescriptorRegistry =
    TestServiceAggregationServiceDescriptors
