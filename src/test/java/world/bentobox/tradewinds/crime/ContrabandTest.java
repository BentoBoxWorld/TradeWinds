package world.bentobox.tradewinds.crime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.economy.PriceModel;
import world.bentobox.tradewinds.ocean.SecurityBand;

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
    void testTheFeedstockIsAsIllegalAsTheGood() {
        // One cane crafts into one sugar, so cane past a scan is sugar past a
        // scan - and the shipped default has to say so, or the whole customs
        // layer is theatre for anyone who reads a recipe book (2026-08-09).
        // Both lists, because a map/list in config.yml REPLACES the code
        // default rather than merging with it.
        var shipped = new world.bentobox.tradewinds.Settings().getContrabandMaterials();
        assertTrue(shipped.contains("SUGAR"), "SUGAR must be contraband by default");
        assertTrue(shipped.contains("SUGAR_CANE"), "SUGAR_CANE must be contraband by default");
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
    void testContrabandActuallyPaysForTheRisk() {
        // Playtest: "sugar trading should be high risk, high reward - but the
        // trader only offered ~$1 for it". Contraband is priced from its
        // crafting recipe like everything else, and sugar's recipe price is
        // about one unit, so the risk was built and the reward was not.
        PriceModel model = new PriceModel(0.6, 1.4, 0.125, 1.15, 0.85, 500, 0.7, 1.3);
        double premium = 8.0;
        double sugarBase = 1.0;

        // A port that deals in contraband always wants it, so the band bonus
        // applies: the rougher the port, the better it pays
        double frontier = model.playerSellsAt(sugarBase * premium,
                model.economicFactor(false, true, SecurityBand.FRONTIER.ordinal(), 0));
        double lawless = model.playerSellsAt(sugarBase * premium,
                model.economicFactor(false, true, SecurityBand.LAWLESS.ordinal(), 0));
        double anarchic = model.playerSellsAt(sugarBase * premium,
                model.economicFactor(false, true, SecurityBand.ANARCHIC.ordinal(), 0));
        assertTrue(lawless > frontier, "Running further out must pay more");
        assertTrue(anarchic > lawless, "Running further out must pay more");

        // ... and it must beat honest trading per unit of hold space, or there
        // is no reason to take the risk at all. Compare against the margin on a
        // mid-value good bought where it is produced and sold where it is wanted.
        double honestBase = 8.0;
        double honestMargin = model.playerSellsAt(honestBase,
                model.economicFactor(false, true, SecurityBand.FRONTIER.ordinal(), 0))
                - model.playerBuysAt(honestBase,
                        model.economicFactor(true, false, SecurityBand.FRONTIER.ordinal(), 0));
        assertTrue(frontier > honestMargin,
                "Contraband pays " + frontier + "/item against an honest margin of " + honestMargin);

        // Without the premium it is worth less than the honest margin, which is
        // exactly the bug that was reported
        double unpriced = model.playerSellsAt(sugarBase,
                model.economicFactor(false, true, SecurityBand.FRONTIER.ordinal(), 0));
        assertTrue(unpriced < honestMargin);
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
