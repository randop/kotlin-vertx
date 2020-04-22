package com.randolphledesma.gad

import javax.inject.Inject

fun main(args: Array<String>) {
    DaggerApplicationComponent.builder()                        
            .vertxModule(VertxModule)            
            .daggerVerticleFactoryModule(DaggerVerticleFactoryModule)
            .sqlModule(SqlModule)
            .httpVerticleModule(HttpVerticleModule)
            .vertxWebClientModule(VertxWebClientModule)
            .build()
            .application()
            .start()
}