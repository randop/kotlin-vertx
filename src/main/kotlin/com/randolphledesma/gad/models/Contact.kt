package com.randolphledesma.gad.models

data class Contact(val id: String, val name: String, val phone: String) {
    override fun toString() = "$name : $phone"
}