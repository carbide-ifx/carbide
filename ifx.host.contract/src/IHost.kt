package ifx.host

import ifx.protocol.contract.IInterceptor
import ifx.protocol.contract.IProtocol
import ifx.service.IService
import kotlin.reflect.KClass


interface IHost {

    fun <T : IService> registerService(contract: KClass<T>, instance: T): IHost
    fun <T : IService> registerService(contract: KClass<T>, factory: () -> T): IHost

    fun open(): IHost
    fun close(): IHost

    companion object {
        inline fun <reified T : IService> IHost.registerService(instance: T): IHost =
            registerService(T::class, instance)

        inline fun <reified T : IService> IHost.registerService(noinline factory: () -> T): IHost =
            registerService(T::class, factory)
    }
    fun addInterceptors(vararg i: IInterceptor): IHost
    fun addInterceptors(interceptors: List<IInterceptor>): IHost
    val interceptors: List<IInterceptor>
    val protocol: IProtocol
}


/*
// Create a ServiceHost
ServiceHost host = new ServiceHost(typeof(MyService));

// Add an endpoint
host.AddServiceEndpoint(
    typeof(IMyService),
    new BasicHttpBinding(),
    "http://localhost:8080/MyService"
);

// Open the host
host.Open();
Console.WriteLine("Service is running...");
Console.ReadKey();

// Close the host
host.Close();

Host Responsibilities
The WCF host handles:

Service instantiation - Creating service objects when needed
Endpoint management - Managing multiple endpoints and their bindings
Message dispatching - Routing incoming messages to appropriate service methods
Security context - Managing authentication and authorization
Transaction coordination - Handling distributed transactions if configured
Error handling - Managing faults and exceptions
Resource cleanup - Disposing of service instances and resources

ServiceHost Class
The ServiceHost class is the primary host implementation, providing methods like:

Open() - Start accepting requests
Close() - Gracefully shut down
Abort() - Forcefully terminate
AddServiceEndpoint() - Configure endpoints programmatically

 */
