package com.randolphledesma.gad

data class DataStoreCell(val added_id: Int, val row_key: String, val column_name: String, val ref_key: Int, val body: ByteArray, val created_at: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DataStoreCell

        if (added_id != other.added_id) return false
        if (row_key != other.row_key) return false
        if (column_name != other.column_name) return false
        if (ref_key != other.ref_key) return false
        if (!body.contentEquals(other.body)) return false
        if (created_at != other.created_at) return false

        return true
    }

    override fun hashCode(): Int {
        var result = added_id
        result = 31 * result + row_key.hashCode()
        result = 31 * result + column_name.hashCode()
        result = 31 * result + ref_key
        result = 31 * result + body.contentHashCode()
        result = 31 * result + created_at.hashCode()
        return result
    }
}