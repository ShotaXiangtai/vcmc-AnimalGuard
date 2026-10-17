package net.vcmc.animalguard.data

import java.util.UUID

data class MobRecord(
    val mobUuid: UUID,
    val ownerUuid: UUID,
    val ownerName: String,
    val mobName: String?,
    val regDate: Long
)
