package sune.util.load;

import java.lang.module.ResolvedModule;
import java.nio.file.Path;

/**
 * Contains useful methods for loading ZIP files.
 * @author Sune
 */
public final class ZIPLoader {
	
	// Forbid anyone to create an instance of this class
	private ZIPLoader() {
	}
	
	/**
	 * Loads a ZIP file located at the given {@code path}. The file is loaded
	 * with the given {@code name} and into the given {@code loader}.
	 * @param path the path of a file to be loaded
	 * @param name the module name
	 * @param loader the ClassLoader where load the file to
	 * @return {@code true}, if the file was loaded, otherwise {@code false}.
	 */
	public static final boolean load(Path path, String name, ClassLoader loader) throws Exception {
		if(ModuleLoader.isLoaded(name)) {
			return true;
		}
		
		ResolvedModule module = ModuleLoader.loadModule(path, name, loader);
		
		if(module == null) {
			return false; // Unable to load the module
		}
		
		ModuleContentLoader.loadContent(module, loader);
		return true;
	}
}