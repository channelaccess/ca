package org.epics.ca.impl.types;

import java.nio.ByteBuffer;

interface ValueReader<T> {
	T readValue(ByteBuffer buffer, T value);
}