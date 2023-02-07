package sune.util.load;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;

import sun.misc.Unsafe;

final class Reflection {
	
	/**
	 * The Unsafe instance.
	 */
	private static final Unsafe unsafe = UnsafeInstance.get();
	
	private static Field field_override;
	
	// Forbid anyone to create an instance of this class
	private Reflection() {
	}
	
	private static final void unsafe_setFieldValue(Object instance, Field field, boolean value) {
		unsafe.putBoolean(instance, unsafe.objectFieldOffset(field), value);
	}
	
	private static final Field getField_override()
			throws NoSuchFieldException,
				   SecurityException {
		if(field_override == null) {
			field_override = AccessibleObject.class.getDeclaredField("override");
			unsafe_setFieldValue(field_override, field_override, true);
		}
		
		return field_override;
	}
	
	/**
	 * <p>
	 * Sets the {@code accessible} flag for the given object to the given boolean value.
	 * A value of {@code true} indicates that the reflected object should suppress Java
	 * language access checking when it is used. A value of {@code false} indicates that
	 * the reflected object should enforce Java language access checks.
	 * </p>
	 * 
	 * <p>
	 * Unlike the orignal {@linkplain AccessibleObject#setAccessible(boolean) setAccessible}
	 * method, this method does not fail when a module is not exported and/or opened. After
	 * calling of this method, the given object should be accessible and ready for further
	 * actions requiring accessibility.
	 * </p>
	 * 
	 * @param object the object where to set the {@code accessible} flag
	 * @param flag the new value for the {@code accessible} flag
	 */
	public static final void setAccessible(AccessibleObject object, boolean flag)
			throws NoSuchFieldException,
			       SecurityException,
			       IllegalArgumentException,
				   IllegalAccessException {
		getField_override().setBoolean(object, true);
	}
}