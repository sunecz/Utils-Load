package sune.util.load;

import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Special class loader that loads a specific given class and all
 * its required classes. Apart from {@linkplain RootClassLoader}
 * it also analyzes the class byte code using {@linkplain ClassDependencyAnalyzer},
 * since not all required classes may be loaded otherwise.
 * @author Sune
 */
public abstract class RootAnalyzingClassLoader extends RootClassLoader {
	
	public RootAnalyzingClassLoader(ClassLoader loader) {
		super(loader);
	}
	
	protected static final boolean classLoaded(ClassLoader loader, String name) {
		try {
			Class.forName(name, false, loader);
			return true;
		} catch(ClassNotFoundException ex) {
			return false;
		}
	}
	
	protected static final boolean isBuiltinDependency(String name) {
		return name.startsWith("java.");
	}
	
	protected static final int dependencyComparator(String a, String b) {
		int al = a.length() - a.replace("$", "").length();
		int bl = b.length() - b.replace("$", "").length();
		return al < bl ? -1 : (al > bl ? 1 : a.compareTo(b));
	}
	
	protected final List<String> dependencies(byte[] bytes) {
		return ClassDependencyAnalyzer.dependencies(ByteBuffer.wrap(bytes)).stream()
					.filter(Predicate.not(RootAnalyzingClassLoader::isBuiltinDependency))
					.sorted(RootAnalyzingClassLoader::dependencyComparator)
					.collect(Collectors.toList());
	}
	
	@Override
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
			// Dependencies will move the original class down the stack
			do {
				entry = stack.peek();
				path  = entry.getKey();
				name  = entry.getValue();
				bytes = bytes(path);
				
				// Push all class dependecies to the stack
				for(String depName : dependencies(bytes)) {
					if(name.equals(depName) || loaded.contains(depName)
							|| (classLoaded(loader, depName) && loaded.add(depName))) {
						continue; // Skip already loaded classes
					}
					
					pushToStack(stack, queued, classNameToPath(depName));
				}
			} while(stack.peek() != entry);
			
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