package sune.util.load;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Contains utility methods for various operations that can be done
 * with modules.
 * @author Sune
 */
public final class ModuleUtils {
	
	private static final String FILE_MODULE_INFO = "module-info.class";
	private static final byte[] COMPILED_MODULE_INFO_BEGIN = {
		(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, (byte) 0x00, (byte) 0x00,
		(byte) 0x00, (byte) 0x35, (byte) 0x00, (byte) 0x0B, (byte) 0x07, (byte) 0x00,
		(byte) 0x08, (byte) 0x01, (byte) 0x00, (byte) 0x0A, (byte) 0x53, (byte) 0x6F,
		(byte) 0x75, (byte) 0x72, (byte) 0x63, (byte) 0x65, (byte) 0x46, (byte) 0x69,
		(byte) 0x6C, (byte) 0x65, (byte) 0x01, (byte) 0x00, (byte) 0x10, (byte) 0x6D,
		(byte) 0x6F, (byte) 0x64, (byte) 0x75, (byte) 0x6C, (byte) 0x65, (byte) 0x2D,
		(byte) 0x69, (byte) 0x6E, (byte) 0x66, (byte) 0x6F, (byte) 0x2E, (byte) 0x6A,
		(byte) 0x61, (byte) 0x76, (byte) 0x61, (byte) 0x01, (byte) 0x00, (byte) 0x06,
		(byte) 0x4D, (byte) 0x6F, (byte) 0x64, (byte) 0x75, (byte) 0x6C, (byte) 0x65,
		(byte) 0x13, (byte) 0x00, (byte) 0x09, (byte) 0x13, (byte) 0x00, (byte) 0x0A,
		(byte) 0x01, (byte) 0x00, (byte) 0x05, (byte) 0x39, (byte) 0x2E, (byte) 0x30,
		(byte) 0x2E, (byte) 0x34, (byte) 0x01, (byte) 0x00, (byte) 0x0B, (byte) 0x6D,
		(byte) 0x6F, (byte) 0x64, (byte) 0x75, (byte) 0x6C, (byte) 0x65, (byte) 0x2D,
		(byte) 0x69, (byte) 0x6E, (byte) 0x66, (byte) 0x6F, (byte) 0x01, (byte) 0x00 };
	private static final byte[] COMPILED_MODULE_INFO_END = {
		(byte) 0x01, (byte) 0x00, (byte) 0x09, (byte) 0x6A, (byte) 0x61, (byte) 0x76,
		(byte) 0x61, (byte) 0x2E, (byte) 0x62, (byte) 0x61, (byte) 0x73, (byte) 0x65,
		(byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00,
		(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		(byte) 0x00, (byte) 0x02, (byte) 0x00, (byte) 0x02, (byte) 0x00, (byte) 0x00,
		(byte) 0x00, (byte) 0x02, (byte) 0x00, (byte) 0x03, (byte) 0x00, (byte) 0x04,
		(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x16, (byte) 0x00, (byte) 0x05,
		(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
		(byte) 0x00, (byte) 0x06, (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x07,
		(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		(byte) 0x00, (byte) 0x00 };
	
	private static final ModuleFinder emptyFinder = ModuleFinder.of();
	private static final ModuleLayer  parentLayer = ModuleLayer.boot();
	
	// Forbid anyone to create an instance of this class
	private ModuleUtils() {
	}
	
	private static final int getModuleInfoClassLength(String name) {
		return COMPILED_MODULE_INFO_BEGIN.length
					+ 1             // The name's size length (1 byte)
					+ name.length() // The actual name length
					+ COMPILED_MODULE_INFO_END.length;
	}
	
	private static final byte[] generateModuleInfoClass(String name) {
		int length = getModuleInfoClassLength(name);
		ByteBuffer buffer = ByteBuffer.allocate(length);
		
		buffer.put(COMPILED_MODULE_INFO_BEGIN);
		buffer.put((byte) name.length());
		buffer.put(name.getBytes());
		buffer.put(COMPILED_MODULE_INFO_END);
		buffer.flip();
		
		return Arrays.copyOf(buffer.array(), buffer.remaining());
	}
	
	private static final ZipOutputStream newZipOutputStream(Path file) throws IOException {
		return new ZipOutputStream(Files.newOutputStream(file, CREATE, WRITE));
	}
	
	private static final boolean isModuleLoaded(String name) {
		return ModuleLoader.isLoaded(name);
	}
	
	/**
	 * Defines a dummy module with the given name in the given class loader.
	 * This action requires creation of a temporary file that can be then loaded
	 * and discarded afterwards.
	 * If a module with the given name is already loaded, then nothing is done.
	 * @param name the dummy module's name
	 * @param loader the class loader
	 */
	public static final void defineDummyModule(String name, ClassLoader loader) {
		if(isModuleLoaded(name)) {
			return;
		}
		
		try {
			defineDummyModule(Files.createTempFile("dummy-module-" + name, ".jar"), name, loader);
		} catch(IOException ex) {
			throw new IllegalStateException("Unable to define a dummy module: " + name, ex);
		}
	}
	
	/**
	 * Defines a dummy module with the given name at the given path in the given
	 * class loader.
	 * This action requires creation of a temporary file that can be then loaded
	 * and discarded afterwards.
	 * If a module with the given name is already loaded, then nothing is done.
	 * @param path the path where to put the dummy module file
	 * @param name the dummy module's name
	 * @param loader the class loader
	 */
	public static final void defineDummyModule(Path path, String name, ClassLoader loader) {
		if(isModuleLoaded(name)) {
			return;
		}
		
		try {
			try(ZipOutputStream zip = newZipOutputStream(path)) {
				zip.putNextEntry(new ZipEntry(FILE_MODULE_INFO));
				zip.write(generateModuleInfoClass(name));
				zip.closeEntry();
			}
			
			defineModule(path, name, loader);
		} catch(IOException ex) {
			throw new IllegalStateException("Unable to define a dummy module: " + name, ex);
		} finally {
			try {
				Files.delete(path);
			} catch(IOException ex) {
				// Ignore
			}
		}
	}
	
	/**
	 * Defines a dummy module with the given name in the given class loader
	 * for the current location, i.e. a JAR file.
	 * If a module with the given name is already loaded, then nothing is done.
	 * @param name the dummy module's name
	 * @param loader the class loader
	 */
	public static final void defineCurrentModule(String name, ClassLoader loader) {
		if(isModuleLoaded(name)) {
			return;
		}
		
		URI uri = null;
		try {
			uri = ModuleUtils.class.getProtectionDomain().getCodeSource().getLocation().toURI();
		} catch(URISyntaxException ex) {
			// Unable to resolve, just throw an exception
			throw new IllegalStateException("Unable to obtain an URI of the current module", ex);
		}
		
		defineModule(Path.of(uri), name, loader);
	}
	
	/**
	 * Defines a module at the given path and with the given name in the given
	 * class loader.
	 * @param path the module's path
	 * @param name the module's name
	 * @param loader the class loader
	 */
	public static final void defineModule(Path path, String name, ClassLoader loader) {
		if(isModuleLoaded(name)) {
			return;
		}
		
		ModuleFinder finder = ModuleFinder.of(path);
		Configuration config = parentLayer.configuration().resolve(finder, emptyFinder, List.of(name));
		ModuleLayer layer = parentLayer.defineModulesWithOneLoader(config, loader);
		
		ModuleLoader.addModule(layer, name);
		ModuleLoader.addResolvedModule(config, name);
	}
	
	/**
	 * Gets the automatic module name of a file at the given path.
	 * @param path the module's path
	 */
	public static final String automaticModuleName(Path path) {
		return ModuleFinder.of(path).findAll().stream()
					.findFirst()
					.map(ModuleReference::descriptor)
					.map(ModuleDescriptor::name)
					.orElseGet(() -> path.getFileName().toString().replaceAll("[^A-Za-z0-9\\.]", "."));
	}
}