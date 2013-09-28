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

package aphelion.client;


import aphelion.client.graphics.Graph;
import aphelion.client.net.NetworkedGame;
import aphelion.client.net.SingleGameConnection;
import aphelion.shared.resource.ResourceDB;
import aphelion.client.graphics.screen.Camera;
import aphelion.client.graphics.screen.EnergyBar;
import aphelion.client.graphics.screen.Gauges;
import aphelion.client.graphics.screen.StatusDisplay;
import aphelion.client.graphics.world.ActorShip;
import aphelion.client.graphics.world.GCImageAnimation;
import aphelion.client.graphics.world.MapEntities;
import aphelion.client.graphics.world.Projectile;
import aphelion.client.graphics.world.StarField;
import aphelion.client.resource.AsyncTexture;
import aphelion.shared.event.TickEvent;
import aphelion.shared.event.TickedEventLoop;
import aphelion.shared.event.WorkerTask;
import aphelion.shared.event.WorkerTaskCallback;
import aphelion.shared.gameconfig.GCImage;
import aphelion.shared.gameconfig.GCStringList;
import aphelion.shared.gameconfig.LoadYamlTask;
import aphelion.shared.physics.entities.ActorPublic;
import aphelion.shared.physics.entities.ProjectilePublic;
import aphelion.shared.physics.events.pub.EventPublic;
import aphelion.shared.physics.events.pub.ProjectileExplosionPublic;
import aphelion.shared.physics.events.pub.ProjectileExplosionPublic.EXPLODE_REASON;
import static aphelion.shared.physics.events.pub.ProjectileExplosionPublic.EXPLODE_REASON.EXPIRATION;
import static aphelion.shared.physics.events.pub.ProjectileExplosionPublic.EXPLODE_REASON.HIT_SHIP;
import static aphelion.shared.physics.events.pub.ProjectileExplosionPublic.EXPLODE_REASON.HIT_TILE;
import static aphelion.shared.physics.events.pub.ProjectileExplosionPublic.EXPLODE_REASON.PROX_DELAY;
import static aphelion.shared.physics.events.pub.ProjectileExplosionPublic.EXPLODE_REASON.PROX_DIST;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.valueobjects.PhysicsMovement;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.physics.valueobjects.PhysicsShipPosition;
import aphelion.shared.physics.WEAPON_SLOT;
import aphelion.shared.swissarmyknife.Point;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import aphelion.shared.map.MapClassic;
import aphelion.shared.map.tile.TileType;

import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.newdawn.slick.Color;
import org.newdawn.slick.Image;

/**
 *
 * @author Joris
 */
public class GameLoop
{
        private static final Logger log = Logger.getLogger(GameLoop.class.getName());
        
        private ResourceDB resourceDB;
        private TickedEventLoop loop;
        private boolean loadedResources = false;
        private boolean connectionError = false;
        
        // Network:
        private SingleGameConnection connection;
        private NetworkedGame networkedGame;
        
        // Input:
        private MyKeyboard myKeyboard;
        
        // Physics:
        private PhysicsEnvironment physicsEnv;
        private ActorPublic localActor;
        private List<LoadYamlTask.Return> yamlGameConfigTemp;
        private GCStringList ships;
        
        // Map:
        private MapClassic mapClassic;
        
        // Graphics:        
        private StarField stars;
        private Camera mainCamera;
        private Camera radarCamera;
        private Camera bigMapCamera;
        private MapEntities mapEntities;
        
        // Screen Graphics
        private EnergyBar energyBar;
        private StatusDisplay statusDisplay;
        private Gauges gauges;
       
        
        // Graphics statistics
        private long frames;
        private long lastFrameReset;
        private long lastFps;

        public GameLoop(ResourceDB resourceDB, TickedEventLoop loop, SingleGameConnection connection, NetworkedGame networkedGame)
        {
                this.resourceDB = resourceDB;
                this.loop = loop;
                this.connection = connection;
                this.networkedGame = networkedGame;
        }
        
        public boolean isLoadingResources()
        {
                return !loadedResources;
        }
        
        public boolean isConnectionError()
        {
                return connectionError;
        }
        
