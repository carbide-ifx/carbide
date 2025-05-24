@file:OptIn(ExperimentalMetadataApi::class)

package ifx.rsocket.impl

import ifx.proxy.rsocket.ProxyFactory
import ifx.rsocket.Payload
import ifx.rsocket.read
import ifx.rsocket.rsocket.FloatPair
import ifx.rsocket.rsocket.IMyService
import ifx.rsocket.rsocket.IntPair
import io.kotest.common.runBlocking
import io.rsocket.kotlin.ExperimentalMetadataApi
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.payload.Payload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.ExperimentalSerializationApi

@OptIn(ExperimentalSerializationApi::class, ExperimentalMetadataApi::class)
class HandcodedClient(private val rSocket: RSocket = ProxyFactory.buildRsocket("IMyService")) : IMyService {
    override suspend fun hello() = rSocket.fireAndForget(Payload("hello()"))
    override fun blockingHello() = runBlocking { rSocket.fireAndForget(Payload("blockingHello()")) }

    override suspend fun exception(): Boolean = rSocket
        .requestResponse(Payload("exception():Boolean"))
        .read()

    override fun blockingException():Boolean = runBlocking { rSocket
        .requestResponse(Payload("blockingException():Boolean"))
        .read()
    }

    override suspend fun add(pair: IntPair): Int = rSocket
        .requestResponse(Payload("add(IntPair):Int", pair))
        .read()

    override suspend fun add(pair: FloatPair): Float = rSocket
        .requestResponse(Payload("add(FloatPair):Float", pair))
        .read()

    override suspend fun stream(): Flow<List<Int>> = rSocket
        .requestStream(Payload("stream():Flow"))
        .map<Payload, List<Int>> { it.read() }


    override fun blockingAdd(pair: IntPair): Int = runBlocking { rSocket
        .requestResponse(Payload("blockingAdd(IntPair):Int", pair))
        .read()
    }

    override fun blockingStream(): Flow<List<Int>> = runBlocking { rSocket
        .requestStream(Payload("blockingStream():Flow"))
        .map { it.read() }
    }

    override fun blockingList(): List<Int> {
        TODO("Not yet implemented")
    }

    override suspend fun list(): List<Int> {
        TODO("Not yet implemented")
    }
}

