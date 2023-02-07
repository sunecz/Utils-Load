package sune.util.load;

import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.lang.ref.SoftReference;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * A module loader that loads modules lazily. That is, if a given
 * class loader supports loading of modules through their references,
 * it injects a module reference but do not actually load any classes.
 * This means that all classes from that module are loaded as needed
 * and not always as is when {@linkplain ZIPLoader} is used.
 * </p>
 * 
 * <p>
 * Note that this class uses Unsafe and Reflection to achieve
 * the above functionality and therefore may be unstable.
 * </p>
 * 
 * @author Sune
 */
public final class ModuleLazyLoader {
	
	private static final Map<Class<?>, SoftReference<Method>> methods = new HashMap<>();
	
	// Forbid anyone to create an instance of this class
	private ModuleLazyLoader() {
	}
	
	private static final Method findLoadModuleMethod(Class<?> clazz) {
		do {
			try {
				return clazz.getDeclaredMethod("loadModule", ModuleReference.class);
			} catch(NoSuchMethodException ex) {
				clazz = clazz.getSuperclass();
			}
		} while(clazz != null);
		
		return null;
	}
	
	private static final void loadModule(ModuleReference module, ClassLoader loader) throws Exception {
		Class<?> clazz = loader.getClass();
		Method method = null;
		
		SoftReference<Method> ref;
		if((ref = methods.get(clazz)) == null) {
			Method mtd = findLoadModuleMethod(clazz);
			
			if(mtd != null) {
				Reflection.setAccessible(mtd, true);
			}
			
			methods.put(clazz, ref = new SoftReference<>(mtd));
		}
		
		if((method = ref.get()) == null) {
			throw new IllegalStateException("Module loading through its reference not supported");
		}
		
		method.invoke(loader, module);
	}
	
	/**
	 * <p>
	 * Lazily loads a module at the given path with the given name in the given
	 * class loader.
	 * </p>
	 * 
	 * <p>
	 * This actually does not load any classes, it only registers the module in
	 * the internal structures of the class loader, so that when a class from this
	 * module is requested it can then be resolved using this module.
	 * </p>
	 * 
	 * @param path the module's path
	 * @param name the module's name
	 * @param loader the class loader where to load the module
	 * @return The resolved module instance, or {@code null} if the module
	 * cannot be loaded.
	 */
	public static final ResolvedModule loadModule(Path path, String name, ClassLoader loader) {
		ResolvedModule module;
		
		if((module = ModuleLoader.moduleOfName(name)) != null) {
			return module;
		}
		
		module = ModuleLoader.loadModule(path, name, loader);
		
		try {
			loadModule(module.reference(), loader);
		} catch(Exception ex) {
			throw new IllegalStateException("Unable to load module: " + name, ex);
		}
		
		return module;
	}
}