        public void tryLoaded()
        {
                if (mapClassic != null && yamlGameConfigTemp != null && !loadedResources)
                {
                        loaded();
                }
        }
        
        public void loaded()
        {
        	
                physicsEnv = new PhysicsEnvironment(false, mapClassic);
                mapEntities.setPhysicsEnv(physicsEnv);

                loop.prependTickEvent(physicsEnv); // must come before keyboard input, which generates new events
                loop.addTickEvent(myKeyboard);
                
                for (LoadYamlTask.Return ret : yamlGameConfigTemp)
                {
                        physicsEnv.loadConfig(
                                physicsEnv.getTick() - physicsEnv.MAX_OPERATION_AGE, 
                                ret.fileIdentifier, 
                                ret.yamlDocuments);
                }
                yamlGameConfigTemp = null;
                
                this.ships = physicsEnv.getGlobalConfigStringList(0, "ships");
                
                networkedGame.registerArenaResources(physicsEnv, mapEntities);
                loadedResources = true;
        }
        
        public void loop()
        {
                loop.addWorkerTask(
                        new MapClassic.LoadMapTask(resourceDB, true), 
                        "level.map", 
                        new WorkerTaskCallback<MapClassic>()
                {
                        @Override
                        public void taskCompleted(WorkerTask.WorkerException error, MapClassic ret)
                        {
                                if (error != null)
                                {
                                        log.log(Level.SEVERE, "Error while reading map", error);
                                        loop.interrupt();
                                        // TODO
                                        return;
                                }
                                
                                log.log(Level.INFO, "Map loaded");
                                mapClassic = ret;
                                // should work fine for lvl files < 2 GiB
                                stars = new StarField((int) mapClassic.getLevelSize(), resourceDB);
                                
                                tryLoaded();
                        }
                });
                
                loop.addWorkerTask(
                        new LoadYamlTask(resourceDB), 
                        resourceDB.getKeysByPrefix("gameconfig."), 
                        new WorkerTaskCallback<List<LoadYamlTask.Return>>()
                {
                        @Override
                        public void taskCompleted(WorkerTask.WorkerException error, List<LoadYamlTask.Return> ret)
                        {
                                if (error != null)
                                {
                                        log.log(Level.SEVERE, "Error while reading game config", error);
                                        loop.interrupt();
                                        // TODO
                                        return;
                                }
                                
                                log.log(Level.INFO, "Game config read");
                                yamlGameConfigTemp = ret;
                                tryLoaded();
                        }
                });
                
                AsyncTexture loadingTex = resourceDB.getTextureLoader().getTexture("gui.loading.graphics");
                
                myKeyboard = new MyKeyboard();
                
                mainCamera = new Camera(resourceDB);
                
                radarCamera = new Camera(resourceDB);
                radarCamera.setZoom(1/12f);
                radarCamera.setPosition(512 * 16, 512 * 16);
                
                bigMapCamera = new Camera(resourceDB);
                bigMapCamera.setPosition(512 * 16, 512 * 16);
                
                mapEntities = new MapEntities(resourceDB);
                loop.addTickEvent(mapEntities);
                loop.addLoopEvent(mapEntities);
                
                boolean first = true;
                lastFrameReset = System.nanoTime();
                frames = 60;
                
                while (!loop.isInterruped())
                {
                        long begin = System.nanoTime();
                        
                        Display.update();
                        if (Display.isCloseRequested())
                        {
                                log.log(Level.WARNING, "Close requested in game loop");
                                loop.interrupt();
                                break;
                        }
                        
                        
                        if (networkedGame.isReady() && localActor == null)
                        {
                                localActor = physicsEnv.getActor(networkedGame.getMyPid());
                        }
                        
                        Client.initGL();
                        
                        int displayWidth = Display.getWidth();
                        int displayHeight = Display.getHeight();
                        
                        if (first || Display.wasResized())
                        {
                                mainCamera.setDimension(displayWidth, displayHeight);
                                
                                radarCamera.setDimension((displayHeight / 3.5f), (displayHeight / 3.5f));
                                radarCamera.setScreenPosition(
                                        displayWidth - radarCamera.dimension.x - 8, 
                                        displayHeight - radarCamera.dimension.y - 8);
                                
                                bigMapCamera.setDimension((displayHeight * 0.6f), (displayHeight * 0.6f));
                                //bigMapCamera.setZoom(1/16f);
                                bigMapCamera.setZoom(bigMapCamera.dimension.y / 1024f / 16f); 
                                bigMapCamera.setScreenPosition(
                                        displayWidth - bigMapCamera.dimension.x - 8, 
                                        displayHeight - bigMapCamera.dimension.y - 8);
                        }
                        
                        Keyboard.poll();
                        myKeyboard.pollStates();
                        
                        loop.loop(); // logic
                        
                        if (networkedGame.isDisconnected())
                        {
                                connectionError = true;
                                return;
                        }
                        
                        
                        
                        ActorShip localShip = mapEntities.getLocalShip();
                        
                        
                        if (!networkedGame.isReady())
                        {
                                Image loadingBanner = loadingTex.getCachedImage();
                                if (loadingBanner != null)
                                {
                                        loadingBanner.drawCentered(Display.getWidth() / 2, Display.getHeight() / 2);
                                }
                        }
                        else
                        {
                                loadingTex = null;
                                
                                updateEntities();
                                
                                if (localShip == null || localActor == null)
                                {
                                        // TODO spec
                                        mainCamera.setPosition(8192, 8192);
                                        energyBar = null;
                                        statusDisplay = null;
                                        gauges = null;
                                }
                                else
                                {
                                        if (energyBar == null)
                                        {
                                                energyBar = new EnergyBar(resourceDB, localShip);
                                        }
                                        
                                        if(statusDisplay == null)
                                        {
                                        	statusDisplay = new StatusDisplay(resourceDB, localShip);
                                        }
                                        
                                        if(gauges == null)
                                        {
                                        	gauges = new Gauges(resourceDB, localActor);
                                        }
                                        
                                        
                                        mainCamera.setPosition(localShip.pos);
                                }
                                
                                render();
                        }
                        
                        // statistics
                        long now = System.nanoTime();
                        ++frames;
                        long frameTimeDelta = (now - begin) / 1_000_000;
                        
                        Graph.g.setColor(Color.yellow);
                        if (physicsEnv != null)
                        {
                                Graph.g.setFont(Fonts.monospace_bold_16);
                                Graph.g.drawString(String.format("%d (%2dms) %4d %d %3dms",
                                        lastFps, 
                                        frameTimeDelta, 
                                        physicsEnv.localTickToServer(physicsEnv.getTick()),
                                        physicsEnv.getTimewarpCount(),
                                        networkedGame.getlastRTTNano() / 1000_000L), 0, 0);
                                
                                if (localShip != null)
                                {
                                        String s = "("+((int) localShip.pos.x / 16)+","+((int) localShip.pos.y / 16)+")";
                                        
                                        if (localShip.getActor() != null)
                                        {
                                                s += " " + localShip.getActor().getShip();
                                        }
                                        
                                        Graph.g.drawString(s, 0, 20);
                                }
                        }
                        
                        Display.sync(60);
                        first = false;
                        
                        if (now - lastFrameReset > 1000000000L)
                        {
                                lastFps = frames;
                                frames = 0;
                                lastFrameReset = now;
                        }
                }       
        }
        
