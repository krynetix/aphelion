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
package aphelion.client.graphics.world;

import aphelion.client.RENDER_LAYER;
import aphelion.shared.gameconfig.GCImage;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.entities.ActorPublic;
import aphelion.shared.physics.entities.ProjectilePublic;
import aphelion.shared.physics.events.pub.ProjectileExplosionPublic;
import aphelion.shared.physics.events.pub.ProjectileExplosionPublic.EXPLODE_REASON;
import static aphelion.shared.physics.events.pub.ProjectileExplosionPublic.EXPLODE_REASON.EXPIRATION;
import static aphelion.shared.physics.events.pub.ProjectileExplosionPublic.EXPLODE_REASON.HIT_SHIP;
import static aphelion.shared.physics.events.pub.ProjectileExplosionPublic.EXPLODE_REASON.HIT_TILE;
import static aphelion.shared.physics.events.pub.ProjectileExplosionPublic.EXPLODE_REASON.PROX_DELAY;
import static aphelion.shared.physics.events.pub.ProjectileExplosionPublic.EXPLODE_REASON.PROX_DIST;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.physics.valueobjects.PhysicsShipPosition;
import aphelion.shared.resource.ResourceDB;
import java.util.ArrayList;

/**
 *
 * @author Joris
 */
public class ProjectileExplosionTracker
{
        private ResourceDB resourceDB;
        private PhysicsEnvironment physicsEnv;
        private MapEntities mapEntities;
        
        private boolean firstRun = true;
        private ProjectileExplosionPublic event;
        
        private long renderDelay;
        private int renderingAt_state;
        
        private ArrayList<GCImageAnimation> animations;

        public ProjectileExplosionTracker(ResourceDB resourceDB, PhysicsEnvironment physicsEnv, MapEntities mapEntities)
        {
                this.resourceDB = resourceDB;
                this.physicsEnv = physicsEnv;
                this.mapEntities = mapEntities;
        }
        
        public void update(ProjectileExplosionPublic event)
        {
                if (this.event == null)
                {
                        this.event = event;
                }
                
                assert this.event == event;
                
                ProjectilePublic physicsProjectile_state0 = event.getProjectile(0);
                Projectile projectile = mapEntities.physicsProjectileToGraphics(physicsProjectile_state0);
                
                if (firstRun)
                {
                        // do not update the render delay after it has been set
                        renderDelay = projectile.currentRenderDelay;
                        renderingAt_state = physicsEnv.getState(physicsEnv.getTick() - renderDelay);
                        
                        if (renderingAt_state < 0)
                        {
                                return;
                        }
                }
                
                firstRun = false;
                
                if (event.hasOccured(renderingAt_state) && 
                    event.getOccuredAt(renderingAt_state) <= physicsEnv.getTick() - renderDelay)
                {
                        if (animations == null)
                        {
                                spawnAnimations();
                        }
                }
                else
                {
                        //System.out.println("has not occured! " + (physicsEnv.getTick() - renderDelay) + " " + event.getOccuredAt(renderingAt_state));
                        if (animations != null)
                        {
                                // the event no longer occured (timewarp), 
                                // remove the animations
                                for (GCImageAnimation anim : animations)
                                {
                                        anim.setDone();
                                }
                                animations = null;
                        }
                }
        }
        
        private void spawnAnimations()
        {
                final PhysicsPoint pos = new PhysicsPoint();
                final PhysicsShipPosition actorPos = new PhysicsShipPosition();
                
                ProjectilePublic physicsProjectile_state0 = event.getProjectile(0);
                
                long occuredAt_tick = event.getOccuredAt(renderingAt_state);
                
                animations = new ArrayList<>(
                        event.getKilledSize(renderingAt_state)
                        + 1
                        + event.getCoupledProjectilesSize(renderingAt_state)
                );
                

                for (Integer pid : event.getKilled(renderingAt_state))
                {
                        ActorPublic actor = physicsEnv.getActor(pid);
                        if (actor == null) continue;

                        GCImage image = actor.getActorConfigImage("ship-explosion-animation", resourceDB);

                        if (image != null && actor.getHistoricPosition(actorPos, occuredAt_tick, false))
                        {
                                GCImageAnimation anim = new GCImageAnimation(resourceDB, image);
                                anim.setPositionFromPhysics(actorPos.x, actorPos.y);
                                anim.setVelocityFromPhysics(actorPos.x_vel, actorPos.y_vel);
                                mapEntities.addAnimation(RENDER_LAYER.AFTER_LOCAL_SHIP, anim, null);
                                animations.add(anim);
                        }
                }

                PhysicsPoint tileHit = new PhysicsPoint();
                event.getHitTile(renderingAt_state, tileHit);

                GCImage hitImage;
                EXPLODE_REASON reason = event.getReason(renderingAt_state);
                switch (reason)
                {
                        case EXPIRATION:
                                hitImage = physicsProjectile_state0.getWeaponConfigImage(
                                        "projectile-expiration-animation", 
                                        resourceDB);
                                break;

                        case PROX_DELAY:
                                hitImage = physicsProjectile_state0.getWeaponConfigImage(
                                        "projectile-prox-animation", 
                                        resourceDB);
                                break;

                        case PROX_DIST:
                                hitImage = physicsProjectile_state0.getWeaponConfigImage(
                                        "projectile-prox-animation", 
                                        resourceDB);
                                break;

                        case HIT_TILE:
                                hitImage = physicsProjectile_state0.getWeaponConfigImage(
                                        "projectile-hit-tile-animation", 
                                        resourceDB);
                                break;

                        case HIT_SHIP:
                                hitImage = physicsProjectile_state0.getWeaponConfigImage(
                                        "projectile-hit-ship-animation", 
                                        resourceDB);
                                break;

                        default:
                                assert false;
                                return;
                }


                if (hitImage != null)
                {
                        event.getPosition(renderingAt_state, pos);
                        if (pos.set)
                        {
                                GCImageAnimation anim = new GCImageAnimation(resourceDB, hitImage);

                                anim.setPositionFromPhysics(pos);
                                mapEntities.addAnimation(RENDER_LAYER.AFTER_LOCAL_SHIP, anim, null);
                                animations.add(anim);
                        }

                        for (ProjectilePublic coupledProjectile : event.getCoupledProjectiles(renderingAt_state))
                        {
                                coupledProjectile.getHistoricPosition(pos, occuredAt_tick, false);

                                GCImageAnimation anim = new GCImageAnimation(resourceDB, hitImage);

                                anim.setPositionFromPhysics(pos);
                                mapEntities.addAnimation(RENDER_LAYER.AFTER_LOCAL_SHIP, anim, null);
                                animations.add(anim);
                        }
                }
        }
}
