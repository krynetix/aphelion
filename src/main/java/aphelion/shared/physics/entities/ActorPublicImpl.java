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
 *
 * 
 * 
 */

package aphelion.shared.physics.entities;

import aphelion.shared.gameconfig.ConfigSelection;
import aphelion.shared.gameconfig.GCBoolean;
import aphelion.shared.gameconfig.GCBooleanList;
import aphelion.shared.gameconfig.GCColour;
import aphelion.shared.gameconfig.GCImage;
import aphelion.shared.gameconfig.GCInteger;
import aphelion.shared.gameconfig.GCIntegerList;
import aphelion.shared.gameconfig.GCString;
import aphelion.shared.gameconfig.GCStringList;
import aphelion.shared.net.protobuf.GameOperation;
import aphelion.shared.physics.State;
import aphelion.shared.physics.valueobjects.PhysicsMoveable;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.physics.valueobjects.PhysicsShipPosition;
import aphelion.shared.physics.WEAPON_SLOT;
import aphelion.shared.resource.ResourceDB;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * This wrapper makes sure that if an actor gets destroyed 
 * and re-added because of a time warp, the correct actor will be used.
 * 
 * @author Joris
 */
public class ActorPublicImpl implements ActorPublic
{
        WeakReference<Actor> actorRef;
        final int pid;
        private final State state;
        
        // The actor (and its config selection) may get destroyed in a timewarp
        // Any config values that were retreived by non physics code will then stop 
        // being updated properly (such as when the actor changes ship / freq, etc).
        // If the actor object changes, adopt all the old values.
        private ConfigSelection actorConfigSelection_lastSeen;
        private HashMap<String, ConfigSelection> weaponConfigSelections_lastSeen = new HashMap<>();

        public ActorPublicImpl(Actor actor, State state)
        {
                this.actorRef = new WeakReference<>(actor);
                this.pid = actor.pid;
                this.state = state;
                this.actorConfigSelection_lastSeen = actor.actorConfigSelection;
        }
        
        public ActorPublicImpl(int pid, State state)
        {
                this.actorRef = null;
                this.pid = pid;
                this.state = state;
        }
        
        Actor getActor()
        {
                Actor actor;
                if (pid == 0)
                {
                        return null;
                }
                
                actor = this.actorRef == null ? null : this.actorRef.get();
                
                if (actor == null || actor.pid != this.pid || actor.removed)
                {
                        actor = state.actors.get(this.pid);
                        if (actor == null)
                        {
                                this.actorRef = null;
                        }
                        else
                        {
                                this.actorRef = new WeakReference<>(actor);
                                
                                if (this.actorConfigSelection_lastSeen != actor.actorConfigSelection)
                                {
                                        actor.actorConfigSelection.adoptAllValues(actorConfigSelection_lastSeen);
                                        this.actorConfigSelection_lastSeen = actor.actorConfigSelection;
                                        
                                        // Adopt weapon config
                                        Iterator<Map.Entry<String, ConfigSelection>> it 
                                                = this.weaponConfigSelections_lastSeen.entrySet().iterator();
                                        
                                        while (it.hasNext())
                                        {
                                                Map.Entry<String, ConfigSelection> entry = it.next();
                                                ConfigSelection newSelection = actor.getWeaponConfig(entry.getKey()).configSelection;
                                                newSelection.adoptAllValues(entry.getValue());
                                                entry.setValue(newSelection);
                                        }
                                }
                        }
                        
                }
                
                return actor;
        }
        
        @Override
        public boolean hasReference()
        {
                return getActor() != null;
        }
        
        @Override
        public boolean isDeleted()
        {
                Actor actor;
                actor = getActor();
                if (actor == null)
                {
                        return true;
                }
                
                return actor.removed;
        }

        @Override
        public int getStateId()
        {
                return state.id;
        }
        
        @Override
        public int getPid()
        {
                return pid;
        }
        
        /** Gets the most current position, velocity and rotation for the actor.
         * @param pos The object to fill with position, velocity, and rotation
         * @return true if the actor exists and pos has been filled 
         */
        @Override
        public boolean getPosition(PhysicsShipPosition pos)
        {
                Actor actor;
                actor = getActor();
                pos.set = false;
                if (actor == null)
                {
                        return false; // deleted
                }
                else
                {
                        pos.set = true;
                        pos.x = actor.pos.pos.x;
                        pos.y = actor.pos.pos.y;
                        pos.x_vel = actor.pos.vel.x;
                        pos.y_vel = actor.pos.vel.y;
                        pos.rot = actor.rot.points;
                        pos.rot_snapped = actor.rot.snapped;
                        return true;
                }
        }
        