        private void render()
        {
                renderCamera(mainCamera);
                
                // GUI
                if (energyBar != null)
                {
                        energyBar.render(mainCamera);
                }
                if(statusDisplay != null)
                {
                	statusDisplay.render(mainCamera);
                }
                if(gauges != null)
                {
                	gauges.render(mainCamera, myKeyboard.multiFireGun);
                }
                
                if (myKeyboard.altMap)
                {
                        bigMapCamera.renderCameraBox();
                        bigMapCamera.renderTiles(this.mapClassic, TileType.TILE_LAYER.PLAIN);
                        renderCamera(bigMapCamera);
                }
                else
                {
                        radarCamera.setPosition(mainCamera.pos);
                        radarCamera.clipPosition(0, 0, 1024*16, 1024*16);
                        renderCamera(radarCamera);
                        
                        Graph.g.setColor(Color.white);
                        String text = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + ":" + Calendar.getInstance().get(Calendar.MINUTE);
                        
                        Graph.g.drawString(text, 
                                Display.getWidth() - Graph.g.getFont().getWidth(text) - 5, 
                                radarCamera.screenPosY - Graph.g.getFont().getLineHeight());
                }
        }
        
        @SuppressWarnings("unchecked")
        private void renderCamera(Camera camera)
        {
                GL11.glColor3f(1, 1, 1);
                
                if (camera != this.mainCamera)
                {
                        // draw camera edges and background unless this is the main camera
                        camera.renderCameraBox();
                }
                
                if (camera == mainCamera)
                {
                        camera.setGraphicsClip();
                        stars.render(camera);
                }
                
                camera.renderEntities(mapEntities.animations(RENDER_LAYER.BACKGROUND, camera));
                camera.renderTiles(this.mapClassic, TileType.TILE_LAYER.PLAIN);
                // rendered in a seperate iteration so that we do not have to switch between textures as often
                // (tile set is one big texture)
                camera.renderTiles(this.mapClassic, TileType. TILE_LAYER.ANIMATED);
                camera.renderEntities(mapEntities.animations(RENDER_LAYER.AFTER_TILES, camera));
                camera.renderEntities(mapEntities.projectiles(false));
                camera.renderEntities(mapEntities.animations(RENDER_LAYER.AFTER_PROJECTILES, camera));
                camera.renderEntities(mapEntities.shipsNoLocal());
                camera.renderEntities(mapEntities.animations(RENDER_LAYER.AFTER_SHIPS, camera));
                camera.renderEntity(mapEntities.getLocalShip());
                camera.renderTiles(this.mapClassic, TileType.TILE_LAYER.PLAIN_OVER_SHIP);
                camera.renderEntities(mapEntities.animations(RENDER_LAYER.AFTER_LOCAL_SHIP, camera));
        }
        
