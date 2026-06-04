package net.vcmc.animalguard.manager;

import net.vcmc.animalguard.data.MobRecord;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GuardManager {

    private final Map<UUID, MobRecord> protectedMobs = new ConcurrentHashMap<>();

    public void loadAll(List<MobRecord> records) {
        protectedMobs.clear();
        for (MobRecord record : records) {
            protectedMobs.put(record.mobUuid(), record);
        }
    }

    public void add(MobRecord record) {
        protectedMobs.put(record.mobUuid(), record);
    }

    public void remove(UUID mobUuid) {
        protectedMobs.remove(mobUuid);
    }

    public boolean isProtected(UUID mobUuid) {
        return protectedMobs.containsKey(mobUuid);
    }

    public MobRecord getRecord(UUID mobUuid) {
        return protectedMobs.get(mobUuid);
    }

    public boolean isOwner(UUID mobUuid, UUID playerUuid) {
        MobRecord record = protectedMobs.get(mobUuid);
        return record != null && record.ownerUuid().equals(playerUuid);
    }

    public Collection<MobRecord> getAll() {
        return Collections.unmodifiableCollection(protectedMobs.values());
    }

    public int size() {
        return protectedMobs.size();
    }
}
