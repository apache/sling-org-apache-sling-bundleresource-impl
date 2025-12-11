/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.bundleresource.impl;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the private static inner class BundleResourceMap inside
 * BundleResourceCache. The class is private, so we access it via reflection
 * and treat instances as plain Maps to exercise equals() and hashCode().
 */
class BundleResourceMapTest {

    /**
     * Create an instance of the private inner class BundleResourceMap using
     * reflection. The constructor used takes a single int parameter (limit).
     */
    @SuppressWarnings("unchecked")
    private <K, V> Map<K, V> createMap(int limit) throws Exception {
        Class<?> outer = BundleResourceCache.class;
        Class<?> target = null;
        for (Class<?> c : outer.getDeclaredClasses()) {
            if ("BundleResourceMap".equals(c.getSimpleName())) { // NOSONAR
                target = c;
                break;
            }
        }
        if (target == null) {
            throw new IllegalStateException("BundleResourceMap inner class not found");
        }

        Constructor<?> ctor = target.getDeclaredConstructor(int.class);
        ctor.setAccessible(true);
        Object instance = ctor.newInstance(limit);
        return (Map<K, V>) instance;
    }

    @Test
    void equalsAndHashCode_sameLimitAndContent() throws Exception {
        Map<String, String> m1 = createMap(10);
        Map<String, String> m2 = createMap(10);

        // same content
        m1.put("a", "1");
        m1.put("b", "2");

        m2.put("a", "1");
        m2.put("b", "2");

        // equals should be true and hashCodes equal
        assertTrue(m1.equals(m2), "Maps with same limit and same entries should be equal"); // NOSONAR
        assertTrue(m2.equals(m1), "Equality should be symmetric"); // NOSONAR
        assertEquals(m1.hashCode(), m2.hashCode(), "hashCode must be equal for equal maps");

        // same instance equals itself
        assertTrue(m1.equals(m1)); // NOSONAR
    }

    @Test
    void equals_differentLimits() throws Exception {
        Map<String, String> m1 = createMap(5);
        Map<String, String> m2 = createMap(10);

        // same content
        m1.put("k", "v");
        m2.put("k", "v");

        // equals should be false because limits differ even though content equals
        assertFalse(m1.equals(m2), "Maps with same entries but different limits should NOT be equal"); // NOSONAR
        assertFalse(m2.equals(m1), "Symmetry for inequality"); // NOSONAR

        // hashCodes are very likely different; at least they shouldn't be required to be equal
        // but we can assert that when equals is false, hashCodes may differ (not a strict requirement),
        // so we don't assert anything about hashCode here beyond that calling it doesn't throw.
        m1.hashCode();
        m2.hashCode();
    }

    @Test
    void equals_differentContent() throws Exception {
        Map<String, String> m1 = createMap(7);
        Map<String, String> m2 = createMap(7);

        m1.put("x", "1");
        m2.put("y", "2");

        assertFalse(m1.equals(m2), "Maps with same limit but different content should NOT be equal"); // NOSONAR
    }

    @Test
    void equals_againstOtherTypesAndNull() throws Exception {
        Map<String, String> m1 = createMap(3);
        m1.put("foo", "bar");

        assertFalse(m1.equals(null), "Should not be equal to null"); // NOSONAR
        final HashMap<Object, Object> otherMap = new HashMap<>();
        otherMap.put("foo", "bar");
        assertFalse(m1.equals(otherMap), "Should not be equal to an unrelated object"); // NOSONAR
    }
}
