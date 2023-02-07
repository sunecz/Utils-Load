package sune.util.load;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

/**
 * Special class loader that loads a specific given class and all
 * its required classes.
 * @author Sune
 */
public abstract class RootClassLoader {
	
	protected final ClassLoader loader;
	
	public RootClassLoader(ClassLoader loader) {
		this.loader = Objects.requireNonNull(loader);
	}
	
	/**
	 * Extracts a full class name from the given {@code path}.
	 * @param path the path
	 * @return The class name.
	 */
	public static final String pathToClassName(String path) {
		return (path.endsWith(".class") ? path.substring(0, path.length() - ".class".length()) : path).replace('/', '.');
	}
	
	/**
	 * Converts a full class name given as the {@code name} to a path.
	 * @param name the class name
	 * @return The resource path.
	 */
	public static final String classNameToPath(String name) {
		return (name.endsWith(".class") ? name.substring(0, name.length() - ".class".length()) : name).replace('.', '/') + ".class";
	}
	
	/**
	 * <p>
	 * Checks if the given {@code path} is a class file or not.
	 * </p>
	 * 
	 * <p>
	 * Note that this method does not classify {@code module-info.class}
	 * as a actual class file, therefore returning {@code false}.
	 * </p>
	 * 
	 * @param path the path
	 * @return {@code true}, if a file located at the path is a class file,
	 * otherwise {@code false}.
	 */
	public static final boolean isClassFile(String path) {
		return path.endsWith(".class") && !path.endsWith("module-info.class");
	}
	
	protected static final Class<?> defineClass(ClassLoader loader, String name, byte[] bytes)
			throws InvocationTargetException,
				   IllegalAccessException,
				   IllegalArgumentException {
		try {
			// If the class was already loaded, just return it
			return Class.forName(name, false, loader);
		} catch(ClassNotFoundException ex) {
			// Not loaded, will load
		}
		
		// Define the requested class using the given bytes
		return UnsafeLegacy.defineClass(name, bytes, 0, bytes.length, loader, null);
	}
	
	/**
	 * Gets bytes from a file located at the given {@code path} in
	 * the current {@code module}.
	 * @param path the path to the resource
	 * @return The content of the resource as an byte array.
	 */
	protected abstract byte[] bytes(String path) throws Exception;
	
	protected void pushToStack(Deque<Entry<String, String>> stack, Set<String> queued, String path) {
		if(!queued.contains(path)) {
			stack.push(Map.entry(path, pathToClassName(path)));
			queued.add(path);
		}
	}
	
	protected void pushToStackTop(Deque<Entry<String, String>> stack, Set<String> queued, String path)
			throws Exception {
		if(!queued.contains(path)) {
			stack.push(Map.entry(path, pathToClassName(path)));
			queued.add(path);
		} else {
			Entry<String, String> entry = null;
			
			for(Iterator<Entry<String, String>> it = stack.iterator(); it.hasNext();) {
				Entry<String, String> current = it.next();
				
				if(current.getKey().equals(path)) {
					it.remove();
					entry = current;
					break;
				}
			}
			
			if(entry != null) {
				stack.push(entry);
			}
		}
	}
	
	/**
	 * Loads a class given by the {@code path}. This class is loaded into
	 * the current loader. All the classes that are required by
	 * this class are loaded beforehand. These classes must be already loaded
	 * or must be resolvable using the given resolver.
	 * @param path the path of the class
	 * @return The class object of a class file located at the given
	 * {@code path}.
	 */
	public Class<?> loadClass(String path) throws Exception {
		Class<?> clazz = null;
		
		if(!isClassFile(path)) {
			return clazz; // Do not load non-class files
		}
		
		Set<String> loaded = new HashSet<>();
		Set<String> queued = new HashSet<>();
		Deque<Entry<String, String>> stack = new ArrayDeque<>();
		pushToStack(stack, queued, path);
		
		Entry<String, String> entry;
		String name;
		byte[] bytes;
		do {
			entry = stack.peek();
			path  = entry.getKey();
			name  = entry.getValue();
			bytes = bytes(path);
			
			try {
				clazz = defineClass(loader, name, bytes);
				// Remember that we already loaded this class
				loaded.add(clazz.getName());
				// Class was successfully defined, remove it from the stack
				stack.remove();
			} catch(InvocationTargetException
						| IllegalArgumentException
						| IllegalAccessException ex) {
				if(ex.getCause() instanceof NoClassDefFoundError) {
					// The class needs another class to be defined
					String classPath = classNameToPath(ex.getCause().getMessage());
					pushToStackTop(stack, queued, classPath);
				} else {
					throw ex;
				}
			}
		}
		// Repeat till there are some class need defining
		while(!stack.isEmpty());
		
		// Return the requested class
		return clazz;
	}
}