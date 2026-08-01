package world.bentobox.tradewinds.crime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.galaxy.SecurityBand;

/**
 * Headless tests of the contraband rules - which ports deal in it, and how
 * hard a smuggler is searched.
 *
 * @author tastybento
 */
class ContrabandTest {

    private static final Set<String> LIST = Set.of("SUGAR");

    @Test
    void testMasterGateRemovesTheWholeMechanic() {
        // A family server turns illegal-trade off and nothing is contraband,
        // whatever the list says
        assertTrue(Contraband.isContraband("SUGAR", LIST, true));
        assertFalse(Contraband.isContraband("SUGAR", LIST, false));
        assertFalse(Contraband.isContraband("BREAD", LIST, true));
    }

    @Test
    void testSafePortsRefuseContraband() {
        // Crime pays, into danger: the safe ports will not touch it, so a
        // smuggling run has to be a voyage outward
        assertFalse(Contraband.buysContraband(SecurityBand.SAFE, SecurityBand.FRONTIER));
        assertFalse(Contraband.buysContraband(SecurityBand.POLICED, SecurityBand.FRONTIER));
        assertTrue(Contraband.buysContraband(SecurityBand.FRONTIER, SecurityBand.FRONTIER));
        assertTrue(Contraband.buysContraband(SecurityBand.LAWLESS, SecurityBand.FRONTIER));
        assertTrue(Contraband.buysContraband(SecurityBand.ANARCHIC, SecurityBand.FRONTIER));
    }

    @Test
    void testTheBandsThatBuyAreTheBandsThatDoNotScan() {
        // The whole risk/reward shape: nowhere both buys contraband AND
        // searches hard for it. If this ever inverts, smuggling becomes either
        // free money or impossible.
        java.util.Map<SecurityBand, Double> scans = java.util.Map.of(SecurityBand.SAFE, 0.9,
                SecurityBand.POLICED, 0.6, SecurityBand.FRONTIER, 0.3, SecurityBand.LAWLESS, 0.1,
                SecurityBand.ANARCHIC, 0.0);
        for (SecurityBand band : SecurityBand.values()) {
            boolean buys = Contraband.buysContraband(band, SecurityBand.FRONTIER);
            if (buys) {
                assertTrue(scans.get(band) <= 0.3,
                        band + " both buys contraband and searches hard for it");
            }
        }
        // ... and scan pressure falls monotonically as the law thins out
        double previous = Double.MAX_VALUE;
        for (SecurityBand band : SecurityBand.values()) {
            assertTrue(scans.get(band) <= previous, "Scan chance rose at " + band);
            previous = scans.get(band);
        }
    }

    @Test
    void testStandingMovesTheOddsBothWays() {
        // A clean name is worth something at the border (spec 7: positive rep
        // must pay), and a known offender is searched harder
        double base = 0.5;
        assertEquals(0.2, Contraband.scanChance(base, Standing.UPSTANDING, 0.4, 1.6), 1e-9);
        assertEquals(0.5, Contraband.scanChance(base, Standing.CLEAN, 0.4, 1.6), 1e-9);
        assertEquals(0.8, Contraband.scanChance(base, Standing.OFFENDER, 0.4, 1.6), 1e-9);
        assertEquals(0.8, Contraband.scanChance(base, Standing.WANTED, 0.4, 1.6), 1e-9);
    }

    @Test
    void testScanChanceStaysAProbability() {
        // A generous offender factor must not push the chance past certainty,
        // nor a band with no customs below zero
        assertEquals(1.0, Contraband.scanChance(0.9, Standing.FUGITIVE, 0.4, 5.0), 1e-9);
        assertEquals(0.0, Contraband.scanChance(0.0, Standing.FUGITIVE, 0.4, 5.0), 1e-9);
        // ANARCHIC has nobody to send: no standing makes it search you
        for (Standing standing : Standing.values()) {
            assertEquals(0.0, Contraband.scanChance(0.0, standing, 0.4, 1.6), 1e-9);
        }
    }
}
