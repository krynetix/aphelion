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

package aphelion.shared.physics.entities;

import aphelion.shared.swissarmyknife.EntityGridEntity;
import aphelion.shared.swissarmyknife.EntityGrid;
import aphelion.shared.gameconfig.GCInteger;
import aphelion.shared.physics.PhysicsMap;
import aphelion.shared.physics.State;
import aphelion.shared.physics.valueobjects.*;
import aphelion.shared.swissarmyknife.LinkedListEntry;
import aphelion.shared.swissarmyknife.SwissArmyKnife;

/**
 *
 * @author Joris
 */
public abstract class MapEntity implements EntityGridEntity
{
        public long createdAt_tick;
        private boolean removed = false;
        /** valid if removed = true */
        public long removedAt_tick;
        
        public final LinkedListEntry<MapEntity> dirtyPositionPathLink_state = new LinkedListEntry<>(this);
        
        /** Tracks for which ticks the position path needs reexecuting.
         * Any dirty tick will need to reexecute dead reckon.
         * This is consistent as long as everything is received in-order (otherwise a timewarp is needed)
         */
        public final SequentialDirtyTracker dirtyPositionPathTracker = new SequentialDirtyTracker();
        
        /** Set if this entity was removed during State.resetTo(). 
         * It is very likely this entity will be reused if this attribute is set.
         * This attribute is used for assertions and should not be modified in MapEntity.resetTo
         */
        public boolean removedDuringReset = false;
        
        public final State state;
        public final MapEntity[] crossStateList;
        /** This attribute must only be accessed by EntityGrid. */
        public final LinkedListEntry<MapEntity> entityGridEntry = new LinkedListEntry<>(this);
        
        public final int HISTORY_LENGTH;
        /** The most recent position. 
         * Most methods work on this value, after which it is added to the posHistory.
         */
        public final PhysicsPositionVector pos = new PhysicsPositionVector();
        /** The history of all positions of this entity. */
        public final PhysicsPointHistoryDetailed posHistory;
        /** The history of all velocities of this entity. */
        public final PhysicsPointHistory velHistory;
        
        /** The radius in pixels * 1024 (not diameter) of the object. The radius may be 0 if it has no
         * diameter, like a bullet.
         */
        public GCInteger radius;

        //public long debug_id;
        //public long debug_id_with_reset;
        MapEntity(State state, MapEntity[] crossStateList, long createdAt_tick, int historyLength)
        {
                //this.debug_id = ++state.env.debug_entities;
                //this.debug_id_with_reset = debug_id;
                this.state = state;
                this.crossStateList = crossStateList;
                crossStateList[state.id] = this;
                this.createdAt_tick = createdAt_tick;
                this.HISTORY_LENGTH = historyLength;
                this.posHistory = new PhysicsPointHistoryDetailed(createdAt_tick, HISTORY_LENGTH);
                assert posHistory.HISTORY_LENGTH == HISTORY_LENGTH;
                this.velHistory = new PhysicsPointHistoryDetailed(createdAt_tick, HISTORY_LENGTH);
                assert velHistory.HISTORY_LENGTH == HISTORY_LENGTH;
                dirtyPositionPathTracker.setFirstDirtyTick(createdAt_tick+1);
        }

        /**
         * Remove the entity from all lists. This is called during the ticking loop after as the result of a soft remove.
         * @param tick
         */
        public void hardRemove(long tick)
        {
                this.softRemove(tick);
                
                crossStateList[state.id] = null;
                dirtyPositionPathLink_state.remove();
        }

        /**
         * Mark the entity for removal, but do not really remove it from any list yet.
         * The ticking loop will execute a hardRemove() when this entity has been removed in all states.
         * This ensures timewarps will work properly
         * @param tick
         */
        public void softRemove(long tick)
        {
                if (removed)
                {
                        if (removedAt_tick > tick)
                        {
                                removedAt_tick = tick;
                        }
                }
                else
                {
                        removedAt_tick = tick;
                }
                
                removed = true;
        }

