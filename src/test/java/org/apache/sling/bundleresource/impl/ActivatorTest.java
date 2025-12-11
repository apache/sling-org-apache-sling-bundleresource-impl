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

import java.util.Hashtable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActivatorTest {

    private Bundle bundle;

    @BeforeEach
    void setUp() {
        bundle = mock(Bundle.class);
        // Return an empty headers dictionary so that addBundleResourceProvider
        // finds no prefixes and returns early without performing registrations.
        when(bundle.getHeaders()).thenReturn(new Hashtable<>());
    }

    @Test
    void bundleChanged_handlesStartedEventWithoutThrowing() {
        final Activator activator = new Activator();
        final BundleEvent startedEvent = new BundleEvent(BundleEvent.STARTED, bundle);

        // Verify the STARTED branch runs (and does not throw).
        assertDoesNotThrow(() -> activator.bundleChanged(startedEvent));
    }

    @Test
    void bundleChanged_handlesStoppedEventWithoutThrowing() {
        final Activator activator = new Activator();
        final BundleEvent stoppedEvent = new BundleEvent(BundleEvent.STOPPED, bundle);

        // Verify the STOPPED branch runs (and does not throw).
        assertDoesNotThrow(() -> activator.bundleChanged(stoppedEvent));
    }

    @Test
    void bundleChanged_handlesOtherEventWithoutThrowing() {
        final Activator activator = new Activator();
        final BundleEvent stoppedEvent = new BundleEvent(BundleEvent.RESOLVED, bundle);

        // Verify nothing happens (and does not throw).
        assertDoesNotThrow(() -> activator.bundleChanged(stoppedEvent));
    }
}
