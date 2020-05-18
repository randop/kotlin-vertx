package com.randolphledesma.gad.models

data class Contact(val contactId: String, val name: String, val phone: String) {
    override fun toString() = "$contactId : $name : $phone"
}