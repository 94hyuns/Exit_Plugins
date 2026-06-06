package com.example.land.data;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ClaimedChunk {

    /** 관리구역 청크의 owner 필드에 저장되는 더미 UUID (의미 없음) */
    public static final UUID ADMIN_OWNER_UUID = new UUID(0L, 0L);

    /** 청크 타입: 플레이어 개인 소유 / 관리자 지정 공용 구역 */
    public enum ClaimType { PLAYER, ADMIN }

    private final ChunkPos pos;
    private final UUID owner;
    private final ClaimType type;
    private final Set<UUID> members;

    // ── 기존 생성자 (하위 호환) — PLAYER 타입 ─────────────────
    public ClaimedChunk(ChunkPos pos, UUID owner) {
        this(pos, owner, ClaimType.PLAYER, new HashSet<>());
    }

    public ClaimedChunk(ChunkPos pos, UUID owner, Set<UUID> members) {
        this(pos, owner, ClaimType.PLAYER, members);
    }

    // ── 신규 생성자 ─────────────────────────────────────────
    public ClaimedChunk(ChunkPos pos, UUID owner, ClaimType type) {
        this(pos, owner, type, new HashSet<>());
    }

    public ClaimedChunk(ChunkPos pos, UUID owner, ClaimType type, Set<UUID> members) {
        this.pos = pos;
        this.owner = owner;
        this.type = type;
        this.members = new HashSet<>(members);
    }

    /** 관리구역 생성용 팩토리 */
    public static ClaimedChunk admin(ChunkPos pos) {
        return new ClaimedChunk(pos, ADMIN_OWNER_UUID, ClaimType.ADMIN);
    }

    public ChunkPos getPos() { return pos; }
    public UUID getOwner() { return owner; }
    public ClaimType getType() { return type; }
    public boolean isAdmin() { return type == ClaimType.ADMIN; }
    public Set<UUID> getMembers() { return Collections.unmodifiableSet(members); }

    public void addMember(UUID uuid) { members.add(uuid); }
    public void removeMember(UUID uuid) { members.remove(uuid); }

    /**
     * 해당 플레이어가 이 청크에서 행동 가능한지.
     * 관리구역은 항상 false → land.admin 권한자만 ProtectionListener의 early-return으로 통과한다.
     */
    public boolean canInteract(UUID uuid) {
        if (type == ClaimType.ADMIN) return false;
        return owner.equals(uuid) || members.contains(uuid);
    }
}
