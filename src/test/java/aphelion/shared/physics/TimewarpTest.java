/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package aphelion.shared.physics;


import aphelion.shared.gameconfig.GameConfig;
import static aphelion.shared.physics.PhysicsEnvironmentTest.MOVE_UP;
import aphelion.shared.physics.entities.ProjectilePublic;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.*;
import static org.junit.Assert.*;

/**
 * Test if timewarps are able to execute without error.
 * @author Joris
 */
public class TimewarpTest extends PhysicsTest
{
        @Test
        public void testActorCreation()
        {
                env.actorNew(1, ACTOR_FIRST, "Bla", 1234, "warbird");
                env.actorWarp(1, ACTOR_FIRST, false, 1000, 90, 0, 0, 0);
                
                env.timewarp(1); // todo list should remain intact
                env.tick(); // tick 1, it should now create the actor
                this.assertPosition(1000, 90, env.getActor(ACTOR_FIRST, 0, false));
                
                assertEquals(1, env.getActorCount(0));
                
                env.timewarp(1); // the actor is not yet present in state 1, it should recreate him in state 0
                assertEquals(1, env.getActorCount(0));
                env.timewarp(1);
                assertEquals(1, env.getActorCount(0));
                env.timewarp(1);
                assertEquals(1, env.getActorCount(0));
                this.assertPosition(1000, 90, env.getActor(ACTOR_FIRST, 0, false));
                
                while(env.getTick() < PhysicsEnvironment.TRAILING_STATE_DELAY)
                {
                        env.tick();
                }
                
                assertEquals(1, env.getActorCount(0));
                
                env.tick(); // should create the actor at this tick in state 1
                this.assertPosition(1000, 90, env.getActor(ACTOR_FIRST, 0, false));
                this.assertPosition(1000, 90, env.getActor(ACTOR_FIRST, 1, false));
                env.timewarp(1);
                assertEquals(1, env.getActorCount(0));
                this.assertPosition(1000, 90, env.getActor(ACTOR_FIRST, 0, false));
                this.assertPosition(1000, 90, env.getActor(ACTOR_FIRST, 1, false));
                env.timewarp(env.TRAILING_STATES-1);
                assertEquals(1, env.getActorCount(0));
                this.assertPosition(1000, 90, env.getActor(ACTOR_FIRST, 0, false));
                this.assertPosition(1000, 90, env.getActor(ACTOR_FIRST, 1, false));
        }
        
        @Test
        public void testActorDestruction()
        {
                env.actorNew(1, ACTOR_FIRST, "Bla", 1234, "warbird");
                env.actorRemove(PhysicsEnvironment.TRAILING_STATE_DELAY + 3, ACTOR_FIRST);
                
                env.tick();
                assertActorExists(env.getActor(ACTOR_FIRST, 0, false));
                assertActorNotExists(env.getActor(ACTOR_FIRST, 1, false));
                
                while(env.getTick() < PhysicsEnvironment.TRAILING_STATE_DELAY)
                {
                        env.tick();
                }
                
                env.tick(); // should create the actor at this tick in state 1
                assertActorExists(env.getActor(ACTOR_FIRST, 0, false));
                assertActorExists(env.getActor(ACTOR_FIRST, 1, false));
                
                env.tick();
                env.tick(); // should remove the actor at this tick in state 0
                
                assertActorNotExists(env.getActor(ACTOR_FIRST, 0, false));
                assertNotNull(env.getActor(ACTOR_FIRST, 1, false));
                
                env.timewarp(1);
                assertActorNotExists(env.getActor(ACTOR_FIRST, 0, false));
                assertNotNull(env.getActor(ACTOR_FIRST, 1, false));
                
                env.timewarp(env.TRAILING_STATES-1);
                assertActorNotExists(env.getActor(ACTOR_FIRST, 0, false));
                assertNotNull(env.getActor(ACTOR_FIRST, 1, false));
        }
        
        @Test
        public void testConfigChange()
        {
                env.actorNew(1, ACTOR_FIRST, "Bla", 1234, "warbird");
                env.actorWarp(1, ACTOR_FIRST, false, 1000, 90, 0, 0, 0);
                
                // Config change
                try
                {
                        List<Object> yamlDocuments = GameConfig.loadYaml(
                                "- ship-thrust: 1000\n" // was 28
                        );
                        env.loadConfig(3, "test", yamlDocuments);
                }
                catch (Exception ex)
                {
                        throw new Error(ex);
                }
                
                env.actorMove(2, ACTOR_FIRST, MOVE_UP);
                env.actorMove(3, ACTOR_FIRST, MOVE_UP);
                env.actorMove(4, ACTOR_FIRST, MOVE_UP);
                env.actorMove(5, ACTOR_FIRST, MOVE_UP);
                env.actorMove(6, ACTOR_FIRST, MOVE_UP);
                
                env.tick(); // tick 1
                env.tick(); // tick 2
                env.tick(); // tick 3
                env.tick(); // tick 4
                env.tick(); // tick 5
                env.tick(); // tick 6

                assertEquals(1000, env.getGlobalConfigInteger(0, "ship-thrust").get());
                assertEquals(28, env.getGlobalConfigInteger(1, "ship-thrust").get());
                assertVelocity(0, -3624, env.getActor(ACTOR_FIRST, 0, false));
                
                env.timewarp(1);
                assertEquals(1000, env.getGlobalConfigInteger(0, "ship-thrust").get());
                assertEquals(28, env.getGlobalConfigInteger(1, "ship-thrust").get());
                assertVelocity(0, -3624, env.getActor(ACTOR_FIRST, 0, false));
                
                env.timewarp(env.TRAILING_STATES-1);
                assertEquals(1000, env.getGlobalConfigInteger(0, "ship-thrust").get());
                assertEquals(28, env.getGlobalConfigInteger(1, "ship-thrust").get());
                assertVelocity(0, -3624, env.getActor(ACTOR_FIRST, 0, false));
        }
        
