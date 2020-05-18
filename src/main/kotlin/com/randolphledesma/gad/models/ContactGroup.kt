package com.randolphledesma.gad.models

import java.util.*

typealias ContactGroupUuid = UUID

data class ContactGroup(val contactGroupId: ContactGroupUuid, val name: String) {
    override fun toString() = "$contactGroupId : $name"
}