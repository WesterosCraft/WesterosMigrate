package com.westeroscraft.westerosmigrate;

import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Reader;
import java.util.Optional;

import org.slf4j.Logger;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GamePostInitializationEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.plugin.Dependency;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.yaml.snakeyaml.Yaml;

import io.github.nucleuspowered.nucleus.api.NucleusAPI;
import io.github.nucleuspowered.nucleus.api.service.NucleusWarpService;

import com.google.inject.Inject;

@Plugin(id = "westerosmigrate", 
		name = "WesterosMigrate",
		version = "0.1", 
		description = "Migration aid for 1.7.10 to 1.11.2 migration", 
		authors = {"mikeprimm"},
		dependencies = { @Dependency(id = "nucleus") } )
public class WesterosMigrate {

	@Inject private Logger logger;
	@Inject private PluginContainer plugin;
	@Inject @ConfigDir(sharedRoot = false) private File configDir;
	
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
    public void onGamePostInitialization(GamePostInitializationEvent e){
        Optional<NucleusWarpService> warp = NucleusAPI.getWarpService();
        
        if (warp.isPresent()) {
            File warpdir = new File(configDir, "warps");
            for (String fn : warpdir.list(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".yml");
                }
                })) {
                Yaml yaml = new Yaml();
                try {
                    Reader rdr = new FileReader(new File(warpdir, fn));
                    yaml.load(rdr);
                    rdr.close();
                } catch (IOException iox) {}
            }
        }
        
    }
}
	