        private void testProjectileCreation_assertSingleProj(int state, int x, int y)
        {
                int count = 0;
                for (ProjectilePublic proj : env.projectileIterable(state))
                {
                        ++count;
                        assertPosition(x, y, proj);
                }

                assertEquals(1, count);
        }
        
        @Test
        public void testProjectileCreation()
        {
                try
                {
                        List<Object> yamlDocuments = GameConfig.loadYaml(""
                                + "- weapon-slot-gun: test-noreload\n"
                                + "  weapon-slot-bomb: test-reload\n" 
                                
                                + "- selector: {weapon: test-noreload}\n"
                                + "  weapon-switch-delay: 0\n"
                                
                                + "- selector: {weapon: test-reload}\n"
                                + "  weapon-switch-delay: 4\n"
                        );
                        env.loadConfig(env.getTick() - env.MAX_OPERATION_AGE, "test", yamlDocuments);
                }
                catch (Exception ex)
                {
                        throw new Error(ex);
                }
                
                
                env.actorNew(1, ACTOR_FIRST, "Bla", 1234, "warbird");
                env.actorWarp(1, ACTOR_FIRST, false, 1000, 90, 0, 0, 0);
                env.actorWeapon(2, ACTOR_FIRST, WEAPON_SLOT.GUN, false, 0, 0, 0 ,0 ,0);
                
                env.tick(); // 1
                env.tick(); // 2
                
                testProjectileCreation_assertSingleProj(0, 1000, -14246);
                
                env.timewarp(1);
                testProjectileCreation_assertSingleProj(0, 1000, -14246);
                
                env.timewarp(env.TRAILING_STATES-1);
                testProjectileCreation_assertSingleProj(0, 1000, -14246);
                
                
                
                while(env.getTick() < PhysicsEnvironment.TRAILING_STATE_DELAY)
                {
                        env.tick();
                }
                
                env.tick();
                env.tick();
                
                testProjectileCreation_assertSingleProj(0, 1000, -96166);
                testProjectileCreation_assertSingleProj(1, 1000, -14246);
                
                env.timewarp(1);
                testProjectileCreation_assertSingleProj(0, 1000, -96166);
                testProjectileCreation_assertSingleProj(1, 1000, -14246);
                
                env.timewarp(env.TRAILING_STATES-1);
                testProjectileCreation_assertSingleProj(0, 1000, -96166);
                testProjectileCreation_assertSingleProj(1, 1000, -14246);
                
                
                assertEquals(4, env.getTimewarpCount());
                
                // 2 weapons, one of them has a weapon switch delay
                // execute them in the wrong order so that only 1 fires in state 0,
                // but both fire in state 1
                long t = env.getTick();
                env.tick();
                env.tick();
                // make sure they are both late
                env.actorWeapon(t+2, ACTOR_FIRST, WEAPON_SLOT.BOMB, false, 0, 0, 0, 0, 0); // bomb has switch delay,
                env.actorWeapon(t+1, ACTOR_FIRST, WEAPON_SLOT.GUN, false, 0, 0, 0, 0, 0);  // gun does not
                // after resolving inconsistencies, both weapons should have executed properly
                
                Logger.getAnonymousLogger().log(Level.SEVERE, "----------------------------");
                {
                        // assert that the inconsistency is present
                        int count = 0;
                        for (ProjectilePublic proj : env.projectileIterable(0))
                        {
                                ++count;
                        }

                        assertEquals(2, count);
                }
                
                assertEquals(4, env.getTimewarpCount());
                
                // should detect the inconsistency and resolve it
                // (new Projectile() should execute properly)
                while(env.getTick(1) < t+PhysicsEnvironment.TRAILING_STATE_DELAY)
                {
                        env.tick();
                }
                assertEquals(5, env.getTimewarpCount());
                
                
                env.tick();
                env.tick();
                assertEquals(5, env.getTimewarpCount());
                
                {
                        int count = 0;
                        for (ProjectilePublic proj : env.projectileIterable(0))
                        {
                                ++count;
                        }

                        assertEquals(3, count);
                }
        }
}