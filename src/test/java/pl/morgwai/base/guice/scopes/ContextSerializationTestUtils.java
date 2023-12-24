// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.guice.scopes;

import java.io.*;

import com.google.inject.Key;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;



/** Some helper functions for testing {@code Context} serialization. */
public interface ContextSerializationTestUtils {



	/** Serializes and then de-serializes object {@code toSerialize}. */
	static Object serialize(Object toSerialize) throws IOException {
		final var serializedBytesOutput = new ByteArrayOutputStream(500);
		try (
			serializedBytesOutput;
			final var serializedObjects = new ObjectOutputStream(serializedBytesOutput);
		) {
			serializedObjects.writeObject(toSerialize);
		}
		try (
			final var serializedBytesInput =
					new ByteArrayInputStream(serializedBytesOutput.toByteArray());
			final var serializedObjects = new ObjectInputStream(serializedBytesInput);
		) {
			return serializedObjects.readObject();
		} catch (ClassNotFoundException neverHappens) {
			throw new RuntimeException(neverHappens);
		}
	}



	class NonSerializableObject {
		public final String value;
		public NonSerializableObject(String value) { this.value = value; }
	}



	/**
	 * Stores some {@link Serializable} and {@link NonSerializableObject non-Serializable} objects
	 * into {@code ctx}, {@link #serialize(Object) serializes} it and then verifies if the stored
	 * objects were retained correctly throughout the process.
	 */
	static void testContextSerialization(InjectionContext ctx) throws IOException {
		final var scopedString = "scopedString";
		ctx.produceIfAbsent(Key.get(String.class), () -> scopedString);
		ctx.produceIfAbsent(
			Key.get(NonSerializableObject.class),
			() -> new NonSerializableObject(scopedString)
		);
		final var deserializedCtx = (InjectionContext) serialize(ctx);
		assertEquals(
			"serializable scoped object should be retained throughout ctx (de)serialization",
			scopedString,
			deserializedCtx.produceIfAbsent(Key.get(String.class), () -> "another")
		);
		assertNotEquals(
			"non-serializable object should NOT be retained throughout ctx (de)serialization",
			scopedString,
			deserializedCtx.produceIfAbsent(
				Key.get(NonSerializableObject.class),
				() -> new NonSerializableObject("another")
			).value
		);
	}
}
