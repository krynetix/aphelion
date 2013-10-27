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

package aphelion.server;

import aphelion.server.game.ServerGame;
import aphelion.server.http.HttpServer;
import aphelion.shared.event.*;
import aphelion.shared.gameconfig.LoadYamlTask;
import aphelion.shared.map.MapClassic;
import aphelion.shared.map.MapClassic.LoadMapTask;
import aphelion.shared.net.protobuf.GameOperation;
import aphelion.shared.net.protobuf.GameS2C;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.WEAPON_SLOT;
import aphelion.shared.physics.valueobjects.PhysicsMovement;
import aphelion.shared.resource.ResourceDB;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 *
 * @author Joris
 */
public class ServerMain implements LoopEvent, TickEvent
{
        private static final Logger log = Logger.getLogger("aphelion.server");
        private TickedEventLoop loop;
        private final ServerSocketChannel listen;
        private AphelionServer server;
        private ServerGame serverGame;
        private PhysicsEnvironment physicsEnv;
        private final Map<String, Object> config;
        
        private static final int DUMMY_1_PID = -1;
        private static final int DUMMY_2_PID = -2;
        
        public ServerMain(ServerSocketChannel listen, Map<String, Object> config)
        {                
                this.listen = listen;
                this.config = config;
        }
        
        public void setup() throws IOException
        {
                int processors = Runtime.getRuntime().availableProcessors();
                if (processors < 2) { processors = 2; } // minimum of two workers
                loop = new TickedEventLoop(10, processors, null);
                server = new AphelionServer(listen, new File("./www"), loop);
                loop.addLoopEvent(server);
                
                ResourceDB resourceDB = new ResourceDB(loop);
                resourceDB.addZip(new File("assets/singleplayer.zip"));
                MapClassic map;
                List<LoadYamlTask.Return> gameConfig;
                try
                {
                        map = new LoadMapTask(resourceDB, false).work("level.map");
                        gameConfig = new LoadYamlTask(resourceDB).work(resourceDB.getKeysByPrefix("gameconfig."));
                }
                catch (WorkerTask.WorkerException ex)
                {
                        log.log(Level.SEVERE, null, ex);
                        throw (IOException) ex.getCause();
                }
                
                physicsEnv = new PhysicsEnvironment(true, map);
                
                for (LoadYamlTask.Return ret : gameConfig)
                {
                        physicsEnv.loadConfig(physicsEnv.getTick() - PhysicsEnvironment.TOTAL_HISTORY, ret.fileIdentifier, ret.yamlDocuments);
                }
                gameConfig = null;
                
                // dummy for testing
                physicsEnv.actorNew(0, DUMMY_1_PID, "Dummy", 1, "javelin");
                physicsEnv.actorWarp(0, DUMMY_1_PID, false, 512 * 16 * 1024, 448 * 16 * 1024, 0, 10000, 0);
                
                physicsEnv.actorNew(0, DUMMY_2_PID, "Dummy 2", 1, "terrier");
                physicsEnv.actorWarp(0, DUMMY_2_PID, false, 512 * 16 * 1024, 448 * 16 * 1024, 0, -10000, 0); 
                
                serverGame = new ServerGame(physicsEnv, loop);
                loop.addLoopEvent(serverGame);
                loop.addTickEvent(serverGame);
                server.setGameClientListener(serverGame);
                
                loop.addLoopEvent(this);
                loop.addTickEvent(this);
                server.setup();
        }
        
        public int getHTTPListeningPort()
        {
                return server.getHTTPListeningPort();
        }
        
        public void run()
        {
                loop.run();
        }
        
        public void stop()
        {
                loop.interrupt();
                server.stop();
                log.log(Level.INFO, "ServerMain has stopped");
        }
        
        @Override
        public void loop(long systemNanoTime, long sourceNanoTime)
        {
                if (serverGame == null)
                {
                        server.setPingPlayerCount(-1, -1);
                }
                else
                {
                        server.setPingPlayerCount(serverGame.getPlayerCount(), -1);
                        // todo playing
                }
        }

        @Override
        public void tick(long tick)
        {
                if (tick % 20 == 0) // dummy
                {
                        GameS2C.S2C.Builder s2c = GameS2C.S2C.newBuilder();
                        
                        {
                                GameOperation.ActorMove.Builder moveBuilder = s2c.addActorMoveBuilder();

                                moveBuilder.setTick(physicsEnv.getTick()-20);
                                moveBuilder.setPid(DUMMY_1_PID);
                                moveBuilder.setDirect(true);

                                PhysicsMovement move = PhysicsMovement.get(SwissArmyKnife.random.nextInt(16));
                                for (int i = 20; i > 0; --i)
                                {
                                        physicsEnv.actorMove(physicsEnv.getTick()-i, DUMMY_1_PID, move);
                                        moveBuilder.addMove(move.bits);
                                }
                                
                                
                                if (tick % 1000 == 0 && SwissArmyKnife.random.nextInt(3) == 0)
                                {
                                        physicsEnv.actorWeapon(physicsEnv.getTick(), DUMMY_1_PID, WEAPON_SLOT.BOMB, false, 0, 0, 0, 0, 0);
                                        GameOperation.ActorWeapon.Builder weaponBuilder = s2c.addActorWeaponBuilder();
                                        weaponBuilder.setTick(physicsEnv.getTick());
                                        weaponBuilder.setPid(DUMMY_1_PID);
                                        weaponBuilder.setSlot(WEAPON_SLOT.BOMB.id);
                                }
                        }
                        
                        {
                                GameOperation.ActorMove.Builder moveBuilder = s2c.addActorMoveBuilder();

                                moveBuilder.setTick(physicsEnv.getTick()-20);
                                moveBuilder.setPid(DUMMY_2_PID);
                                moveBuilder.setDirect(true);

                                PhysicsMovement move = tick % 1000 < 500 
                                                       ? PhysicsMovement.get(false, false, false, true, false) 
                                                       : PhysicsMovement.get(false, false, true, false, false);
                                
                                for (int i = 20; i > 0; --i)
                                {
                                        physicsEnv.actorMove(physicsEnv.getTick()-i, DUMMY_2_PID, move);
                                        moveBuilder.addMove(move.bits);
                                }
                        }
                        
                        serverGame.broadcast(s2c);
                }
        }
        
        public static void main(String[] args) throws IOException
        {
                if (args.length < 1)
                {
                        throw new IllegalArgumentException("The first argument should be the path to a yaml config file");
                }
                
                Yaml yaml = new Yaml(new SafeConstructor());
                // might throw IOException, ClassCastException, etc
                Map<String, Object> config = (Map<String, Object>) yaml.load(new FileInputStream(args[0])); 
                
                String address = config.containsKey("bind-address") ? (String) config.get("bind-address") : "0.0.0.0";
                int port = config.containsKey("bind-port") ? (int) config.get("bind-port") : 80;
                
                try (ServerSocketChannel ssChannel = HttpServer.openServerChannel(new InetSocketAddress(address, port)))
                {
                        Deadlock.start(false, null);
                        ServerMain main = new ServerMain(ssChannel, config);
                        main.setup();
                        main.run();
                        Deadlock.stop();
                }
        }

        
        
}
