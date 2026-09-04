package world.bentobox.tradewinds.ocean;

/**
 * Island security bands, EVE-style: safest nearest spawn, lawless out in the
 * deep ocean. The band drives protection flags, customs scan chances, police
 * response and market access as later stages land.
 *
 * @author tastybento
 */
public enum SecurityBand {
    SAFE, POLICED, FRONTIER, LAWLESS, ANARCHIC;

    /**
     * The locale key for this band's name - the ONLY way a band is named to a
     * player; there is deliberately no English display name on the enum. Bands are coloured in the locale so
     * a sailor can read where they are at a glance - arriving in anarchic water
     * and having no way to tell is how people lose cargo.
     *
     * @return locale key
     */
    public String getLocaleKey() {
        return "tradewinds.band." + name().toLowerCase(java.util.Locale.ENGLISH);
    }

    /**
     * @return true if PvP is live on this island's space
     */
    public boolean isPvp() {
        return this == LAWLESS || this == ANARCHIC;
    }
}
