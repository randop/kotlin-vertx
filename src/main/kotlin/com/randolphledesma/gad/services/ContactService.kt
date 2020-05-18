package com.randolphledesma.gad.services

import com.randolphledesma.gad.models.Account
import com.randolphledesma.gad.models.Contact
import com.randolphledesma.gad.models.ContactGroup
import io.reactivex.Single

interface ContactService {
    suspend fun addContact(account: Account, contact: Contact): Contact
    suspend fun addGroup(account: Account, contactGroup: ContactGroup): ContactGroup
    suspend fun addContactAtGroup(account: Account, contact: Contact, contactGroup: ContactGroup): ContactGroup
    //suspend fun allContacts(account: Account): Single<List<Contact>>

    //suspend fun getCertain(accountId: AccountUuid, contactId: ContactUuid): Maybe<Contact>
    //suspend fun update(accountId: AccountUuid, contactId: ContactUuid, newContact: Contact): Maybe<Contact>
    //suspend fun delete(accountId: AccountUuid, contactId: ContactUuid): Completable
    //suspend fun deleteAll(accountId: AccountUuid): Completable
}