package world.bentobox.tradewinds.listeners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent.TargetReason;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.tradewinds.CommonTestSetup;
import world.bentobox.tradewinds.generator.IslandDecorator;

/**
 * Tests resident protection: mobs never target or hurt residents; players can
 * (crime is Stage 6's problem, not invincibility's).
 *
 * @author tastybento
 */
class ResidentProtectionListenerTest extends CommonTestSetup {

    private ResidentProtectionListener listener;
    private Villager resident;
    private Villager stranger;
    private Zombie zombie;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        listener = new ResidentProtectionListener();
        resident = mock(Villager.class);
        PersistentDataContainer taggedPdc = mock(PersistentDataContainer.class);
        when(taggedPdc.has(IslandDecorator.RESIDENT_KEY, PersistentDataType.STRING)).thenReturn(true);
        when(resident.getPersistentDataContainer()).thenReturn(taggedPdc);
        stranger = mock(Villager.class);
        when(stranger.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        zombie = mock(Zombie.class);
    }

    private DamageSource source() {
        DamageSource src = mock(DamageSource.class);
        when(src.getDamageType()).thenReturn(DamageType.GENERIC);
        return src;
    }

    @Test
    void testMobsNeverTargetResidents() {
        EntityTargetLivingEntityEvent event = new EntityTargetLivingEntityEvent(zombie, resident,
                TargetReason.CLOSEST_ENTITY);
        listener.onTarget(event);
        assertTrue(event.isCancelled());
        // Ordinary villagers are still fair game for zombies
        EntityTargetLivingEntityEvent other = new EntityTargetLivingEntityEvent(zombie, stranger,
                TargetReason.CLOSEST_ENTITY);
        listener.onTarget(other);
        assertFalse(other.isCancelled());
    }

    @Test
    void testMobDamageCancelled() {
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(zombie, resident,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, source(), 4.0);
        listener.onDamage(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void testPlayerDamageAllowed() {
        EntityDamageByEntityEvent direct = new EntityDamageByEntityEvent(mockPlayer, resident,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, source(), 4.0);
        listener.onDamage(direct);
        assertFalse(direct.isCancelled());
        // Player projectiles too
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(mock(Player.class));
        EntityDamageByEntityEvent shot = new EntityDamageByEntityEvent(arrow, resident,
                EntityDamageEvent.DamageCause.PROJECTILE, source(), 4.0);
        listener.onDamage(shot);
        assertFalse(shot.isCancelled());
    }

    @Test
    void testEnvironmentDamageCancelled() {
        EntityDamageEvent event = new EntityDamageEvent(resident, EntityDamageEvent.DamageCause.FIRE, source(), 2.0);
        listener.onDamage(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void testStrangersUnprotected() {
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(zombie, stranger,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, source(), 4.0);
        listener.onDamage(event);
        assertFalse(event.isCancelled());
    }
}
