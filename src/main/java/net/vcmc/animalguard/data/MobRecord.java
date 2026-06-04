package net.vcmc.animalguard.data;

import java.util.UUID;

public record MobRecord(
        UUID mobUuid,
        UUID ownerUuid,
        String ownerName,
        String mobName,
        long regDate
) {}
