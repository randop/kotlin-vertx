package com.randolphledesma.gad

import javax.inject.Inject

fun main(args: Array<String>) {
    DaggerApplicationComponent.builder()
            .build()
            .application()
            .start()
}