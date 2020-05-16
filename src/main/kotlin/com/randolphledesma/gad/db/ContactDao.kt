package com.randolphledesma.gad.db

import com.randolphledesma.gad.models.Contact

interface ContactDao {
    suspend fun getAll(accountId: String): List<Contact>
    suspend fun getContact(id: String): Contact
    suspend fun insertAll(contacts: List<Contact>)
    suspend fun delete(contact: Contact)
}