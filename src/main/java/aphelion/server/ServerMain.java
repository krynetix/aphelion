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

import aphelion.server.game.Dummies;
import aphelion.shared.resource.Asset;
import aphelion.server.game.ServerGame;
import aphelion.server.http.HttpServer;
import aphelion.shared.event.*;
import aphelion.shared.event.promise.PromiseException;
import aphelion.shared.gameconfig.LoadYamlTask;
import aphelion.shared.map.MapClassic;
import aphelion.shared.map.MapClassic.LoadMapTask;
import aphelion.shared.physics.EnvironmentConf;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.SimpleEnvironment;
import aphelion.shared.resource.AssetCache;
import aphelion.shared.resource.FileStorage;
import aphelion.shared.resource.ResourceDB;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 *
 * @author Joris
 */
public class ServerMain implements LoopEvent, TickEvent
{
        private static final Logger log = Logger.getLogger("aphelion.server");
        private final ServerSocketChannel listen;
        private final Map<String, Object> config;
        
        private TickedEventLoop loop;
        private AphelionServer server;
        private ServerGame serverGame;
        private SimpleEnvironment physicsEnv;
        
        private Dummies dummies;
        
        private AssetCache assetCache;
        private List<Asset> assets;
        private String mapResource;
        private List<String> gameConfigResources;
        private List<String> niftyGuiResources;
        
        public ServerMain(ServerSocketChannel listen, Map<String, Object> config)
        {                
                this.listen = listen;
                this.config = config;
        }
        
        public void setup() throws IOException, ServerConfigException
        {
                int processors = Runtime.getRuntime().availableProcessors();
                if (processors < 2) { processors = 2; } // minimum of two workers
                loop = new TickedEventLoop(10_000_000L, processors, null);
                
                server = new AphelionServer(listen, new File("./www"), loop);
                
                
                try
                {
                        File dir = new File((String) config.get("assets-cache-path")).getCanonicalFile();
                        dir.mkdirs();
                        FileStorage assetCacheStorage = new FileStorage(dir);
                        
                        if (!assetCacheStorage.isUseable())
                        {
                                throw new ServerConfigException("assets-cache-path is not readable/writeable or not a directory: " + assetCacheStorage);
                        }
                        
                        assetCache = new AssetCache(assetCacheStorage);
                        
                }
                catch (ClassCastException | NullPointerException ex)
                {
                        throw new ServerConfigException("Missing or invalid server config entry: assets-cache-path");
                }
                catch (IOException ex)
                {
                        throw new ServerConfigException("The given assets-cache-path is not a valid directory: " + config.get("assets-cache-path"), ex);
                }
                
                try
                {
                        // todo: multiple arenas (seperate directories with arena config?)
                        Map<String, Object> arena = (Map<String, Object>) config.get("arena");

                        List configAssets = (List) arena.get("assets");
                        this.assets = new ArrayList<>(configAssets.size());
                        for (Object c : configAssets)
                        {
                                Asset ass = new Asset(assetCache, c);
                                this.assets.add(ass);
                        }

                        mapResource = (String) arena.get("map");

                        gameConfigResources = new ArrayList<>((List<String>) arena.get("game-config"));
                        niftyGuiResources = new ArrayList<>((List<String>) arena.get("nifty-gui"));
                }
                catch (ClassCastException | NullPointerException ex)
                {
                        throw new ServerConfigException("Invalid config for 'arena'", ex);
                }
                
                
                server.addHttpRouteStatic("assets", assetCache.getStorage().getDirectory());
                
                loop.addLoopEvent(server);
                
                ResourceDB resourceDB = new ResourceDB(loop);
                
                for (Asset ass : this.assets)
                {
                        try
                        {
                                ass.storeAsset(ass.configFile, true);
                        }
                        catch (AssetCache.InvalidContentException ex)
                        {
                                throw new AssertionError(ex);
                        }
                        
                        // ass.file is now valid
                        resourceDB.addZip(ass.file);
                }
                
                if (!resourceDB.resourceExists(mapResource))
                {
                        throw new ServerConfigException("Resource does not exist: " + mapResource);
                }
                
                for (String key : gameConfigResources)
                {
                        if (!resourceDB.resourceExists(key))
                        {
                                throw new ServerConfigException("Resource does not exist: " + mapResource);
                        }
                }
                
                
                MapClassic map;
                List<LoadYamlTask.Return> gameConfig;
                
                try
                {
                        map = new LoadMapTask(resourceDB, false).work(mapResource);
                        gameConfig = new LoadYamlTask(resourceDB).work(gameConfigResources);
                }
                catch (PromiseException ex)
                {
                        log.log(Level.SEVERE, null, ex);
                        throw (IOException) ex.getCause();
                }
                
                physicsEnv = new SimpleEnvironment(true, map);
                
                for (LoadYamlTask.Return ret : gameConfig)
                {
                        
                        physicsEnv.loadConfig(physicsEnv.getTick() - physicsEnv.getConfig().HIGHEST_DELAY, ret.fileIdentifier, ret.yamlDocuments);
                }
                gameConfig = null;
                
                serverGame = new ServerGame(physicsEnv, loop, assets, mapResource, gameConfigResources, niftyGuiResources);
                loop.addLoopEvent(serverGame);
                loop.addTickEvent(serverGame);
                server.setGameClientListener(serverGame);
                
                dummies = new Dummies(10, physicsEnv, serverGame);
                dummies.setup();
                
                
                loop.addLoopEvent(this);
                loop.addTickEvent(this);
                loop.addTickEvent(dummies);
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
        }
        
        public static void main(String[] args) throws IOException, ServerConfigException
        {
                if (args.length < 1)
                {
                        throw new IllegalArgumentException("The first argument should be the path to a yaml config file");
                }
                
                Yaml yaml = new Yaml(new SafeConstructor());
                
                Map<String, Object> config;
                String address;
                int port;
                
                try
                {
                        config = (Map<String, Object>) yaml.load(new FileInputStream(args[0])); 
                }
                catch (FileNotFoundException | ClassCastException | YAMLException ex)
                {
                        // Note: YAMLException is a RunTimeException
                        throw new ServerConfigException("Unable to read server config", ex);
                }
                
                try
                {
                        address = config.containsKey("bind-address") ? (String) config.get("bind-address") : "0.0.0.0";
                        port = config.containsKey("bind-port") ? (int) config.get("bind-port") : 80;
                }
                catch (ClassCastException ex)
                {
                        throw new ServerConfigException("Invalid bind-address or bind-port", ex);
                }
                
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
