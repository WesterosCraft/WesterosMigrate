package com.westeroscraft.westerosmigrate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.plugin.Dependency;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.yaml.snakeyaml.Yaml;

import io.github.nucleuspowered.nucleus.api.NucleusAPI;
import io.github.nucleuspowered.nucleus.api.service.NucleusWarpService;
import me.lucko.luckperms.LuckPerms;
import me.lucko.luckperms.api.LocalizedNode;
import me.lucko.luckperms.api.LuckPermsApi;
import me.lucko.luckperms.api.Node;
import me.lucko.luckperms.api.User;

import com.flowpowered.math.vector.Vector3d;
import com.google.common.base.Charsets;
import com.google.inject.Inject;

@Plugin(id = "westerosmigrate", 
		name = "WesterosMigrate",
		version = "0.1", 
		description = "Migration aid for 1.7.10 to 1.11.2 migration", 
		authors = {"mikeprimm"},
		dependencies = { @Dependency(id = "nucleus"), @Dependency(id = "luckperms") } )
public class WesterosMigrate {

	@Inject private Logger logger;
	@Inject private PluginContainer plugin;
	@Inject @ConfigDir(sharedRoot = false) private File configDir;

	private Map<String, String> grpidmap = new HashMap<String, String>();
	
	public WesterosMigrate() {
		grpidmap.put("NoBuild", "default");
		grpidmap.put("NewBuilder", "newbuilder");
		grpidmap.put("Builder", "builder");
		grpidmap.put("Editor", "editor");
		grpidmap.put("EditorLite", "editorlite");
		grpidmap.put("Moderator", "moderator");
		grpidmap.put("Administrator", "administrator");
		grpidmap.put("Coder", "coder");
	}
	
	public Logger getLogger(){
		return logger;
	}
	
	public File getConfigDirectory(){
		return configDir;
	}
	
	public PluginContainer getPlugin(){
		return plugin;
	}
	
	/**
	 * 
	 * All initializations for the plugin should be done here.
	 * 
	 * @param e GamePreInitializationEvent dispatched by Sponge.
	 */
	@Listener
	public void onGamePreInitialization(GamePreInitializationEvent e){
		getLogger().info("Enabling " + plugin.getName() + " version " + plugin.getVersion().get() + ".");
	}
	
