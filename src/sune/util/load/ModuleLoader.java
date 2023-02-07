package sune.util.load;

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ResolvedModule;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sun.misc.Unsafe;

/**
 * <p>
 * Contains methods for adding and loading custom modules to the boot
 * module layer at runtime, allowing dynamic class loading into a class loader.
 * </p>
 * 
 * <p>
 * Note that this class heavily uses Unsafe operations to achieve dynamic loading
 * of classes and manipulates with the internals of some of the Java's module
 * system classes. Hence this class may be highly unstable!
 * </p>
 * 
 * @author Sune
 * 
 * @see ModuleLayer
 * @see ModuleFinder
 * @see Module
 * @see ResolvedModule
 * @see Configuration
 */
public final class ModuleLoader {
	
	private static final Unsafe unsafe = UnsafeInstance.get();
	
	private static final ModuleLayer  parentLayer = ModuleLayer.boot();
	private static final ModuleFinder emptyFinder = ModuleFinder.of();
	
	private static Map<String, Module>         bootModulesNames;
	private static Set<Module>                 bootModules;
	private static List<ModuleLayer>           bootModuleLayers;
	private static Map<String, ResolvedModule> resolvedModulesNames;
	private static Set<ResolvedModule>         resolvedModules;
	
	@SuppressWarnings("unchecked")
	private static final void ensureBootModules() {
		try {
			if(bootModulesNames == null) {
				Field field = ModuleLayer.class.getDeclaredField("nameToModule");
				long offset = unsafe.objectFieldOffset(field);
				bootModulesNames = (Map<String, Module>) unsafe.getObject(parentLayer, offset);
			}
			
			if(bootModules == null) {
				Field field = ModuleLayer.class.getDeclaredField("modules");
				long offset = unsafe.objectFieldOffset(field);
				bootModules = new HashSet<>(parentLayer.modules());
				unsafe.putObject(parentLayer, offset, bootModules);
			}
			
			if(bootModuleLayers == null) {
				Class<?> clazz = Class.forName("java.util.Collections$UnmodifiableList");
				Field field = clazz.getDeclaredField("list");
				long offset = unsafe.objectFieldOffset(field);
				Field field2 = ModuleLayer.class.getDeclaredField("allLayers");
				long offset2 = unsafe.objectFieldOffset(field2);
				List<ModuleLayer> layers = (List<ModuleLayer>) unsafe.getObject(parentLayer, offset2);
				bootModuleLayers = (List<ModuleLayer>) unsafe.getObject(layers, offset);
			}
		} catch(Exception ex) {
			throw new IllegalStateException("Unable to initialize properties for boot modules", ex);
		}
	}
	
	@SuppressWarnings("unchecked")
	private static final void ensureResolvedModules() {
		try {
			if(resolvedModulesNames == null) {
				Class<?> clazz = Class.forName("java.util.ImmutableCollections$MapN");
				Field field = clazz.getDeclaredField("table");
				long offset = unsafe.objectFieldOffset(field);
				Field field2 = Configuration.class.getDeclaredField("nameToModule");
				long offset2 = unsafe.objectFieldOffset(field2);
				Configuration configuration = parentLayer.configuration();
				Map<String, ResolvedModule> modules = (Map<String, ResolvedModule>) unsafe.getObject(configuration, offset2);
				Object[] table = (Object[]) unsafe.getObject(modules, offset);
				
				Map<String, ResolvedModule> mapElements = new LinkedHashMap<>();
				for(int i = 0, l = table.length; i < l; i+=2) {
					if(table[i] != null) {
						mapElements.put((String) table[i], (ResolvedModule) table[i+1]);
					}
				}
				
				unsafe.putObject(configuration, offset2, mapElements);
				resolvedModulesNames = mapElements;
			}
			
			if(resolvedModules == null) {
				Class<?> clazz = Class.forName("java.util.ImmutableCollections$SetN");
				Field field = clazz.getDeclaredField("elements");
				long offset = unsafe.objectFieldOffset(field);
				Field field2 = Configuration.class.getDeclaredField("modules");
				long offset2 = unsafe.objectFieldOffset(field2);
				Configuration configuration = parentLayer.configuration();
				Set<ResolvedModule> modules = (Set<ResolvedModule>) unsafe.getObject(configuration, offset2);
				Object[] elements = (Object[]) unsafe.getObject(modules, offset);
				
				Set<ResolvedModule> setElements = new LinkedHashSet<>();
				for(Object module : elements) {
					if(module != null) {
						setElements.add((ResolvedModule) module);
					}
				}
				
				unsafe.putObject(configuration, offset2, setElements);
				resolvedModules = setElements;
			}
		} catch(Exception ex) {
			throw new IllegalStateException("Unable to initialize properties for resolved modules", ex);
		}
	}
	
	/**
	 * Gets a module with the given {@code name} that is defined in the given
	 * module {@code layer} and adds it to the boot module layer. This acutally
	 * makes the module declared in every class throughout the application,
	 * however does not load the module itself.
	 * @param layer the module layer
	 * @param name the module's name
	 * @return The module instance.
	 */
	public static final Module addModule(ModuleLayer layer, String name) {
		ensureBootModules();
		Module module = layer.findModule(name).get();
		
		if(module != null) {
			bootModulesNames.put(name, module);
			bootModules.add(module);
			bootModuleLayers.add(layer);
		}
		
		return module;
	}
	
	/**
	 * Gets a resolved module with the given {@code name} that is defined in
	 * the given {@code configuration} and adds it to the boot module layer's
	 * configuration. This acutally makes the module declared in every class
	 * throughout the application, however does not load the module itself.
	 * @param config the configuration
	 * @param name the resolved module's name
	 * @return The resolved module instance.
	 */
	public static final ResolvedModule addResolvedModule(Configuration config, String name) {
		ensureResolvedModules();
		ResolvedModule module = config.findModule(name).get();
		
		if(module != null) {
			resolvedModulesNames.put(name, module);
			resolvedModules.add(module);
		}
		
		return module;
	}
	
	/**
	 * Loads a file located at the given {@code path} (JAR or ZIP) with
	 * the given {@code name} as its name to the given {@code loader}.
	 * This acutally makes the module declared in every class throughout
	 * the application, however does not load the module's content itself.
	 * @param path the path to the {@code .jar} or {@code .zip} file
	 * @param name the module's name
	 * @param loader the class loader where to define the module
	 * @return The resolved module instance, or {@code null} if the module
	 * could not be loaded.
	 */
	public static final ResolvedModule loadModule(Path path, String name, ClassLoader loader) {
		ModuleFinder finder = ModuleFinder.of(path);
		Configuration config = parentLayer.configuration().resolve(finder, emptyFinder, List.of(name));
		ModuleLayer layer = parentLayer.defineModulesWithOneLoader(config, loader);
		
		return addModule(layer, name) != null
					? addResolvedModule(config, name)
					: null;
	}
	
	/**
	 * Checks if a module with the given {@code name} is loaded in the boot
	 * module layer.
	 * @param name the module's name
	 * @return {@code true}, if the module is loaded, otherwise {@code false}.
	 */
	public static final boolean isLoaded(String name) {
		return parentLayer.findModule(name).isPresent();
	}
	
	/**
	 * Gets a module with the given {@code name} if it is loaded, otherwise
	 * returns {@code null}.
	 * @param name the module's name
	 * @return The resolved module, if the module is loaded, otherwise {@code null}.
	 */
	public static final ResolvedModule moduleOfName(String name) {
		ensureResolvedModules();
		return resolvedModulesNames.get(name);
	}
}