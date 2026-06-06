package com.exit.gamble.slot.world;

import org.bukkit.Location;

import java.util.UUID;

public class SlotMachine {

    private final String id;
    private final Location anchor;
    private final UUID reel1Id;
    private final UUID reel2Id;
    private final UUID reel3Id;
    private final UUID statusId;

    private UUID currentUser;
    private long currentBet;
    private boolean spinning;
    private String npcId;
    private String npcSkin;

    public SlotMachine(String id, Location anchor,
                       UUID reel1Id, UUID reel2Id, UUID reel3Id, UUID statusId) {
        this.id = id;
        this.anchor = anchor;
        this.reel1Id = reel1Id;
        this.reel2Id = reel2Id;
        this.reel3Id = reel3Id;
        this.statusId = statusId;
    }

    public String id() { return id; }
    public Location anchor() { return anchor.clone(); }

    public UUID reelId(int idx) {
        return switch (idx) {
            case 0 -> reel1Id;
            case 1 -> reel2Id;
            case 2 -> reel3Id;
            default -> throw new IllegalArgumentException("reel idx: " + idx);
        };
    }
    public UUID statusId() { return statusId; }
    public UUID[] displayIds() { return new UUID[]{reel1Id, reel2Id, reel3Id, statusId}; }

    public UUID currentUser() { return currentUser; }
    public long currentBet() { return currentBet; }
    public boolean isSpinning() { return spinning; }
    public boolean isOccupied() { return currentUser != null; }

    public void setCurrentUser(UUID user) { this.currentUser = user; }
    public void setCurrentBet(long bet) { this.currentBet = bet; }
    public void setSpinning(boolean spinning) { this.spinning = spinning; }

    public String npcId() { return npcId; }
    public String npcSkin() { return npcSkin; }
    public void setNpc(String npcId, String skin) { this.npcId = npcId; this.npcSkin = skin; }
    public void clearNpc() { this.npcId = null; this.npcSkin = null; }
    public boolean hasNpc() { return npcId != null; }
}
