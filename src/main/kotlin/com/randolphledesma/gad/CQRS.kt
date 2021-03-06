package com.randolphledesma.gad

import com.randolphledesma.gad.util.ConfigurationKeyList
import com.randolphledesma.gad.util.logger
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.predicate.ResponsePredicate
import io.vertx.kotlin.coroutines.awaitResult

import java.lang.RuntimeException
import javax.inject.Singleton

/* Format
application/vnd.eventstore.events+json
{
    "eventId"    : "string",
    "eventType"  : "string",
    "data"       : "object",
    "metadata"   : "object"
}
*/

/**
 * TODO: exclude null metadata when marshalling it as JSON payload
 */

data class EventStoreEvent(val eventId: String, val eventType: String, val data: JsonObject, val metadata: JsonObject? = null) {
    fun encode(): String? {
        return JsonObject.mapFrom(this).encode()
    }
}

data class EventStoreResource(val stream: String, val event: EventStoreEvent) {
    fun encode(): String? {
        return JsonObject.mapFrom(this).encode()
    }
}

class EventStoreStreamWriteException : RuntimeException {
    constructor(message: String, ex: Exception?) : super(message, ex)

    constructor(message: String) : super(message)

    constructor(ex: Exception) : super(ex)
}

class EventStoreEntityException : RuntimeException {
    constructor(message: String, ex: Exception?) : super(message, ex)

    constructor(message: String) : super(message)

    constructor(ex: Exception) : super(ex)
}

@Singleton
class EventStoreClient(private val client: WebClient, private val config: JsonObject) {
    private val LOG by logger()

    @Throws(EventStoreEntityException::class, EventStoreStreamWriteException::class)
    suspend fun writeToStream(data: EventStoreResource): String {
        val host = config.getString(ConfigurationKeyList.ES_HOST.name, "127.0.0.1")
        val port = config.getInteger(ConfigurationKeyList.ES_PORT.name, 6379)
        val timeout = config.getLong(ConfigurationKeyList.ES_DISPATCH_TIMEOUT.name, 10000)
        val user = config.getString(ConfigurationKeyList.ES_USER.name, "admin")
        val password = config.getString(ConfigurationKeyList.ES_PASSWORD.name, "changeit")

        if (data.event.eventType.isNullOrBlank()) {
            throw EventStoreEntityException("Invalid EventType")
        }

        if (data.event.eventId.isNullOrBlank()) {
            throw EventStoreEntityException("Invalid EventId")
        }

        val url = "http://$host:$port/streams/${data.stream}"
        try {
            val request = client.postAbs(url)
                .basicAuthentication(user, password)
                .timeout(timeout)
                .putHeader("Content-Type", "application/json")
                .putHeader("ES-EventType", data.event.eventType)
                .putHeader("ES-EventId", data.event.eventId)
                .expect(ResponsePredicate.SC_SUCCESS)
                .expect(
                    ResponsePredicate.contentType(
                        listOf(
                            "application/json",
                            "application/json; charset=utf-8",
                            "text/plain; charset=utf-8"
                        )
                    )
                )
            val buffer = Buffer.buffer(data.encode())
            val result = awaitResult<HttpResponse<Buffer>> { handler ->
                request.sendBuffer(buffer, handler)
            }
            return result.getHeader("Location")
                ?: throw EventStoreStreamWriteException("Invalid Location response header")
        } catch (error: EventStoreStreamWriteException) {
            throw error
        } catch (error: Throwable) {
            LOG.debug(error.message, error)
        }
        return ""
    }
}

data class DataStoreCell(val added_id: Int, val row_key: String, val column_name: String, val ref_key: Int, val body: ByteArray, val created_at: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DataStoreCell

        if (added_id != other.added_id) return false
        if (row_key != other.row_key) return false
        if (column_name != other.column_name) return false
        if (ref_key != other.ref_key) return false
        if (!body.contentEquals(other.body)) return false
        if (created_at != other.created_at) return false

        return true
    }

    override fun hashCode(): Int {
        var result = added_id
        result = 31 * result + row_key.hashCode()
        result = 31 * result + column_name.hashCode()
        result = 31 * result + ref_key
        result = 31 * result + body.contentHashCode()
        result = 31 * result + created_at.hashCode()
        return result
    }
}