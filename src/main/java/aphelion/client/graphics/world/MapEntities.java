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

import aphelion.client.graphics.screen.Camera;
import aphelion.client.RENDER_LAYER;
import aphelion.shared.event.LoopEvent;
import aphelion.shared.event.TickEvent;
import aphelion.shared.physics.entities.ProjectilePublic;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.resource.ResourceDB;
import aphelion.shared.swissarmyknife.AttachmentConsumer;
import aphelion.shared.swissarmyknife.EmptyIterator;
import aphelion.shared.swissarmyknife.FilteredIterator;
import aphelion.shared.swissarmyknife.LinkedListHead;
import aphelion.shared.swissarmyknife.Point;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.hash.TIntObjectHashMap;
import java.util.Iterator;

/**
 *
 * @author Joris
 */
public class MapEntities implements TickEvent, LoopEvent, Animator
{
        private static final AttachmentConsumer<ProjectilePublic, Projectile> projectileAttachment 
                = new AttachmentConsumer<>(aphelion.shared.physics.entities.Projectile.attachmentManager);
        
        private TIntObjectHashMap<ActorShip> actorShips = new TIntObjectHashMap<>(64); // pid
        private PhysicsEnvironment physicsEnv;
        private ResourceDB resourceDB;
        private ActorShip localShip;
        private LinkedListHead<MapAnimation> animations[] = new LinkedListHead[RENDER_LAYER.values().length];

        public MapEntities(ResourceDB db)
        {
                this.resourceDB = db;
                
                for (int i = 0; i < animations.length; i++)
                {
                        animations[i] = new LinkedListHead<>();
                }
        }
        
        
        public void setPhysicsEnv(PhysicsEnvironment physicsEnv)
        {
                this.physicsEnv = physicsEnv;
        }

        
        public void addShip(ActorShip en)
        {
                actorShips.put(en.pid, en);
                if (en.localPlayer)
                {
                        assert localShip == null; // since the getter is singular
                        localShip = en;
                }
        }
        
        public void removeShip(ActorShip en)
        {
                if (en == null) { return; }
                actorShips.remove(en.pid);
                if (localShip == en)
                {
                        localShip = null;
                }
        }
        
        public ActorShip getLocalShip()
        {
                return localShip; // may be null
        }
        
        public Iterator<ActorShip> shipIterator()
        {
                Iterator<ActorShip> it = new Iterator<ActorShip>() 
                {
                        TIntObjectIterator<ActorShip> wrapped = actorShips.iterator();
                        
                        @Override
                        public boolean hasNext()
                        {
                                return wrapped.hasNext();
                        }

                        @Override
                        public ActorShip next()
                        {
                                wrapped.advance();
                                return wrapped.value();
                        }

                        @Override
                        public void remove()
                        {
                                wrapped.remove();
                        }
                };
                return it;
        }
        
        public Iterator<ActorShip> shipNoLocalIterator()
        {
                Iterator<ActorShip> it = new Iterator<ActorShip>() 
                {
                        TIntObjectIterator<ActorShip> wrapped;
                        ActorShip next;
                        
                        {
                                wrapped =  actorShips.iterator();
                                advanceUntilCorrect();
                        }
                        
                        @Override
                        public boolean hasNext()
                        {
                                return next != null;
                        }

                        @Override
                        public ActorShip next()
                        {
                                ActorShip ret = next;
                                advanceUntilCorrect();
                                return ret;
                        }

                        @Override
                        public void remove()
                        {
                                wrapped.remove();
                        }
                        
                        private void advanceUntilCorrect()
                        {
                                while (wrapped.hasNext())
                                {
                                        wrapped.advance();
                                        next = wrapped.value();
                                        if (!next.localPlayer)
                                        {
                                                return;
                                        }
                                }
                                
                                next = null;
                        }
                };
                return it;
        }
        
        public Iterable<ActorShip> shipsNoLocal()
        {
                return new Iterable<ActorShip>()
                {
                        @Override
                        public Iterator<ActorShip> iterator()
                        {
                                return shipNoLocalIterator();
                        }
                };
        }
        
