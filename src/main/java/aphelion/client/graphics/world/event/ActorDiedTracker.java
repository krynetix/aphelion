/*
 * Aphelion
 * Copyright (c) 2013  Joris van der Wel
 * 
 * This file is part of Aphelion
 * 
 * Aphelion is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 * 
 * Aphelion is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with Aphelion.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * In addition, the following supplemental terms apply, based on section 7 of
 * the GNU Affero General Public License (version 3):
 * a) Preservation of all legal notices and author attributions
 * b) Prohibition of misrepresentation of the origin of this material, and
 * modified versions are required to be marked in reasonable ways as
 * different from the original version (for example by appending a copyright notice).
 * 
 * Linking this library statically or dynamically with other modules is making a
 * combined work based on this library. Thus, the terms and conditions of the
 * GNU Affero General Public License cover the whole combination.
 * 
 * As a special exception, the copyright holders of this library give you 
 * permission to link this library with independent modules to produce an 
 * executable, regardless of the license terms of these independent modules,
 * and to copy and distribute the resulting executable under terms of your 
 * choice, provided that you also meet, for each linked independent module,
 * the terms and conditions of the license of that module. An independent
 * module is a module which is not derived from or based on this library.
 */
package aphelion.client.graphics.world.event;


import aphelion.client.RENDER_LAYER;
import aphelion.client.graphics.world.ActorShip;
import aphelion.client.graphics.world.GCImageAnimation;
import aphelion.client.graphics.world.MapEntities;
import aphelion.shared.gameconfig.GCImage;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.entities.ActorPublic;
import aphelion.shared.physics.events.pub.ActorDiedPublic;
import aphelion.shared.physics.valueobjects.PhysicsShipPosition;
import aphelion.shared.resource.ResourceDB;

/**
 *
 * @author Joris
 */
public class ActorDiedTracker implements EventTracker
{
        private ResourceDB resourceDB;
        private PhysicsEnvironment physicsEnv;
        private MapEntities mapEntities;
        
        private boolean firstRun = true;
        private ActorDiedPublic event;
        private GCImageAnimation anim;
        
        private long renderDelay;
        private int renderingAt_state;

        public ActorDiedTracker(ResourceDB resourceDB, PhysicsEnvironment physicsEnv, MapEntities mapEntities)
        {
                this.resourceDB = resourceDB;
                this.physicsEnv = physicsEnv;
                this.mapEntities = mapEntities;
        }
        
        public void update(ActorDiedPublic event)
        {
                if (this.event == null)
                {
                        this.event = event;
                }
                
                assert this.event == event;
                
                int pid_state0 = event.getDied(0);
                
                if (firstRun)
                {
                        if (pid_state0 == 0)
                        {
                                return; // try again next tick
                        }
                        
                        ActorShip ship = mapEntities.getActorShip(pid_state0);
                        
                        if (ship == null)
                        {
                                return; // try again next tick
                        }
                        
                        // do not update the render delay after it has been set
                        renderDelay = ship.currentRenderDelay;
                        renderingAt_state = physicsEnv.getState(physicsEnv.getTick() - renderDelay);
                        
                        if (renderingAt_state < 0)
                        {
                                return;
                        }
                }
                
                firstRun = false;
                
                if (pid_state0 != 0  &&
                    event.hasOccured(renderingAt_state) && 
                    event.getOccuredAt(renderingAt_state) <= physicsEnv.getTick() - renderDelay)
                {
                        if (anim == null)
                        {
                                spawnAnimations();
                        }
                }
                else
                {
                        // the event no longer occured (timewarp), 
                        // remove the animations
                        removeAnimations();
                }
        }
        
        private void removeAnimations()
        {
                if (anim != null)
                {
                        anim.setDone();
                        anim = null;
                }
        }
        
        private void spawnAnimations()
        {
                final PhysicsShipPosition actorPos = new PhysicsShipPosition();
                
                long occuredAt_tick = event.getOccuredAt(renderingAt_state);
                
                ActorPublic actor = physicsEnv.getActor(event.getDied(0));
                if (actor == null)
                {
                        return;
                }

                GCImage image = actor.getActorConfigImage("ship-explosion-animation", resourceDB);

                if (image != null && actor.getHistoricPosition(actorPos, occuredAt_tick, false))
                {
                        anim = new GCImageAnimation(resourceDB, image);
                        anim.setPositionFromPhysics(actorPos.x, actorPos.y);
                        anim.setVelocityFromPhysics(actorPos.x_vel, actorPos.y_vel);
                        mapEntities.addAnimation(RENDER_LAYER.AFTER_LOCAL_SHIP, anim, null);
                }
        }
}