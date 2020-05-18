package com.randolphledesma.gad

import com.randolphledesma.gad.managers.ContactServiceManager
import com.randolphledesma.gad.models.Account
import com.randolphledesma.gad.models.Contact
import com.randolphledesma.gad.models.ContactGroup
import com.randolphledesma.gad.util.*
import io.vertx.core.http.HttpHeaders.CONTENT_TYPE
import io.vertx.core.http.HttpMethod.*
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.ext.sql.*
import java.text.SimpleDateFormat
import java.util.*

import javax.inject.Inject

class MainController @Inject constructor(val applicationContext: ApplicationContext) {
    private val contactServiceManager = ContactServiceManager(applicationContext)
    private val redis = applicationContext.redis
    private val LOG by logger()

    fun create(): Router = Router.router(applicationContext.vertx).apply {
        val cors = CorsHandler.create("*")
            .allowedHeader(CONTENT_TYPE.toString())
            .allowedMethods(setOf(GET, POST, PUT, DELETE, OPTIONS))
        route().handler(cors)

        route().last().handler { context ->
            with(context.response()) {
                statusCode = HttpStatus.NotFound.code
                end()
            }
        }

        get("/").handler { context ->
            val now = SimpleDateFormat("MM-dd", Locale.getDefault()).format( Date() )
            if (now == "04-01") {
                with(context.response()) {
                    statusCode = HttpStatus.IAmATeapot.code
                    end("April Fools: I'm a teapot")
                }
            } else {
                with(context.response()) {
                    statusCode = HttpStatus.OK.code
                    end(applicationContext.configuration.getString(ConfigurationKeyList.APP_GREETING.name))
                }
            }
        }

        route("/uuid").coroutineHandler { ctx -> getUUID(ctx) }
        route("/health").coroutineHandler { ctx -> health(ctx) }
        route("/es").coroutineHandler { ctx -> eventStore(ctx) }

        route().last().failureHandler { errorContext ->
            val e: Throwable? = errorContext.failure()
            if (e != null) {
                LOG.error(e.message, e)
            }
            val code = when (e) {
                is EventStoreEntityException -> HttpStatus.UnprocessableEntity.code
                else ->
                    if (errorContext.statusCode()>0) {
                        errorContext.statusCode()
                    } else {
                        HttpStatus.InternalServerError.code
                    }
            }
            with(errorContext.response()) {
                statusCode = code
                val result = mapOf(
                    "status" to code,
                    "error" to errorContext.failure().message
                )
                endWithJson(result)
            }
        }
    }

    private suspend fun getUUID(ctx: RoutingContext) {
        var uuid = UUID.randomUUID().toString()
        val r = json {
            obj("uuid" to uuid, "emoji" to "Hello 😀")
        }

        val connection = applicationContext.dbClient.getConnectionAwait()
        connection.inTransaction {
            val sqlDs = "INSERT INTO datastore (row_key,column_name,ref_key,body) VALUES (?,?,?,?)"
            val paramsDs = json {
                array(uuid, "base", 0, r.encode().toByteArray().gzip())
            }
            updateWithParamsAwait(sqlDs, paramsDs)
            val sqlAcccount = "INSERT INTO accounts (row_key) VALUES (?)"
            val paramsAcccount = json {
                array(uuid)
            }
            updateWithParamsAwait(sqlAcccount, paramsAcccount)
            val sqlUser = "INSERT INTO users (row_key, email, password) VALUES (?,?,?)"
            val paramsUser = json {
                array(uuid, "test@email.com", "test")
            }
            updateWithParamsAwait(sqlUser, paramsUser)
        }

        val connection2 = applicationContext.dbClient.getConnectionAwait()
        val sql = "SELECT * FROM datastore WHERE row_key='$uuid'"
        val res = connection2.queryAwait(sql)
        val created_at = res.rows[0].getString("created_at").toZuluDateTime()

        println(created_at.toPhilippinesDateTime())

        //println(res.rows[0].getBinary("body").ungzip().toString())
        //val created_at = res.rows[0].getString("created_at").toDateString().toZuluDateTime()
        ctx.response().endWithJson(r)
    }

    private suspend fun health(ctx: RoutingContext) {
        val now = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format( Date() )
        ctx.response().end("$now")
    }

    private suspend fun locking(ctx: RoutingContext) {
        try {
            val lockID = redis.acquireLockWithTimeout("test", 1000, 15000)
            println(lockID)

            val lockID2 = redis.acquireLockWithTimeout("test", 1000, 15000)
            println(lockID2)
        }
        catch(e: RuntimeException) {
            println(e)
        }
        ctx.response().end()
    }

    private suspend fun eventStore(ctx: RoutingContext) {
        val account = Account(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"), "Randolph Ledesma", "4157543092", "randop@me.com")
        val contact = Contact(UUID.randomUUID(), "Randolph Ledesma", "4157543092")
        val contactGroup = ContactGroup(UUID.randomUUID(), "Randolph")
        val newContact = contactServiceManager.addContact(account, contact)
        contactServiceManager.addGroup(account, contactGroup)
        contactServiceManager.addContactAtGroup(account, contact, contactGroup)
        ctx.response().end( newContact.contactId.toString() )
    }
}