        public ActorShip getActorShip(int pid)
        {
                return actorShips.get(pid);
        }
        
        public Iterator<Projectile> projectileIterator(final boolean includeRemoved)
        {
                if (physicsEnv == null)
                {
                        return new EmptyIterator<>();
                }
                
                Iterator<Projectile> it = new FilteredIterator<Projectile, ProjectilePublic>(physicsEnv.projectileIterator(0)) 
                {
                        @Override
                        public Projectile filter(ProjectilePublic next)
                        {
                                if (!includeRemoved && next.isRemoved())
                                {
                                        return null;
                                }
                                
                                Projectile projectile = projectileAttachment.get(next);
                                if (projectile == null)
                                {
                                        projectile = new Projectile(resourceDB, next);
                                        projectileAttachment.set(next, projectile);
                                        // caveat: if a timewarp destroys and recreates a projectile, 
                                        // this data is lost

                                }
                                
                                return projectile;
                        }
                };
                
                return it;
        }
        
        public Iterable<Projectile> projectiles(final boolean includeRemoved)
        {
                return new Iterable<Projectile>()
                {
                        @Override
                        public Iterator<Projectile> iterator()
                        {
                                return projectileIterator(includeRemoved);
                        }
                };
        }
        
        public ActorShip findNearestActor(Point pos, boolean includeLocal)
        {
                TIntObjectIterator<ActorShip> it = actorShips.iterator();
                
                Point diff = new Point();
                
                ActorShip nearest = null;
                float nearest_dist = 0;
                
                while (it.hasNext())
                {
                        it.advance();
                        ActorShip ship = it.value();
                        
                        if (ship.localPlayer && !includeLocal)
                        {
                                continue;
                        }
                        
                        if (!ship.exists)
                        {
                                continue;
                        }
                        
                        diff.set(ship.pos);
                        diff.sub(pos);
                        float dist = diff.distanceSquared();
                        
                        if (nearest == null || dist < nearest_dist)
                        {
                                nearest_dist = dist;
                                nearest = ship;
                        }
                }
                
                return nearest;
        }
        
        @Override
        public void addAnimation(RENDER_LAYER layer, MapAnimation animation, Camera camera)
        {
                animation.animating = true;
                animations[layer.id].append(animation.link);
                animation.camera = camera;
        }
        
        public Iterator<MapAnimation> animationIterator(final RENDER_LAYER layer, final Camera camera)
        {
                return new FilteredIterator<MapAnimation, MapAnimation>(animations[layer.id].iterator())
                {
                        @Override
                        public MapAnimation filter(MapAnimation next)
                        {
                                if (next.camera == null || next.camera == camera)
                                {
                                        return next;
                                }

                                return null;
                        }
                };
        }
        
        public Iterable<MapAnimation> animations(final RENDER_LAYER layer, final Camera camera)
        {
                return new Iterable<MapAnimation>()
                {
                        @Override
                        public Iterator<MapAnimation> iterator()
                        {
                                return animationIterator(layer, camera);
                        }
                };
        }

        @Override
        public void tick(long tick)
        {
                TIntObjectIterator<ActorShip> itActor = actorShips.iterator();
                
                while (itActor.hasNext())
                {
                        itActor.advance();
                        itActor.value().tick(tick);
                }
                
                Iterator<Projectile> itProjectile = projectileIterator(false);
                while (itProjectile.hasNext())
                {
                        itProjectile.next().tick(tick);
                }
                
                for (LinkedListHead<MapAnimation> animationList : animations)
                {
                        for (MapAnimation anim : animationList)
                        {
                                anim.tick(tick);
                        }
                }
        }

        @Override
        public void loop()
        {
                for (int i = 0; i < animations.length; ++i)
                {
                        Iterator<MapAnimation> animIt = animations[i].iterator();
                        while (animIt.hasNext())
                        {
                                MapAnimation animation = animIt.next();

                                if (animation.isDone())
                                {
                                        animation.animating = false;
                                        animIt.remove();
                                }
                        }
                }
        }
        
        
}
