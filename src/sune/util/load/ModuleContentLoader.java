package sune.util.load;

import java.io.IOException;
import java.lang.module.ModuleReader;
import java.lang.module.ResolvedModule;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Dynamically loads contents of a module into a ClassLoader at runtime.
 * @author Sune
 */
public class ModuleContentLoader implements AutoCloseable {

	protected final ClassLoader loader;
	protected final ResolvedModule module;
	
	private ModuleReader reader;
	private RootClassLoader rootClassLoader;
	
	/**
	 * Creates a new instance for the given {@code module} and {@code loader}.
	 * @param module the module where to read the contents from
	 * @param loader the loader where to load the contents to
	 */
	private ModuleContentLoader(ResolvedModule module, ClassLoader loader) {
		this.module = Objects.requireNonNull(module);
		this.loader = Objects.requireNonNull(loader);
		this.rootClassLoader = new ModuleContentRootClassLoader(loader);
	}
	
	/**
	 * Dynamically loads contents of the given {@code module} to the given
	 * {@code loader}. This actually defines and loads all the classes that
	 * are located in the given module, making them accessible throughout
	 * the application.
	 * @param module the module where to read the contents from
	 * @param loader the loader where to load the contents to
	 */
	public static final void loadContent(ResolvedModule module, ClassLoader loader) throws Exception {
		try(ModuleContentLoader contentLoader = new ModuleContentLoader(module, loader)) {
			contentLoader.loadAll();
		}
	}
	
	/**
	 * Extracts a full class name from the given {@code path}.
	 * @param path the path
	 * @return The class name.
	 */
	public static final String pathToClassName(String path) {
		return RootClassLoader.pathToClassName(path);
	}
	
	/**
	 * Converts a full class name given as the {@code name} to a path.
	 * @param name the class name
	 * @return The resource path.
	 */
	public static final String classNameToPath(String name) {
		return RootClassLoader.classNameToPath(name);
	}
	
	/**
	 * Checks if the given {@code path} is a class file or not.<br><br>
	 * <em>Note:</em> this method does not classify {@code module-info.class}
	 * as a actual class file, therefore returning {@code false}.
	 * @param path the path
	 * @return {@code true}, if a file located at the path is a class file,
	 * otherwise {@code false}.
	 */
	public static final boolean isClassFile(String path) {
		return RootClassLoader.isClassFile(path);
	}
	
	private final void ensureReader() throws IOException {
		if(reader == null) {
			reader = module.reference().open();
		}
	}
	
	public void loadAll() throws Exception {
		try(ModuleReader reader = module.reference().open()) {
			for(String classPath : ((Iterable<String>) reader.list()::iterator)) {
				loadClass(classPath);
			}
		}
	}
	
	/**
	 * Loads a class given by the {@code path}. This class is loaded into
	 * the current {@code loader}. All the classes that are required by
	 * this class are loaded beforehand. These classes must be already loaded
	 * or must be located in the current module.
	 * @param path the path of the class
	 * @return The class object of a class file located at the given
	 * {@code path}.
	 */
	public Class<?> loadClass(String path) throws Exception {
		return rootClassLoader.loadClass(path);
	}
	
	@Override
	public void close() throws Exception {
		if(reader != null) {
			reader.close();
		}
		
		rootClassLoader = null;
		reader = null;
	}
	
	private final class ModuleContentRootClassLoader extends RootClassLoader {
		
		public ModuleContentRootClassLoader(ClassLoader loader) {
			super(loader);
		}
		
		/**
		 * Gets bytes from a file located at the given {@code path} in
		 * the current {@code module}.
		 * @param path the path to the resource
		 * @return The content of the resource as an byte array.
		 */
		@Override
		protected byte[] bytes(String path) throws Exception {
			ensureReader();
			
			ByteBuffer buffer = null;
			try {
				buffer = reader.read(path).get();
				return buffer.array();
			} finally {
				if(buffer != null) {
					reader.release(buffer);
				}
			}
		}
	}
}