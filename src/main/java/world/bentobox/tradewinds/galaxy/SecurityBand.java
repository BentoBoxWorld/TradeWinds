package world.bentobox.tradewinds.galaxy;

/**
 * Island security bands, EVE-style: safest nearest spawn, lawless out in the
 * deep ocean. The band drives protection flags, customs scan chances, police
 * response and market access as later stages land.
 *
 * @author tastybento
 */
public enum SecurityBand {
    SAFE("Safe"),
    POLICED("Policed"),
    FRONTIER("Frontier"),
    LAWLESS("Lawless"),
    ANARCHIC("Anarchic");

    private final String displayName;

    SecurityBand(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * @return true if PvP is live on this island's space
     */
    public boolean isPvp() {
        return this == LAWLESS || this == ANARCHIC;
    }
}