        private void updateEntities()
        {
                PhysicsShipPosition actorPos = new PhysicsShipPosition();
                
                Point localActorPos = new Point();
                ActorShip localShip = mapEntities.getLocalShip();
                
                if (localShip != null && localShip.getActor() != null && localShip.getActor().getPosition(actorPos))
                {
                        localActorPos.set(actorPos.x, actorPos.y);
                }
                
                Iterator<ActorShip> shipIt = mapEntities.shipIterator();
                while (shipIt.hasNext())
                {
                        ActorShip actorShip = shipIt.next();
                        
                        ActorPublic physicsActor = actorShip.getActor();
                        
                        long spawnedAgo = physicsEnv.getTick() - physicsActor.getSpawnedAt();
                        
                        int renderDelay = SwissArmyKnife.clip(
                                actorShip.renderDelay.get(), 
                                0, 
                                physicsEnv.TRAILING_STATES * PhysicsEnvironment.TRAILING_STATE_DELAY - 1);
                        
                        if (renderDelay > spawnedAgo)
                        {
                                renderDelay = (int) spawnedAgo;
                                if (renderDelay < 0)
                                {
                                        renderDelay = 0;
                                }
                        }
                        
                        actorShip.renderingAt_ticks = physicsEnv.getTick() - renderDelay;
                        
                        actorShip.exists = physicsActor.getHistoricPosition(actorPos, physicsEnv.getTick() - renderDelay, true);
                        actorShip.exists = actorShip.exists && !physicsActor.isDead();
                        
                        if (actorShip.exists)
                        {
                                actorShip.setPositionFromPhysics(actorPos.x, actorPos.y);
                                actorShip.setRotationFromPhysics(actorPos.rot_snapped);
                                actorShip.setNameFromPhysics(physicsActor.getName());
                        }

                        if (physicsActor.getPosition(actorPos))
                        {
                                actorShip.setShadowPositionFromPhysics(actorPos.x, actorPos.y);
                        }

                        if (actorShip != localShip)
                        {
                                actorShip.updateDistanceToLocal(localActorPos);
                        }
                }
                
                
                
                ProjectilePublic.Position projectilePos = new ProjectilePublic.Position();
                PhysicsPoint historicProjectilePos = new PhysicsPoint();
                Point diff = new Point();
                
                Iterator<Projectile> projectileIt = mapEntities.projectileIterator(false);
                while (projectileIt.hasNext())
                {
                        Projectile projectile = projectileIt.next();
                        ProjectilePublic physicsProjectile = projectile.getPhysicsProjectile();
                        
                        if (physicsProjectile.getPosition(projectilePos))
                        {
                                projectile.setShadowPositionFromPhysics(projectilePos.x, projectilePos.y);
                        }
                        
                        // the closest ship excluding the local one
                        // all actors should have been updated at this point
                        ActorShip closest = mapEntities.findNearestActor(projectile.pos, false);
                        if (closest == null || localShip == null)
                        {
                                projectile.renderDelay.set(0);
                        }
                        else
                        {
                                /* p = local player
                                 * r = remote player
                                 * e = entity (projectile)
                                 * r' = the shadow of the player r (the position that is 
                                 *      dead reckoned up the current time)
                                 * 
                                 * δ(x, y) is the distance between x en y
                                 * d(x, y) is the render delay of y on the screen of x
                                 * d(p, e) = 0       if δ(p , e') = 0
                                 * d(p, e) = d(p,r)  if δ(r', e') = 0
                                 * 
                                 * d(p, e) = d(p, r) * max(0, 1 - δ(r', e') / δ(p, r) )
                                 * sqrt(a) / sqrt(b) = sqrt(a / b)
                                 */
                                
                                diff.set(closest.shadowPosition);
                                diff.sub(projectile.shadowPosition);
                                float distSq_rShadow_e = diff.distanceSquared();
                                
                                diff.set(localShip.pos);
                                diff.sub(closest.pos);
                                float distSq_p_r = diff.distanceSquared();
                                
                                double renderDelay = 
                                        closest.renderDelay.get() * 
                                        Math.max(0, 1 - Math.sqrt(distSq_rShadow_e / distSq_p_r));
                                
                                if (Double.isNaN(renderDelay))
                                {
                                        renderDelay = 0;
                                }
                                
                                renderDelay = Math.round(renderDelay);
                                
                                projectile.renderDelay.set((int) renderDelay);
                                
                                // Alternative implementation: smooth "d(p, r)" whenever r changes
                        }
                        
                        // get the actual current smoothed render delay
                        int renderDelay = SwissArmyKnife.clip(
                                projectile.renderDelay.get(), 
                                0, 
                                physicsEnv.TRAILING_STATES * PhysicsEnvironment.TRAILING_STATE_DELAY - 1);
                        
                        projectile.renderingAt_ticks = physicsEnv.getTick() - renderDelay;
                        
                        projectile.exists = physicsProjectile.getHistoricPosition(
                                historicProjectilePos, 
                                projectile.renderingAt_ticks, 
                                true);
                        
                        if (projectile.exists)
                        {
                                projectile.setPositionFromPhysics(historicProjectilePos.x, historicProjectilePos.y);
                        }
                }
                
                PhysicsPoint pos = new PhysicsPoint();
                while (true)
                {
                        EventPublic event_ = physicsEnv.nextEvent();
                        if (event_ == null) { break; }
                        
                        if (event_ instanceof ProjectileExplosionPublic)
                        {
                                ProjectileExplosionPublic event = (ProjectileExplosionPublic) event_;
                                ProjectilePublic projectile = event.getProjectile(0);
                                if (projectile == null) { continue; }
                                
                                for (Integer pid : event.getKilled(0))
                                {
                                        ActorPublic actor = physicsEnv.getActor(pid);
                                        if (actor == null) continue;

                                        GCImage image = actor.getActorConfigImage("ship-explosion-animation", resourceDB);
                                        
                                        if (image != null && actor.getPosition(actorPos))
                                        {
                                                GCImageAnimation anim = new GCImageAnimation(resourceDB, image);
                                                anim.setPositionFromPhysics(actorPos.x, actorPos.y);
                                                anim.setVelocityFromPhysics(actorPos.x_vel, actorPos.y_vel);
                                                mapEntities.addAnimation(RENDER_LAYER.AFTER_LOCAL_SHIP, anim, null);
                                        }
                                }
                                
                                PhysicsPoint tileHit = new PhysicsPoint();
                                event.getHitTile(0, tileHit);

                                GCImage hitImage;
                                EXPLODE_REASON reason = event.getReason(0);
                                switch (reason)
                                {
                                        case EXPIRATION:
                                                hitImage = projectile.getWeaponConfigImage(
                                                        "projectile-expiration-animation", 
                                                        resourceDB);
                                                break;

                                        case PROX_DELAY:
                                                hitImage = projectile.getWeaponConfigImage(
                                                        "projectile-prox-animation", 
                                                        resourceDB);
                                                break;

                                        case PROX_DIST:
                                                hitImage = projectile.getWeaponConfigImage(
                                                        "projectile-prox-animation", 
                                                        resourceDB);
                                                break;

                                        case HIT_TILE:
                                                hitImage = projectile.getWeaponConfigImage(
                                                        "projectile-hit-tile-animation", 
                                                        resourceDB);
                                                break;

                                        case HIT_SHIP:
                                                hitImage = projectile.getWeaponConfigImage(
                                                        "projectile-hit-ship-animation", 
                                                        resourceDB);
                                                break;

                                        default:
                                                assert false;
                                                return;
                                }
                                
                                
                                if (hitImage != null)
                                {
                                        event.getPosition(0, pos);
                                        if (pos.set)
                                        {
                                                GCImageAnimation anim = new GCImageAnimation(resourceDB, hitImage);

                                                anim.setPositionFromPhysics(pos);
                                                mapEntities.addAnimation(RENDER_LAYER.AFTER_LOCAL_SHIP, anim, null);
                                        }

                                        for (ProjectilePublic coupledProjectile : event.getCoupledProjectiles(0))
                                        {
                                                coupledProjectile.getHistoricPosition(pos, event.getTick(0), false);

                                                GCImageAnimation anim = new GCImageAnimation(resourceDB, hitImage);

                                                anim.setPositionFromPhysics(pos);
                                                mapEntities.addAnimation(RENDER_LAYER.AFTER_LOCAL_SHIP, anim, null);
                                        }
                                }
                        }
                }
        }
        
