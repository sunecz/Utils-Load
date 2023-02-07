package sune.util.load;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

/**
 * <p>
 * This class is used to access the Unsafe instance.
 * </p>
 * 
 * <p>
 * Note that this class throws an {@linkplain IllegalStateException}
 * when the Unsafe instance cannot be obtained. This can happen on systems
 * where the sun.misc.Unsafe class was disabled/removed, possibly causing
 * the application to crash, however a simple try-catch block can be used
 * to ignore the exception.
 * </p>
 * 
 * @author Sune
 * @see sun.misc.Unsafe
 */
final class UnsafeInstance {
	
	/**
	 * The Unsafe instance, initialized on the first class access.
	 */
	private static final Unsafe unsafe;
	
	static {
		Unsafe _unsafe = null;
		
		try {
			Class<?> clazz = Class.forName("sun.misc.Unsafe");
			Field field = clazz.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			_unsafe = (Unsafe) field.get(null);
		} catch(Exception ex) {
			throw new IllegalStateException("Unable to obtain the Unsafe instance", ex);
		}
		
		unsafe = _unsafe;
	}
	
	// Forbid anyone to create an instance of this class
	private UnsafeInstance() {
	}
	
	/**
	 * Gets the Unsafe instance.
	 * @return The Unsafe instance.
	 */
	public static final Unsafe get() {
		return unsafe;
	}
}