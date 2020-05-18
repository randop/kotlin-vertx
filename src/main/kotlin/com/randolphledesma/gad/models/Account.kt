package com.randolphledesma.gad.models

import java.util.*

typealias AccountUuid = UUID

data class Account(val accountId: AccountUuid, val name: String, val phone: String, val email: String) {
    override fun toString() = "$accountId;$name;$phone;$email"
}