        private class MyKeyboard implements TickEvent
        {
                private long tick;
                private boolean up, down, left, right, boost;
                private boolean altMap;
                private boolean multiFireGun;
                private boolean fireGun;
                private boolean fireBomb;
                private boolean fireMine;
                private boolean fireThor;
                private boolean fireBurst;
                private boolean fireRepel;
                private boolean fireDecoy;
                private boolean fireRocket;
                private boolean fireBrick;
                
                private long lastShipChangeRequest;
                
                public void pollStates()
                {
                        // Keyboard.poll(); should have just been called.
                        
                        if (!Display.isActive())
                        {
                                up = false;
                                down = false;
                                left = false;
                                right = false;
                                boost = false;
                                altMap = false;
                                fireGun = false;
                                fireBomb = false;
                                fireThor = false;
                                fireBurst = false;
                                fireRepel = false;
                                fireDecoy = false;
                                fireRocket = false;
                                fireBrick = false;
                                return;
                        }
                        
                        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) 
                                || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                        
                        boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) 
                                || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
                        
                        up    = Keyboard.isKeyDown(Keyboard.KEY_UP);
                        down  = Keyboard.isKeyDown(Keyboard.KEY_DOWN);
                        left  = Keyboard.isKeyDown(Keyboard.KEY_LEFT);
                        right = Keyboard.isKeyDown(Keyboard.KEY_RIGHT);
                        boost = shift;
                        
