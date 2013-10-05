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

import aphelion.client.graphics.Graph;
import aphelion.client.RENDER_LAYER;
import aphelion.shared.resource.ResourceDB;
import aphelion.client.graphics.screen.Camera;
import aphelion.client.Fonts;
import aphelion.client.graphics.AnimatedColour;
import aphelion.shared.event.TickEvent;
import aphelion.shared.gameconfig.GCColour;
import aphelion.shared.gameconfig.GCImage;
import aphelion.shared.gameconfig.GCInteger;
import aphelion.shared.gameconfig.WrappedValueAbstract;
import aphelion.shared.physics.entities.ActorPublic;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.PhysicsMath;
import aphelion.shared.physics.valueobjects.PhysicsMoveable;
import aphelion.shared.physics.valueobjects.PhysicsMovement;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.swissarmyknife.Point;

import org.newdawn.slick.Color;
import org.newdawn.slick.Image;
import org.newdawn.slick.SpriteSheetCounted;

/**
 *
 * @author Joris
 */
public class ActorShip extends MapEntity implements TickEvent, WrappedValueAbstract.ChangeListener
{
        public final int pid;
        private final ActorPublic actor;
        public final boolean localPlayer;
        private String name;
        private final Animator animator;
                
        private int physRotation;
        private int spriteTile = 0;
        
        public float distSqToLocal;
        
        /** The position without delay (with dead reckoning) */
        public final Point shadowPosition = new Point(0, 0);

        public final RenderDelay renderDelay = new RenderDelay(50); // todo move the "50" to settings
        public long renderingAt_ticks;
        

        private ReusableAnimationList<ExhaustAnimation> exhaustAnimations;
        private ReusableAnimationList<GCImageAnimation> empedAnimations;
        private long lastExhaust_nanos;
        private long lastEmp_nanos;
        
        private boolean physicsInitialized = false;
        public GCInteger maxEnergy;
        public GCImage shipImage;
        public GCColour radarColour;
        public GCInteger shipRadius;
        public GCImage exhaust_image;
        public GCInteger exhaust_delay;
        public GCImage emped_image;
        public GCInteger emped_delay;
        
        public AnimatedColour radarAnim;

        public ActorShip(ResourceDB db, ActorPublic actor, boolean localPlayer, Animator animator)
        {
    		super(db);
                assert actor != null;
                this.localPlayer = localPlayer;
                this.actor = actor;
                this.pid = actor.getPid();
                this.animator = animator;
                
                exhaustAnimations = new ReusableAnimationList<>(db, animator, new ReusableAnimationList.Factory<ExhaustAnimation>()
                {
                        @Override
                        public ExhaustAnimation create(ResourceDB db)
                        {
                                return new ExhaustAnimation(db, exhaust_image);
                        }
                });
                
                empedAnimations = new ReusableAnimationList<>(db, animator, new ReusableAnimationList.Factory<GCImageAnimation>()
                {
                        @Override
                        public GCImageAnimation create(ResourceDB db)
                        {
                                return new GCImageAnimation(db, emped_image);
                        }
                });
                
                lastExhaust_nanos = Graph.nanoTime();
                lastEmp_nanos = Graph.nanoTime();
                tryInitPhysics();
        }
        
        public void setRotationFromPhysics(int physRotation)
        {
                this.physRotation = physRotation;
                
                if (shipImage != null)
                {
                        int pointsPerTile = PhysicsEnvironment.ROTATION_POINTS / 
                                (shipImage.getTilesHorizontal() * shipImage.getTilesVertical());
                        
                        if (pointsPerTile <= 0)
                        {
                                spriteTile = 0;
                        }
                        else
                        {
                                spriteTile = physRotation / pointsPerTile;
                        }
                }
        }
        
        public final void setNameFromPhysics(String name)
        {
                this.name = name;
        }
        
        public void setShadowPositionFromPhysics(int x, int y)
        {
                shadowPosition.x = x / 1024f;
                shadowPosition.y = y / 1024f;
        }
        
        public void updateDistanceToLocal(Point localPos)
        {
                Point dist = new Point(pos);
                dist.sub(localPos);
                this.distSqToLocal = dist.distanceSquared();
        }
        
        public ActorPublic getActor()
        {
                return actor;
        }
        
        public final void tryInitPhysics()
        {
                if (physicsInitialized)
                {
                        return;
                }
                
                if (actor.isRemoved())
                {
                        return;
                }
                
                
                physicsInitialized = true;
                // any get config should not fail if isDeleted returns false
                
                maxEnergy = actor.getActorConfigInteger("ship-energy");
                shipImage = actor.getActorConfigImage("ship-image", db);
                if (this.localPlayer)
                {
                        radarColour = actor.getActorConfigColour("ship-local-radar-colour");
                }
                else
                {
                        radarColour = actor.getActorConfigColour("ship-radar-colour");
                }
                
                shipRadius = actor.getActorConfigInteger("ship-radius");
                exhaust_image = actor.getActorConfigImage("ship-exhaust-image", db);
                exhaust_delay = actor.getActorConfigInteger("ship-exhaust-delay");
                emped_image = actor.getActorConfigImage("ship-emped-image", db);
                emped_delay = actor.getActorConfigInteger("ship-emped-delay");
                
                lastExhaust_nanos = Graph.nanoTime();
                lastEmp_nanos = Graph.nanoTime();
                
                setNameFromPhysics(actor.getName());
                setRotationFromPhysics(physRotation); // update tile
        }
        
