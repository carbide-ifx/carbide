
# iFX.kotlin
- 
- ifx.Kotlin
  - Build conventions / templates
    -  √ Experiment: JetBrains Amper
    - Gradle  
  - √ Service Hosting
    - Config mgmt
  - √ Logging
    - MDC
  - Naming conventions enforcement
  - √ Serialization
  - √ Proxy
    - Experiment: Transparent proxy
  - Protocols
    - √ RSocket
    - JSON-RPC
    - GRPC
    - √ Invocation
      - √ sync 
      - √ async
      - √ streaming  
  - Message Bus
    - √ In-memory
    - √ Azure
    - GCP
    - Amazon
  - Extensibility pipeline:
    - √ Context Propagation
    - √ Request / Response logging
    - Observability
    - Telemetry
    - Encryption
    - Distributed Tracing
    - Reliable messaging (Maybe part of protocol instead?)
  - Javascript SDK (generated)
  - Distributed Transactions
  - Test Harness and tooling
    - Test client (GUI?)
  - Workflow support (Camunda etc)

- ifx.cloud


- ifx.office
  - Interviewing guidelines / template
  - System Design Report template
  - Project Design Report template 
  - Figma / Visio / LucidChart tooling


Service discovery
Test client with UI
/Transparent/ proxy






## Rationale 
In the name of efficiency, effectiveness and productivity:  
   
* Improve consistency  
* Enforce policy  
* Lower the bar of entry  
* Remove boilerplate  
* Sand down rough edges  
* Extract razorblades

###  iFX should
-   Mitigate technology as a risk
-   Wrap best practice usage (consistent+repeatable)
-   Enforce policy
-   Lower the bar of entry for Dev Community
-   Demystify consumption
-   Convention over configuration


# What is it
Infrastructure. For code
 -  Security 
-   Logging 
-   Diagnostics 
-   Setup 
-   Instrumentation 
-   Control and administration
-   Invocation
-   Etc
![[Pasted image 20221025080817.png]]
![[Pasted image 20221025080417.png]]

## Components
Hand off point
Framework for running and testing services
Communication layer - isolate business (service) code
Formalized guidelines - contstraints
Hosting
Flow
Rules
Security

![[Pasted image 20221025080516.png]]


![[Pasted image 20221025080654.png]]


## Know your team
-   Never throw it over the wall
	-   You, not them, are responsible if they get it wrong
	-   You must train them…
-   In the end (as always), it’s all about planning




# Requirements for us:

## Platforms
JVM
.NET
(some way to call out to python)

## Communication
Request/response
Fire-and forget
Streaming

### Programming Model
 - State
 - Raise event (pubsub/queue)
 - Fire and forget call
 - Request/response
 - Streaming

ProxyFactory
	Invocation
	Call
	Serve

## Hosting
Endpoints

#### Protocols
RSocket
JSON-RPC
gRPC?



- Platforms
Dapr/Kubernets
Local single executable
Net
Jvm


- Message bus
- Workflow


```
Encrypted calls
Authentication
Identity propagation
Authorization
Security audits
Transactions propagation
Transactions voting 
Calls timeout
Reliability
Tracing and logging
Profiling and instrumentation
Instance management
Durability
Error masking
Fault isolation
Channel faulting
Buffering and throttling
Data versioning tolerance
Synchronization and synchronization context 
MBV
Remotability
Interoperability 
Queuing
Service bus
Discovery
```


### Project level iFx - customizations.







# Feature list:
1. Encrypted calls  
2. Authentication  
3. Identity propagation  
4. Authorization  
5. Security audit 
6. Transactions propagation  
7. Transactions voting  
8. Calls timeout  
9. Reliability  
10. Tracing and logging  
11. Profiling and instrumentation  
12. Instance management  
13. Durability  
14. Error masking  
15. Fault isolation  
16. Channel faulting  
17. Buffering and throttling  
18. Data versioning tolerance  
19. Synchronization and synchronization context  
20. MBV  
21. Remotability  
22. Interoperability  
23. Queuing  
24. Service bus  
25. Discovery




Serialization
![[Pasted image 20221031111311.png]]

![[Pasted image 20221031114433.png]]