                        altMap = Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
                        
                        fireGun = !shift && ctrl;
                        fireBomb = !shift && Keyboard.isKeyDown(Keyboard.KEY_TAB);
                        fireMine = shift && Keyboard.isKeyDown(Keyboard.KEY_TAB);
                        fireThor = Keyboard.isKeyDown(Keyboard.KEY_F6);
                        fireBurst = Keyboard.isKeyDown(Keyboard.KEY_DELETE) && shift;
                        fireRepel = shift && ctrl;
                        fireDecoy = Keyboard.isKeyDown(Keyboard.KEY_F5);
                        fireRocket = Keyboard.isKeyDown(Keyboard.KEY_F3);
                        fireBrick = Keyboard.isKeyDown(Keyboard.KEY_F4);
                        
                        
                        // keys as events:
                        
                        while (Keyboard.next())
                        {
                                int key = Keyboard.getEventKey();
                                char chr = Keyboard.getEventCharacter();
                                
                                if (!Keyboard.getEventKeyState())
                                {
                                        // released a key
                                        if (key == Keyboard.KEY_DELETE)
                                        {
                                                multiFireGun = !multiFireGun;
                                        }
                                }
                                
                                if (!Keyboard.isRepeatEvent())
                                {
                                        if (!shift && chr >= '0' && chr <= '9' && 
                                                tick - lastShipChangeRequest > 10 &&
                                                localActor.canChangeShip()
                                                )
                                        {
                                                int s = chr - '0' - 1;

                                                if (s >= 0 && s < ships.getValuesLength())
                                                {
                                                        String ship = ships.get(s);
                                                        if (!ship.equals(localActor.getShip()))
                                                        {
                                                                networkedGame.sendCommand("ship", ship);
                                                                lastShipChangeRequest = tick;
                                                        }
                                                }
                                                continue;
                                        }
                                }
                        }
                }
                
                
                @Override
                public void tick(long tick)
                {
                        this.tick = tick;
                        // PhysicsEnvironment should have ticked before this one.
                        int localPid;
                        
                        if (!networkedGame.isReady())
                        {
                                // esc to cancel connecting?
                                return;
                        }
                        
                        localPid = networkedGame.getMyPid();
                        
                        if (localActor == null || localActor.isDeleted())
                        {
                                return;
                        }
                        
                        if (up || down || left || right)
                        {
                                boost = boost && (up || down);
                                // do not bother sending boost over the network if we can not use it 
                                // (to prevent unnecessary timewarps)
                                boost = boost && localActor.canBoost(); 
                                PhysicsMovement move = PhysicsMovement.get(up, down, left, right, boost);
                                physicsEnv.actorMove(physicsEnv.getTick(), localPid, move);
                                networkedGame.sendMove(physicsEnv.getTick(), move);
                        }
                        
                        if (fireGun)  { tryWeapon(this.multiFireGun ? WEAPON_SLOT.GUN_MULTI : WEAPON_SLOT.GUN , localActor); }
                        if (fireBomb) { tryWeapon(WEAPON_SLOT.BOMB, localActor); }
                        if (fireMine) { tryWeapon(WEAPON_SLOT.MINE, localActor); }
                        if (fireThor) { tryWeapon(WEAPON_SLOT.THOR, localActor); }
                        if (fireBurst) { tryWeapon(WEAPON_SLOT.BURST, localActor); }
                        if (fireRepel) { tryWeapon(WEAPON_SLOT.REPEL, localActor); }
                        if (fireDecoy) { tryWeapon(WEAPON_SLOT.DECOY, localActor); }
                        if (fireRocket) { tryWeapon(WEAPON_SLOT.ROCKET, localActor); }
                        if (fireBrick) { tryWeapon(WEAPON_SLOT.BRICK, localActor); }
                        
                }
                
                private void tryWeapon(WEAPON_SLOT weapon, ActorPublic localActor)
                {
                        if (!localActor.canFireWeapon(weapon))
                        {
                                return;
                        }
                        
                        PhysicsShipPosition weaponHint = new PhysicsShipPosition();
                        localActor.getPosition(weaponHint);
                        
                        physicsEnv.actorWeapon(
                                physicsEnv.getTick(), 
                                networkedGame.getMyPid(), 
                                weapon, 
                                true, weaponHint.x, weaponHint.y,
                                weaponHint.x_vel, weaponHint.y_vel,
                                weaponHint.rot_snapped);
                        
                        networkedGame.sendActorWeapon(physicsEnv.getTick(), weapon, weaponHint);
                }
                
        }
}