        /** Gets the historic position and rotation for the actor.
         * @param pos The object to fill with position and rotation
         * @param tick
         * @param lookAtOtherStates 
         * @return true if the actor exists and pos has been filled
         */
        @Override
        public boolean getHistoricPosition(PhysicsShipPosition pos, long tick, boolean lookAtOtherStates)
        {
                Actor actor;
                actor = getActor();
                pos.set = false;
                
                if (actor == null)
                {
                        return false; // deleted
                }
                
                return actor.getHistoricPosition(pos, tick, lookAtOtherStates);
        }
        
        @Override
        public PhysicsMoveable getHistoricMovement(long tick, boolean lookAtOtherStates)
        {
                Actor actor;
                actor = getActor();
                
                if (actor == null)
                {
                        return null; // deleted
                }
                
                return actor.getHistoricMovement(tick, lookAtOtherStates);
        }
        
        @Override
        public String getName()
        {
                Actor actor;
                actor = getActor();
                if (actor == null)
                {
                        return null; // deleted
                }
                else
                {
                        return actor.name;
                }
        }

        @Override
        public boolean canFireWeapon(WEAPON_SLOT weapon)
        {
                Actor actor;
                actor = getActor();
                
                if (actor == null)
                {
                        return false;
                }
                else
                {
                        return actor.canFireWeapon(weapon, state.tick_now);
                }
        }

        @Override
        public int getRadius()
        {
                Actor actor;
                actor = getActor();

                if (actor == null)
                {
                        return 0;
                }
                else
                {
                        return actor.radius.get();
                }
        }

        @Override
        public void findSpawnPoint(PhysicsPoint result, long tick)
        {
                Actor actor;
                actor = getActor();

                if (actor == null)
                {
                        result.unset();
                }
                else
                {
                        actor.findSpawnPoint(result, tick);
                }
        }
        
        @Override
        public int randomRotation(long tick)
        {
                Actor actor;
                actor = getActor();

                if (actor == null)
                {
                        return 0;
                }
                else
                {
                        return actor.randomRotation(tick);
                }
        }
        
        @Override
        public GCString getWeaponKey(WEAPON_SLOT slot)
        {
                Actor actor;
                actor = getActor();

                if (actor == null)
                {
                        return null;
                }
                else
                {
                        return actor.weaponSlots[slot.id].weaponKey;
                }
        }

        @Override
        public GCInteger getActorConfigInteger(String name)
        {
                if (actorConfigSelection_lastSeen == null)
                {
                        return null;
                }
                
                return actorConfigSelection_lastSeen.getInteger(name);
        }

        @Override
        public GCString getActorConfigString(String name)
        {
                if (actorConfigSelection_lastSeen == null)
                {
                        return null;
                }
                
                return actorConfigSelection_lastSeen.getString(name);
        }

        @Override
        public GCBoolean getActorConfigBoolean(String name)
        {
                if (actorConfigSelection_lastSeen == null)
                {
                        return null;
                }
                
                return actorConfigSelection_lastSeen.getBoolean(name);
        }
        
        @Override
        public GCIntegerList getActorConfigIntegerList(String name)
        {
                if (actorConfigSelection_lastSeen == null)
                {
                        return null;
                }
                
                return actorConfigSelection_lastSeen.getIntegerList(name);
        }

        @Override
        public GCStringList getActorConfigStringList(String name)
        {
                if (actorConfigSelection_lastSeen == null)
                {
                        return null;
                }
                
                return actorConfigSelection_lastSeen.getStringList(name);
        }

        @Override
        public GCBooleanList getActorConfigBooleanList(String name)
        {
                if (actorConfigSelection_lastSeen == null)
                {
                        return null;
                }
                
                return actorConfigSelection_lastSeen.getBooleanList(name);
        }
        
        @Override
        public GCImage getActorConfigImage(String name, ResourceDB db)
        {
                if (actorConfigSelection_lastSeen == null)
                {
                        return null;
                }
                
                return actorConfigSelection_lastSeen.getImage(name, db);
        }
        
        @Override
        public GCColour getActorConfigColour(String name)
        {
                if (actorConfigSelection_lastSeen == null)
                {
                        return null;
                }
                
                return actorConfigSelection_lastSeen.getColour(name);
        }
        
        
        private ConfigSelection getWeaponSelection(String weaponKey)
        {
                ConfigSelection ret = weaponConfigSelections_lastSeen.get(weaponKey);
                if (ret == null)
                {
                        Actor actor = getActor();
                        if (actor == null)
                        {
                                return null;
                        }
                        
                        ret = actor.getWeaponConfig(weaponKey).configSelection;
                        weaponConfigSelections_lastSeen.put(weaponKey, ret);
                }
                
                return ret;
        }
        
