package com.randolphledesma.gad.managers

import com.randolphledesma.gad.ApplicationContext
import com.randolphledesma.gad.EventStoreEvent
import com.randolphledesma.gad.EventStoreResource
import com.randolphledesma.gad.models.Account
import com.randolphledesma.gad.models.Contact
import com.randolphledesma.gad.models.ContactGroup
import com.randolphledesma.gad.services.ContactService
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import java.util.*
import javax.inject.Inject

class ContactServiceManager @Inject constructor(private val applicationContext: ApplicationContext): ContactService {
    private val eventStore = applicationContext.eventStore

    override suspend fun addContact(account: Account, contact: Contact): Contact {
        val json = JsonObject.mapFrom(contact)
        val event = EventStoreEvent(contact.contactId.toString(), "ContactCreated", json)
        val resource = EventStoreResource("contacts_${account.accountId.toString()}", event)
        eventStore.writeToStream(resource)
        return contact
    }

    override suspend fun addGroup(account: Account, contactGroup: ContactGroup): ContactGroup {
        val json = JsonObject.mapFrom(contactGroup)
        val event = EventStoreEvent(contactGroup.contactGroupId.toString(), "ContactGroupCreated", json)
        val resource = EventStoreResource("contacts_${account.accountId.toString()}", event)
        eventStore.writeToStream(resource)
        return contactGroup
    }

    override suspend fun addContactAtGroup(account: Account, contact: Contact, contactGroup: ContactGroup): ContactGroup {
        val json = JsonObject()
        json.put("contactId", contact.contactId.toString())
        json.put("contactGroupId", contactGroup.contactGroupId.toString())
        val event = EventStoreEvent(UUID.randomUUID().toString(), "ContactGrouped", json)
        val resource = EventStoreResource("contacts_${account.accountId.toString()}", event)
        eventStore.writeToStream(resource)
        return contactGroup
    }
}