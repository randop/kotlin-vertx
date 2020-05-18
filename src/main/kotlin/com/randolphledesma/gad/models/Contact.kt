package com.randolphledesma.gad.models

import java.util.*

typealias ContactUuid = UUID

data class Contact(val contactId: ContactUuid, val name: String, val phone: String) {
    override fun toString() = "$contactId : $name : $phone"
}