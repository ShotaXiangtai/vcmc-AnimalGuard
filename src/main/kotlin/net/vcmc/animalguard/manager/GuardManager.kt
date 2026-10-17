package net.vcmc.animalguard.manager

import net.vcmc.animalguard.data.MobRecord
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GuardManager {

    private val protectedMobs = ConcurrentHashMap<UUID, MobRecord>()

    fun loadAll(records: List<MobRecord>) {
        protectedMobs.clear()
        for (record in records) {
            protectedMobs[record.mobUuid] = record
        }
    }

    fun add(record: MobRecord) {
        protectedMobs[record.mobUuid] = record
    }

    fun remove(mobUuid: UUID) {
        protectedMobs.remove(mobUuid)
    }

    fun isProtected(mobUuid: UUID): Boolean {
        return protectedMobs.containsKey(mobUuid)
    }

    fun getRecord(mobUuid: UUID): MobRecord? {
        return protectedMobs[mobUuid]
    }

    fun isOwner(mobUuid: UUID, playerUuid: UUID): Boolean {
        val record = protectedMobs[mobUuid]
        return record != null && record.ownerUuid == playerUuid
    }

    val all: Collection<MobRecord>
        get() = Collections.unmodifiableCollection(protectedMobs.values)

    val size: Int
        get() = protectedMobs.size
}