        @Override
        public GCInteger getActorConfigInteger(String weaponKey, String name)
        {
                ConfigSelection sel = getWeaponSelection(weaponKey);
                if (sel == null)
                {
                        return null;
                }
                
                return sel.getInteger(name);
        }

        @Override
        public GCString getActorConfigString(String weaponKey, String name)
        {
                ConfigSelection sel = getWeaponSelection(weaponKey);
                if (sel == null)
                {
                        return null;
                }
                
                return sel.getString(name);
        }

        @Override
        public GCBoolean getActorConfigBoolean(String weaponKey, String name)
        {
                ConfigSelection sel = getWeaponSelection(weaponKey);
                if (sel == null)
                {
                        return null;
                }
                
                return sel.getBoolean(name);
        }
        
        @Override
        public GCIntegerList getActorConfigIntegerList(String weaponKey, String name)
        {
                ConfigSelection sel = getWeaponSelection(weaponKey);
                if (sel == null)
                {
                        return null;
                }
                
                return sel.getIntegerList(name);
        }

        @Override
        public GCStringList getActorConfigStringList(String weaponKey, String name)
        {
                ConfigSelection sel = getWeaponSelection(weaponKey);
                if (sel == null)
                {
                        return null;
                }
                
                return sel.getStringList(name);
        }

        @Override
        public GCBooleanList getActorConfigBooleanList(String weaponKey, String name)
        {
                ConfigSelection sel = getWeaponSelection(weaponKey);
                if (sel == null)
                {
                        return null;
                }
                
                return sel.getBooleanList(name);
        }
        
        @Override
        public GCImage getActorConfigImage(String weaponKey, String name, ResourceDB db)
        {
                ConfigSelection sel = getWeaponSelection(weaponKey);
                if (sel == null)
                {
                        return null;
                }
                
                return sel.getImage(name, db);
        }
        
        @Override
        public GCColour getActorConfigColour(String weaponKey, String name)
        {
                ConfigSelection sel = getWeaponSelection(weaponKey);
                if (sel == null)
                {
                        return null;
                }
                
                return sel.getColour(name);
        }

        @Override
        public int getEnergy()
        {
                Actor actor;
                actor = getActor();

                if (actor == null)
                {
                        return 0;
                }
                else
                {
                        return actor.energy.get(state.tick_now);
                }
        }

        @Override
        public boolean isDead()
        {
                Actor actor;
                actor = getActor();

                if (actor == null)
                {
                        return false;
                }
                else
                {
                        return actor.dead;
                }
        }

        @Override
        public long getSeed()
        {
                Actor actor;
                actor = getActor();

                if (actor == null)
                {
                        return 0;
                }
                else
                {
                        return actor.seed;
                }
        }

        @Override
        public long getSpawnedAt()
        {
                Actor actor;
                actor = getActor();

                if (actor == null)
                {
                        return 0;
                }
                else
                {
                        return actor.spawnAt_tick;
                }
        }

        @Override
        public String getShip()
        {
                Actor actor;
                actor = getActor();

                if (actor == null)
                {
                        return null;
                }
                else
                {
                        return actor.ship;
                }
        }

        @Override
        public boolean canChangeShip()
        {
                Actor actor;
                actor = getActor();

                if (actor == null)
                {
                        return false;
                }
                else
                {
                        if (actor.removed || actor.dead)
                        {
                                return false;
                        }
                        
                        if (actor.energy.get(state.tick_now) < actor.getMaxEnergy())
                        {
                                return false;
                        }
                        
                        return true;
                }
        }

        @Override
        public boolean canBoost()
        {
                Actor actor;
                actor = getActor();

                if (actor == null)
                {
                        return false;
                }
                
                if (actor.removed || actor.dead)
                {
                        return false;
                }

                if (actor.energy.get(state.tick_now-1) < actor.boostEnergy.get())
                {
                        return false;
                }

                return true;

        }

        @Override
        public boolean isEmped()
        {
                Actor actor;
                actor = getActor();

                if (actor == null)
                {
                        return false;
                }
                
                if (actor.removed || actor.dead)
                {
                        return false;
                }
                
                if (actor.empUntil_tick != null && state.tick_now <= actor.empUntil_tick)
                {
                        return true;
                }
                
                return false;
        }

        @Override
        public Iterator<ProjectilePublic> projectileIterator()
        {
                Actor actor;
                actor = getActor();

                if (actor == null)
                {
                        return null;
                }
                
                return (Iterator<ProjectilePublic>) (Object) actor.projectiles.iteratorReadOnly();
        }

        @Override
        public boolean getSync(GameOperation.ActorSync.Builder b)
        {
                Actor actor;
                actor = getActor();

                if (actor == null)
                {
                        return false;
                }
                
                actor.getSync(b);
                return true;
        }

        


}
