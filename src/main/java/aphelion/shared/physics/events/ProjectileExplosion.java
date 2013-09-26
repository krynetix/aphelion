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

package aphelion.shared.physics.events;


import aphelion.shared.gameconfig.GCStringList;
import aphelion.shared.physics.entities.Actor;
import aphelion.shared.physics.entities.Actor.WeaponConfig;
import aphelion.shared.physics.entities.MapEntity;
import aphelion.shared.physics.entities.Projectile;
import aphelion.shared.physics.entities.ProjectilePublic;
import aphelion.shared.physics.events.pub.ProjectileExplosionPublic;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.State;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.physics.valueobjects.PhysicsShipPosition;
import aphelion.shared.swissarmyknife.LinkedListEntry;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public class ProjectileExplosion extends Event implements ProjectileExplosionPublic
{
        private static final Logger log = Logger.getLogger("Aphelion.Shared.Physics");
        private History[] history = new History[PhysicsEnvironment.MAX_TRAILING_STATES];
        
        // this list is only kept her temporary.
        // it will be set as an attribute in every projectile.
        private ArrayList<MapEntity[]> chained_crossStateLists; // projectile index -> state id -> map entity
        
        public ProjectileExplosion()
        {
                for (int a = 0; a < PhysicsEnvironment.MAX_TRAILING_STATES; ++a)
                {
                        history[a] = new History();
                }
        }
        
        public void execute(long tick, State state, Projectile explodedProjectile, EXPLODE_REASON reason, Actor actorHit, PhysicsPoint tileHit)
        {
                assert state == explodedProjectile.state;
                
                boolean doSplash = false;
                GCStringList chainWeapon;
                
                switch(reason)
                {
                        case EXPIRATION:
                                assert actorHit == null;
                                assert tileHit == null;

                                if (explodedProjectile.cfg(explodedProjectile.config.projectile_expirationExplode, tick))
                                {
                                        doSplash = true;
                                }
                                
                                chainWeapon = explodedProjectile.config.projectile_expirationChainWeapon;
                                
                                break;
                                
                        case PROX_DELAY:
                                assert actorHit == null;
                                assert tileHit == null;
                                doSplash = true;
                                chainWeapon = explodedProjectile.config.projectile_proxChainWeapon;
                                break;
                                
                        case PROX_DIST:
                                assert actorHit == null;
                                assert tileHit == null;
                                doSplash = true;
                                chainWeapon = explodedProjectile.config.projectile_proxChainWeapon;
                                break;
                                
                        case HIT_TILE:
                                assert actorHit == null;
                                assert tileHit != null;
                                assert tileHit.set;
                                doSplash = true;
                                chainWeapon = explodedProjectile.config.projectile_hitTileChainWeapon;
                                break;
                                
                        case HIT_SHIP:
                                assert actorHit != null;
                                assert tileHit == null;
                                doSplash = true;
                                chainWeapon = explodedProjectile.config.projectile_hitShipChainWeapon;
                                break;
                                
                        default:
                                assert false;
                                return;
                }
                
                
                if (SwissArmyKnife.assertEnabled)
                {
                        for (History hist : history)
                        {
                                if (hist.projectile == null)
                                {
                                        continue;
                                }
                                
                                assert hist.projectile.crossStateList == explodedProjectile.crossStateList;
                                assert hist.projectile.crossStateList[state.id] == explodedProjectile;
                        }
                }
                
                History hist = history[state.id];
                hist.set = true;
                hist.reason = reason;
                
                explodedProjectile.softRemove(tick);
                
                if (reason == EXPLODE_REASON.HIT_TILE || reason == EXPLODE_REASON.HIT_SHIP)
                {
                        // it hit an actor or a tile
                        // remove our coupled projectiles
                        
                        for(Projectile coupledProjectile : explodedProjectile.coupled)
                        {
                                if (coupledProjectile.removed)
                                {
                                        continue;
                                }
                                
                                if (reason == EXPLODE_REASON.HIT_SHIP)
                                {
                                        if (!explodedProjectile.cfg(explodedProjectile.config.projectile_hitShipCoupled, tick))
                                        {
                                                continue;
                                        }
                                }
                                else if (reason == EXPLODE_REASON.HIT_TILE)
                                {
                                        if (!explodedProjectile.cfg(explodedProjectile.config.projectile_hitTileCoupled, tick))
                                        {
                                                continue;
                                        }
                                }
                                else
                                {
                                        assert false;
                                }
                                
                                coupledProjectile.softRemove(tick);

                                hist.coupledProjectiles.add(coupledProjectile);
                        }
                }
                
                
                hist.projectile = explodedProjectile;
                hist.hit_x = explodedProjectile.pos.pos.x;
                hist.hit_y = explodedProjectile.pos.pos.y;
                hist.hit_vel_x = explodedProjectile.pos.vel.x;
                hist.hit_vel_y = explodedProjectile.pos.vel.y;
                hist.hit_tile = tileHit != null;
                hist.hit_tile_x = tileHit == null ? 0 : tileHit.x;
                hist.hit_tile_x = tileHit == null ? 0 : tileHit.y;
                hist.tick = tick;
                hist.hit_pid = actorHit == null ? 0 : actorHit.pid;
                hist.fire_pid = explodedProjectile.owner == null ? 0 : explodedProjectile.owner.pid;
                
                WeaponConfig explodedConfig = explodedProjectile.config;
                
                int damage = explodedProjectile.cfg(explodedConfig.projectile_damage, tick) * 1024;
                
                if (reason == EXPLODE_REASON.HIT_SHIP)
                {
                        actorHit.energy.addRelativeValue(Actor.ENERGY_SETTER.OTHER.id, tick, -damage);

                        if (actorHit.energy.get(tick) <= 0)
                        {
                                hist.killed_pids.add(actorHit.pid);
                                actorHit.dead = true;
                                actorHit.spawnAt_tick = tick + actorHit.respawnDelay.get();
                        }

                        actorHit.applyEmp(tick, explodedProjectile.cfg(explodedConfig.projectile_empTime, tick));
                }
                
                if (doSplash)
                {
                        // apply splash damage on all actors except the one we
                        // hit directly (this is different from continum)
                        explodedProjectile.doSplashDamage(actorHit, tick, hist.killed_pids);
                        explodedProjectile.doSplashEmp(actorHit, tick);
                }
                
                
                // Fire a chained weapon
                String weapon = chainWeapon.isSet() ? explodedProjectile.cfg(chainWeapon, tick) : "";
                if (!weapon.isEmpty())
                {
                        // todo projectile.owner == null
                        Actor.WeaponConfig chainConfig = explodedProjectile.owner.getWeaponConfig(weapon);
                        
                        int projectile_count = SwissArmyKnife.clip(chainConfig.projectiles.get(), 1, 1024);
                        
                        PhysicsShipPosition actorPos = new PhysicsShipPosition();
                        actorPos.setPositionVectory(explodedProjectile.pos);
                        actorPos.rot = 0; // todo PhysicsTrig.atan2() based on velocity vector
                        actorPos.rot_snapped = 0;
                        
                        if (chained_crossStateLists == null)
                        {
                                chained_crossStateLists = new ArrayList(projectile_count);
                        }
                        else
                        {
                                chained_crossStateLists.ensureCapacity(projectile_count);
                        }
                        
                        LinkedListEntry<Projectile> coupled_last = null;
                        for (int p = 0; p < projectile_count; ++p)
                        {
                                MapEntity[] crossStateList;
                                if (p < chained_crossStateLists.size())
                                {
                                        crossStateList = chained_crossStateLists.get(p);
                                }
                                else
                                {
                                        crossStateList = new MapEntity[PhysicsEnvironment.MAX_TRAILING_STATES];
                                        chained_crossStateLists.add(crossStateList);
                                        assert p == chained_crossStateLists.size() - 1;
                                }
                                
                                Projectile chainedProjectile = new Projectile(
                                        state, 
                                        crossStateList, 
                                        explodedProjectile.owner, 
                                        tick,
                                        chainConfig,
                                        p);
                                
                                crossStateList[state.id] = (MapEntity) chainedProjectile;
                                
                                if (coupled_last == null)
                                {
                                        chainedProjectile.coupled.beginCircular();
                                }
                                else
                                {
                                        coupled_last.append(chainedProjectile.coupled);
                                }
                                coupled_last = chainedProjectile.coupled;
                                
                                chainedProjectile.initFire(tick, actorPos);
                                
                                state.projectiles.append(chainedProjectile.projectileListLink_state);
                                chainedProjectile.owner.projectiles.append(chainedProjectile.projectileListLink_actor);


                                chainedProjectile.updatePositionHistory(tick);

                                // dead reckon current position so that it is no longer late
                                // the position at the tick of this operation should not be dead reckoned, therefor +1
                                chainedProjectile.performDeadReckoning(state.env.getMap(), tick + 1, state.tick_now - tick);
                        }
                }
                
        }

        @Override
        public boolean isConsistent(State older, State newer)
        {
                History histOlder = history[older.id];
                History histNewer = history[newer.id];
                
                if (!histNewer.set)
                {
                        return !histOlder.set;
                }
                
                if (!histOlder.set)
                {
                        if (histNewer.tick > older.tick_now)
                        {
                                return true; // this event has not had the chance to execute on the older state yet.
                        }
                }
                
                return histNewer.isConsistent(histOlder);
        }
        
        @Override
        public void resetExecutionHistory(State state, State resetTo)
        {
                History histFrom = history[resetTo.id];
                History histTo = history[state.id];
                
                histTo.set(histFrom, state, resetTo);
        }

        @Override
        public boolean isOld(long removeOlderThan_tick)
        {
                long highestTick = 0;
                
                for (int a = 0; a < PhysicsEnvironment.MAX_TRAILING_STATES; ++a)
                {
                        if (history[a].set && history[a].tick > highestTick)
                        {
                                highestTick = history[a].tick;
                        }
                }
                
                return highestTick < removeOlderThan_tick;
        }

        @Override
        public boolean hasOccured(int stateid)
        {
                 History hist = history[stateid];
                 return hist.set;
        }
        
        @Override
        public EXPLODE_REASON getReason(int stateid)
        {
                History hist = history[stateid];
                return hist.reason;
        }

        @Override
        public long getTick(int stateid)
        {
                History hist = history[stateid];
                return hist.tick;
        }

        @Override
        public void getPosition(int stateid, PhysicsPoint pos)
        {
                History hist = history[stateid];
                if (hist.set)
                {
                        pos.set(hist.hit_x, hist.hit_y);
                }
                else
                {
                        pos.unset();
                }
        }

        @Override
        public void getVelocity(int stateid, PhysicsPoint vel)
        {
                History hist = history[stateid];
                if (hist.set)
                {
                        vel.set(hist.hit_vel_x, hist.hit_vel_y);
                }
                else
                {
                        vel.unset();
                }
        }

        @Override
        public int getHitActor(int stateid)
        {
                History hist = history[stateid];
                return hist.hit_pid;
        }

        @Override
        public int getFireActor(int stateid)
        {
                History hist = history[stateid];
                return hist.fire_pid;
        }

        @Override
        public ProjectilePublic getProjectile(int stateid)
        {
                History hist = history[stateid];
                return hist.projectile;
        }

        @Override
        public Iterable<Integer> getKilled(int stateid)
        {
                History hist = history[stateid];
                return hist.killed_pids;
        }

        @Override
        public void getHitTile(int stateid, PhysicsPoint tile)
        {
                History hist = history[stateid];
                if (hist.hit_tile)
                {
                        tile.set(hist.hit_tile_x, hist.hit_tile_y);
                }
                else
                {
                        tile.unset();
                }
        }

        @Override
        public Iterable<ProjectilePublic> getCoupledProjectiles(int stateid)
        {
                History hist = history[stateid];
                return (List<ProjectilePublic>) (Object) hist.coupledProjectiles;
        }

        

        private static class History
        {
                boolean set = false;
                EXPLODE_REASON reason;
                int hit_x;
                int hit_y;
                int hit_pid; // (direct hit)
                boolean hit_tile;
                int hit_tile_x;
                int hit_tile_y;
                int fire_pid;
                long tick;
                final List<Integer> killed_pids = new ArrayList<>(4);
                
                // not part of consistency check:
                int hit_vel_x;
                int hit_vel_y;
                Projectile projectile;
                
                // coupled projectiles that were removed uring this event
                final List<Projectile> coupledProjectiles = new ArrayList<>(4);
                
                public boolean isConsistent(History other)
                {
                        
                        if (this.set != other.set)
                        {
                                return false;
                        }
                        
                        
                        if (this.reason != other.reason)
                        {
                                return false;
                        }
                        
                        if (this.hit_x != other.hit_x)
                        {
                                return false;
                        }
                        if (this.hit_y != other.hit_y)
                        {
                                return false;
                        }
                        
                        if (this.hit_tile != other.hit_tile)
                        {
                                return false;
                        }
                        if (this.hit_tile_x != other.hit_tile_x)
                        {
                                return false;
                        }
                        if (this.hit_tile_x != other.hit_tile_x)
                        {
                                return false;
                        }
                        
                        if (this.hit_pid != other.hit_pid)
                        {
                                return false;
                        }
                        if (this.fire_pid != other.fire_pid)
                        {
                                return false;
                        }
                        if (this.tick != other.tick)
                        {
                                return false;
                        }
                        
                        if (!this.killed_pids.equals(other.killed_pids))
                        {
                                return false;
                        }
                        return true;
                }

                
                public void set(History other, State myState, State otherState)
                {
                        set = other.set;
                        reason = other.reason;
                        hit_x = other.hit_x;
                        hit_y = other.hit_y;
                        hit_vel_x = other.hit_vel_x;
                        hit_vel_y = other.hit_vel_y;
                        hit_tile = other.hit_tile;
                        hit_tile_x = other.hit_tile_x;
                        hit_tile_y = other.hit_tile_y;
                        hit_pid = other.hit_pid;
                        fire_pid = other.fire_pid;
                        tick = other.tick;
                        
                        killed_pids.clear();
                        killed_pids.addAll(other.killed_pids);
                        
                        if (other.projectile == null)
                        {
                                projectile = null;
                        }
                        else
                        {
                                projectile = (Projectile) other.projectile.crossStateList[myState.id];
                        }
                }
        }
}
