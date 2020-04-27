package com.randolphledesma.gad

import dagger.Module
import dagger.Provides
import dagger.Component
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

import kotlinx.coroutines.*
import io.vertx.kotlin.coroutines.*

import javax.inject.Inject
import javax.inject.Singleton
import javax.inject.Provider

import io.vertx.core.Verticle
import io.vertx.core.spi.VerticleFactory
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.awaitResult
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions

import io.vertx.ext.jdbc.JDBCClient
import io.vertx.kotlin.ext.sql.queryAwait
import io.vertx.kotlin.ext.sql.getConnectionAwait

import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.config.ConfigRetriever

import redis.clients.jedis.Jedis

/**
 * The application itself.
 */
class Application @Inject constructor(private val applicationContext: ApplicationContext) {
    private val LOG by logger()

    fun start() {
        runBlocking(applicationContext.vertx.dispatcher()) {
            try {
                startHttpVerticle()
            } catch(error: Throwable) {                
                LOG.error("HttpServer Component Failed", error)
                System.exit(-1)
            }
        }
    }

    private suspend fun startHttpVerticle() {
        awaitResult<String> { applicationContext.vertx.deployVerticle("dagger:${HttpVerticle::class.java.name}", it) }
        LOG.info("Main verticle deployed successful")
    }
}

/**
 * Component which configures all needed Dagger modules to run the application.
 */
@Singleton
@Component(modules = [
        DaggerVerticleFactoryModule::class,
        VertxModule::class,
        VertxWebClientModule::class,
        SqlModule::class,
        RedisModule::class,
        ApplicationContextModule::class,
        HttpVerticleModule::class
    ])
interface ApplicationComponent {
    fun application(): Application
}

@Singleton
class ApplicationContext @Inject constructor(val vertx: Vertx, val dbClient: JDBCClient, val redis: Jedis) {
    private val LOG by logger()
    var configuration = JsonObject()

    init {
        runBlocking {
            try {
                val retriever = ConfigRetriever.create(vertx)
                configuration = awaitResult<JsonObject> { handler ->
                    retriever.getConfig(handler)
                }
                LOG.info("Application Configuration Loaded")
            } catch (error: Throwable) {
                LOG.error("Application Configuration Retrieval Failed", error)
            }
        }
    }
}

class DaggerVerticleFactory(private val verticleMap: Map<String, Provider<Verticle>>) : VerticleFactory {

    private val LOG by logger()

    override fun createVerticle(verticleName: String, classLoader: ClassLoader): Verticle {
        val verticle = verticleMap.getOrElse(sanitizeVerticleClassName(verticleName), { throw IllegalStateException("No provider for verticle type $verticleName found") }).get()
        LOG.info("Verticle for type: $verticleName created")
        return verticle
    }

    private fun sanitizeVerticleClassName(verticleName: String): String = verticleName.substring(verticleName.lastIndexOf(":") + 1)

    override fun prefix(): String = "dagger"
}

@Module
object ApplicationContextModule {
    @Provides
    @Singleton
    @IntoMap
    @StringKey("com.randolphledesma.gad.ApplicationContext")
    fun provideApplicationContext(vertx: Vertx, dbClient: JDBCClient, redis: Jedis) = ApplicationContext(vertx, dbClient, redis)
}

@Module
object DaggerVerticleFactoryModule {
    @JvmSuppressWildcards
    @Provides
    @Singleton
    fun provideVerticleFactory(verticleMap: Map<String, Provider<Verticle>>): VerticleFactory = DaggerVerticleFactory(verticleMap)
}

@Module
object HttpVerticleModule {
    @Provides    
    @Singleton
    @IntoMap
    @StringKey("com.randolphledesma.gad.HttpVerticle")
    fun provideHttpVerticle(context: ApplicationContext): Verticle = HttpVerticle(MainController(context))
}

@Module
object VertxModule {

    @Provides    
    @Singleton
    fun provideVertx(verticleFactory: VerticleFactory): Vertx {        
        val vertx = Vertx.vertx()        
        vertx.registerVerticleFactory(verticleFactory)
        return vertx
    }
}