        /**
         * Has the entity been removed at the given tick?.
         * This method does not check createdAt_tick, use isNonExistent() for that
         * @param tick The tick to check by
         * @return true if the entity has been removed
         */
        public boolean isRemoved(long tick)
        {
                if (this.removed)
                {
                        return this.removedAt_tick <= tick;
                }

                return false;
        }

        /**
         * Has this entity been scheduled for removal (regardless of the tick)
         * @return true if the entity has been scheduled for removal
         */
        public boolean isRemovedSet()
        {
                return this.removed;
        }

        /** Does this entity not yet, or no longer exist at the tick of the state this entity belongs to?.
         * An entity is considered nonexistent before its creation (this.createdAt_tick) and
         * after its (soft) removal (this.removedAt_tick)
         * @return true if the entity is nonexistent
         */
        public boolean isNonExistent()
        {
                return this.isNonExistent(this.state.tick_now);
        }

        /** Does this entity not yet, or no longer exist at the specified tick?.
         * An entity is considered nonexistent before its creation (this.createdAt_tick) and
         * after its (soft) removal (this.removedAt_tick)
         * @param tick The tick to check by
         * @return true if the entity is nonexistent
         */
        public boolean isNonExistent(long tick)
        {
                if (this.isRemoved(tick))
                {
                        return true;
                }
                
                return tick < this.createdAt_tick;
        }

        /**
         * Mark that the historic position at the given tick is "dirty".
         * The position will be rolled back to the tick before this tick and resimulated, but only for this entity.
         * The effect is not instant, it is deferred to the next ticked loop.
         * @param dirtyTick The tick that is dirty
         */
        public void markDirtyPositionPath(long dirtyTick)
        {
                dirtyTick = Math.max(this.createdAt_tick, dirtyTick);
                dirtyPositionPathTracker.markDirty(dirtyTick);

                if (dirtyPositionPathLink_state.head == null)
                {
                        state.dirtyPositionPathList.append(dirtyPositionPathLink_state);
                }
        }
        
        public MapEntity getEntityAt(long tick, boolean ignoreSoftDelete, boolean lookAtOtherStates)
        {
                MapEntity en;
                if (lookAtOtherStates)
                {
                        int ticks_ago = (int) (state.tick_now - tick);

                        int states_ago = SwissArmyKnife.divideFloor(ticks_ago, state.econfig.TRAILING_STATE_DELAY);

                        int state_id = state.id + states_ago;
                        if (state_id < 0 || state_id >= crossStateList.length)
                        {
                                return null;
                        }
                        
                        en = crossStateList[state_id];
                }
                else
                {
                        en = this;
                }
                
                if (en == null)
                {
                        return null;
                }

                if (en.createdAt_tick > tick)
                {
                        return null;
                }
                
                if (!ignoreSoftDelete)
                {
                        if (en.isNonExistent(tick))
                        {
                                return null;
                        }
                }
                
                return en;
        }

        /** Gets the most current position, velocity for the entity.
         * @param pos The object to fill with position, velocity, and rotation
         * @return true if the pos has been filled
         */
        public void getPosition(PhysicsPositionVector pos)
        {
                pos.set(this.pos);
        }
        
        /** Get the historic position of this entity.
         * @param pos The position is filled in this object
         * @param tick 
         * @param lookAtOtherStates If set, also look at entities in older states. 
         *        If the history of this actor overlaps with the older one, the position 
         *        from the older one is used.
         * @return true if a position was found (the entity existed at this point in time)
         */
        public boolean getHistoricPosition(PhysicsPositionVector pos, long tick, boolean lookAtOtherStates)
        {
                MapEntity en = getEntityAt(tick, false, lookAtOtherStates);

                if (en == null)
                {
                        pos.unset();
                        return false; // deleted or too far in the past
                }

                en.posHistory.get(pos.pos, tick);
                en.velHistory.get(pos.vel, tick);
                assert pos.pos.set == pos.vel.set;
                return pos.pos.set;
        }
        