    @Listener
    public void onGameStartedServer(GameStartedServerEvent e){
        Optional<NucleusWarpService> warp = NucleusAPI.getWarpService();
        
        logger.info("Migrate warps");;
        File warpdir = new File(configDir, "warps");
        if (warp.isPresent() && warpdir.isDirectory()) {
            NucleusWarpService svc = warp.get();
            for (String fn : warpdir.list(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".yml");
                }
                })) {
                Yaml yaml = new Yaml();
                try {
                    File rdrfile = new File(warpdir, fn);
                    Reader rdr = new FileReader(rdrfile);
                    Map<String, Object> vals = (Map<String, Object>) yaml.load(rdr);
                    rdr.close();
                    String warpname = (String) vals.get("name");
                    String worldname = (String) vals.get("world");
                    Double x = (Double) vals.get("x");
                    Double y = (Double) vals.get("y");
                    Double z = (Double) vals.get("z");
                    Double yaw = (Double) vals.get("yaw");
                    Double pitch = (Double) vals.get("pitch");
                    Optional<World> world = Sponge.getServer().getWorld(worldname);
                    if (world.isPresent()) {
                        Location<World> loc = new Location<World>(world.get(), x, y, z);
                        Vector3d facing = new Vector3d(pitch, yaw, 0);
                        svc.removeWarp(warpname);
                        boolean rslt = svc.setWarp(warpname, loc, facing);
                        logger.info(String.format("setWarp(%s, %s, %s)", warpname, loc, facing));
                        rdrfile.delete();
                    }
                    else {
                        logger.info("World not found : " + worldname);
                    }
                } catch (IOException iox) {
                    logger.info("Error processing " + fn, iox);
                }
            }
        }
        LuckPermsApi lpapi = LuckPerms.getApi();
        
        logger.info("Migrate users");;
        File users = new File(configDir, "users.yml");
        if ((lpapi != null) && (users.isFile())) {
            Yaml yaml = new Yaml();
            try {
                Reader rdr = new InputStreamReader(new FileInputStream(users), Charsets.UTF_8);
                Map<String, Object> vals = (Map<String, Object>) yaml.load(rdr);
                rdr.close();
                Map<String, Object> usrs = (Map<String, Object>) vals.get("users");
                for (String key : usrs.keySet()) {
                	Map<String, Object> uobj = (Map<String, Object>) usrs.get(key);
                	String groupname = (String) uobj.get("group");
                	String lastname = (String) uobj.get("lastname");
                	String prefix = null;
                	String suffix = null;
                	Map<String, Object> info = (Map<String, Object>) uobj.get("info");
                	if (info != null) {
                		prefix = (String) info.get("prefix");
                		suffix = (String) info.get("suffix");
                	}
                	UUID uuid = null;
                	try {
                		uuid = UUID.fromString(key);
                	} catch (IllegalArgumentException x) {
                        uuid = lpapi.getStorage().getUUID(key).join();
                        if (uuid == null) {
                        	logger.info("Unable to get UUID for " + key);
                            continue;
                        }
                	}
        			String ngroupname = grpidmap.get(groupname);
        			if (ngroupname != null) {
        				if (lpapi.getStorage().loadUser(uuid, "null").join().booleanValue()) {
        					User user = lpapi.getUser(uuid);
        					me.lucko.luckperms.api.Group g;
        					me.lucko.luckperms.api.Node n;
        					// Unset groups
        					for (LocalizedNode gn : user.getAllNodes()) {
        					    if (gn.isGroupNode() == false) continue;
            					g = lpapi.getGroup(gn.getGroupName());
            					n = lpapi.getNodeFactory().makeGroupNode(g).build();
								user.unsetPermission(n);
        					}
        					// Build permission node for group
        					g = lpapi.getGroup(ngroupname);
        					n = lpapi.getNodeFactory().makeGroupNode(g).build();
        					// Set the permission, and return true if the user didn't already have it set.
        					if (user.hasPermission(n).asBoolean() == false) {
        						user.setPermission(n); 
                				logger.info("User " + user.getFriendlyName() + " added to group " + g.getName());
        					}
        					if (user.getPrimaryGroup().equals(ngroupname) == false) {
        						user.setPrimaryGroup(ngroupname);
                				logger.info("User " + user.getFriendlyName() + " set to primary group " + ngroupname);
        					}
							user.clearMatching(new Predicate<Node>() {
								public boolean test(Node t) {
									Entry<Integer, String> pfx;
									if (t.isPrefix()) {
										pfx = t.getPrefix();
										if ((pfx != null) && (pfx.getKey().intValue() == 200)) {
											return true;
										}
									}
									if (t.isSuffix()) {
										pfx = t.getSuffix();
										if ((pfx != null) && (pfx.getKey().intValue() == 200)) {
											return true;
										}
									}
									return false;
								} });
        					if (prefix != null) {
            					n = lpapi.getNodeFactory().makePrefixNode(200, prefix).build();
        						user.setPermission(n);
                				logger.info("User " + user.getFriendlyName() + " prefix set to " + prefix);
        					}
        					if (suffix != null) {
            					n = lpapi.getNodeFactory().makePrefixNode(200, suffix).build();
        						user.setPermission(n);
                				logger.info("User " + user.getFriendlyName() + " suffix set to " + suffix);
        					}
    						// first save the user
    						lpapi.getStorage().saveUser(user).join();
    	                    lpapi.cleanupUser(user);
        				}
	                	else {
	                		logger.info("User " + key + " not found");
	                	}
                	}
                }
            	users.delete();
            } catch (IOException iox) {
                logger.info("Error processing " + users, iox);
			}
        }
    }
}
	