        @Override
        public boolean render(Camera camera, int iteration)
        {
                if (!exists)
                {
                        return false;
                }
                
                if (iteration > 0)
                {
                        return false;
                }
                
                long now = Graph.nanoTime();
                
                Graph.g.setFont(Fonts.player_name);
                
                Point screenPos = new Point();
                camera.mapToScreenPosition(pos, screenPos);
                
                float x = screenPos.x;
                float y = screenPos.y;
                float w = 1;
                float h = 1;
                
                
                SpriteSheetCounted shipSprite = shipImage.getSpriteSheet();
                Image image = null;
                if (shipSprite != null)
                {
                        image = shipSprite.getSubImage(spriteTile);
                        x -= image.getWidth() / 2f * camera.zoom;
                        y -= image.getHeight() / 2f * camera.zoom;
                        
                        w = image.getWidth() * camera.zoom;
                        h = image.getHeight() * camera.zoom;
                }
                
                if (camera.radarRendering)
                {
                        if (this.radarColour.isSet())
                        {
                                if (radarAnim == null)
                                {
                                        radarAnim = radarColour.getAnimation();
                                }

                                Graph.g.setColor(radarAnim.get());
                                Graph.g.fillRect(x-1, y-1, 2, 2);
                        }
                        
                        return false;
                }
                
                if (this.exhaust_image.isSet())
                {
                        if (now - this.lastExhaust_nanos > this.exhaust_delay.get() * 1_000_000L)
                        {
                                this.lastExhaust_nanos = now;

                                PhysicsMoveable moveable = actor.getHistoricMovement(this.renderingAt_ticks, true);
                                PhysicsMovement move = moveable instanceof PhysicsMovement ? (PhysicsMovement) moveable : null;
                                
                                if (move != null && (move.up || move.down))
                                {
                                        ExhaustAnimation left = exhaustAnimations.register(RENDER_LAYER.AFTER_TILES, camera);
                                        ExhaustAnimation right = exhaustAnimations.register(RENDER_LAYER.AFTER_TILES, camera);
                                        left.pos.set(this.pos);

                                        PhysicsPoint offset = new PhysicsPoint();
                                        PhysicsMath.rotationToPoint(offset, 
                                                this.physRotation + PhysicsEnvironment.ROTATION_1_2TH, 
                                                this.shipRadius.get() + 1024 * 5);
                                        left.pos.x += offset.x / 1024f;
                                        left.pos.y += offset.y / 1024f;

                                        right.pos.set(left.pos);

                                        offset.set(0, 0);
                                        PhysicsMath.rotationToPoint(offset, 
                                                this.physRotation - PhysicsEnvironment.ROTATION_1_4TH, 
                                                1024 * 5);

                                        left.pos.x += offset.x / 1024f;
                                        left.pos.y += offset.y / 1024f;

                                        offset.set(0, 0);
                                        PhysicsMath.rotationToPoint(offset, 
                                                this.physRotation + PhysicsEnvironment.ROTATION_1_4TH, 
                                                1024 * 5);

                                        right.pos.x += offset.x / 1024f;
                                        right.pos.y += offset.y / 1024f;


                                }
                        }
                }
                else
                {
                        this.lastExhaust_nanos = now;
                }

                if (this.emped_image.isSet())
                {
                        if (now - this.lastEmp_nanos > this.emped_delay.get() * 1_000_000L)
                        {
                                this.lastEmp_nanos = now;

                                if (actor.isEmped())
                                {
                                        GCImageAnimation anim = empedAnimations.register(RENDER_LAYER.AFTER_TILES, camera);
                                        anim.pos.set(this.pos);
                                }
                        }
                }
                else
                {
                        this.lastEmp_nanos = now;
                }

                if (image != null)
                {
                        image.draw(x, y, w, h);

                        if (name != null)
                        {
                                Graph.g.setColor(Color.yellow);
                                Graph.g.drawString(
                                        name + " [" + renderDelay.get() + "]",
                                        x + image.getWidth(), 
                                        y + image.getHeight() / 2f); 
                        }

                        int energy = this.actor.getEnergy() / 1024;
                        if(energy < this.maxEnergy.get() / 2)
                        {
                                Graph.g.setColor(Color.yellow);

                                if(energy < this.maxEnergy.get() / 4)
                                {
                                        Graph.g.setColor(Color.red);                                		
                                }
                                Graph.g.drawString(energy  + "",
                                                x + image.getWidth(),
                                                y + image.getHeight() / 2f + 15);
                        }
                }

                /*int r = 14;
                Graph.g.setColor(Color.green);
                Graph.g.drawLine(screenPos.x, screenPos.y, screenPos.x, screenPos.y);
                Graph.g.drawRect(screenPos.x-r, screenPos.y-r, r+r, r+r);*/

                
                Fonts.setDefault();
                
                return false;
        }
        
        @Override
        public void tick(long tick)
        {
                tryInitPhysics();
        	renderDelay.tick(tick);
        }

        public boolean isLocalPlayer()
        {
                return localPlayer;
        }

        @Override
        public void gameConfigValueChanged(WrappedValueAbstract val)
        {
                if (val == this.radarColour)
                {
                        radarAnim = null;
                }
        }
}