        /** Get the historic smooth position of this entity.
         * @param pos The position is filled in this object
         * @param tick 
         * @param lookAtOtherStates If set, also look at entities in older states. 
         *        If the history of this actor overlaps with the older one, the position 
         *        from the older one is used.
         * @return true if a position was found (the entity existed at this point in time)
         */
        public boolean getHistoricSmoothPosition(PhysicsPoint pos, long tick, boolean lookAtOtherStates)
        {
                // Smooth history is not tracked for all map entities
                pos.unset();
                return false;
        }

        /**
         * Simulate the given amount of ticks of dead reckoning, collision, force emitters, etc
         * @param map Map
         * @param tick_now The tick this calculation belongs to. The last tick that will be calculated is
         *                 (tick_now + reckon_ticks - 1)
         * @param reckon_ticks The amount of ticks to perform
         * @param applyForceEmitters When this method is used to correct late operations, force emitters
         *                           must also be parsed. However when this dead reckon step is not for a late tick,
         *                           forces are calculated using a much more efficient method (using the entity grid,
         *                           see State.tickForceEmitters). This second method is considered the consistent one
         *                           and is thus used during timewarps.
         */
        public abstract void performDeadReckoning(PhysicsMap map, long tick_now, long reckon_ticks, boolean applyForceEmitters);
        
        /** Add (or update) the current position to the history
         *
         * @param tick The tick the current position (this.pos) belongs to
         */
        public void updatedPosition(long tick)
        {
                posHistory.setHistory(tick, pos.pos);
                velHistory.setHistory(tick, pos.vel);
                dirtyPositionPathTracker.resolved(tick);
                
                if (tick == state.tick_now)
                {
                        if (this.isRemoved(tick))
                        {
                                state.entityGrid.updateLocation(this, null);
                        }
                        else
                        {
                                state.entityGrid.updateLocation(this, pos.pos);
                        }
                }
        }
        
        public PhysicsPointHistoryDetailed getAndSeekHistoryDetail(long tick, boolean lookAtOtherStates)
        {
                MapEntity en = getEntityAt(tick, false, lookAtOtherStates);

                if (en == null)
                {
                        return null; // deleted or too far in the past
                }
                
                en.posHistory.seekDetail(tick);
                return en.posHistory;
        }
        
        public void resetTo(MapEntity other)
        {
                if (!this.state.isForeign(other))
                {
                        assert other.crossStateList == this.crossStateList;

                        if (other.crossStateList[this.state.id] != null)
                        {
                                assert other.crossStateList[this.state.id] == this;
                        }

                        if (this.crossStateList[other.state.id] != null)
                        {
                                assert this.crossStateList[other.state.id] == other;
                        }
                }
                
                //this.debug_id_with_reset = other.debug_id_with_reset;
                createdAt_tick = other.createdAt_tick;
                this.removed = other.removed;
                removedAt_tick = other.removedAt_tick;
                
                pos.set(other.pos);
                posHistory.set(other.posHistory);
                velHistory.set(other.velHistory);
                
                if (state.isForeign(other) && posHistory.HISTORY_LENGTH != other.posHistory.HISTORY_LENGTH)
                {
                        // history length might be different between foreign states.
                        // projectile history within a single state is usually not complete.
                        // (unlike actors, which store full (overlaping) histories up to in each state)
                        // the posHistory.set above make sure the tick range is correct.
                        // Using overwite the missing values will be filled in properly.
                        
                        for (int s = other.state.env.econfig.TRAILING_STATES - 1;
                             s >= other.state.id;
                             --s)
                        {
                                if (other.crossStateList[s] != null)
                                {
                                        posHistory.overwrite(other.crossStateList[s].posHistory);
                                }
                        }
                }
                
                markDirtyPositionPath(other.dirtyPositionPathTracker.getFirstDirtyTick());
        }

        @Override
        public LinkedListEntry getEntityGridEntry(EntityGrid grid)
        {
                return this.entityGridEntry;
        }
}