@Module
object VertxWebClientModule {

    @Provides    
    @Singleton
    fun provideVertxWebClient(vertx: Vertx): WebClient {        
        val options = WebClientOptions().setTcpKeepAlive(true).setUserAgent("Gad/1.0")
        return WebClient.create(vertx, options)
    }
}

@Module
object RedisModule {
    private val LOG by logger()

    @Provides
    @Singleton
    fun provideRedisClient(vertx: Vertx): Jedis {
        lateinit var client: Jedis
        val jsonConfig = runBlocking<JsonObject> {
            var jsonObject = JsonObject()
            try {
                val retriever = ConfigRetriever.create(vertx)
                jsonObject = awaitResult { handler ->
                    retriever.getConfig(handler)
                }
            } catch (error: Throwable) {
                //void
            }
            return@runBlocking jsonObject
        }
        val host = jsonConfig.getString(ConfigurationKeyList.REDIS_HOST.name, "127.0.0.1")
        val port = jsonConfig.getInteger(ConfigurationKeyList.REDIS_PORT.name, 6379)
        val timeout = jsonConfig.getInteger(ConfigurationKeyList.REDIS_CONNECT_TIMEOUT.name, 60)

        try {
            client = Jedis(host, port, timeout)
            client.connect()
            LOG.info("Redis Connection $host:$port Succeeded")
        } catch (error: Throwable) {
            LOG.error(error.message, error)
            System.exit(-1)
        }
        return client
    }
}

@Module
object SqlModule {
    private val LOG by logger()

    @Provides
    @Singleton
    fun provideJdbcClient(vertx: Vertx): JDBCClient {
        lateinit var client: JDBCClient
        val sql = "SELECT CURRENT_TIMESTAMP() AS ts, @@character_set_database AS db_charset, @@collation_database AS db_collation, @@global.time_zone AS tz_global, @@session.time_zone AS tz_session"
        runBlocking {
            var jsonConfig = JsonObject()
            try {
                val retriever = ConfigRetriever.create(vertx)
                jsonConfig = awaitResult<JsonObject> { handler ->
                    retriever.getConfig(handler)
                }
            } catch (error: Throwable) {
                //void
            }

            val host = jsonConfig.getString(ConfigurationKeyList.DB_HOST.name, "127.0.0.1")
            val port = jsonConfig.getInteger(ConfigurationKeyList.DB_PORT.name, 3306)
            val user = jsonConfig.getString(ConfigurationKeyList.DB_USER.name, "DEFAULT_USER")
            val password = jsonConfig.getString(ConfigurationKeyList.DB_PASSWORD.name, "DEFAULT_PASSWORD")
            val poolSize = jsonConfig.getInteger(ConfigurationKeyList.DB_POOL_SIZE.name, 15)
            val db = jsonConfig.getString(ConfigurationKeyList.DB_DATABASE.name, "DEFAULT_DB")
            val extra = jsonConfig.getString(ConfigurationKeyList.DB_EXTRA_PARAMETERS.name, "")
            val timeout = jsonConfig.getLong(ConfigurationKeyList.DB_CONNECT_TIMEOUT.name, 30000L)

            try {
                var config = json {
                    obj(
                        "url" to "jdbc:mysql://$host:$port/$db?$extra",
                        "driver_class" to "com.mysql.cj.jdbc.Driver",
                        "max_pool_size" to poolSize,
                        "user" to user,
                        "password" to password
                    )
                }
                client = JDBCClient.createShared(vertx, config)
                withTimeout<Unit>(timeout) {
                    val connection = client.getConnectionAwait()
                    val res = connection.queryAwait(sql)
                    val jsonVal = res.rows[0].toString()
                    val jsonResult = jsonVal.parseJson().await()
                    val ts = jsonResult.getString("ts")
                    LOG.info("$jsonVal")
                    LOG.info("Database Startup Connection $host:$port Succeeded: $ts")
                    connection.close()
                }                
            } catch(error: Throwable) {
                LOG.error(error.message, error)
                System.exit(-1)
            }
        }        
        return client
    }
}