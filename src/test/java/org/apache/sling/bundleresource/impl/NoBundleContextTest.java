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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Dictionary;

import org.apache.sling.spi.resource.provider.ResourceProvider;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * SLING-11649 - verify check for null BundleContext while registering bundle resources
 */
public class NoBundleContextTest {

    @Test
    public void verifyIllegalStateExceptionWhenNoBundleContextIsAvailable() throws IOException {
        final Bundle bundle = mock(Bundle.class);
        when(bundle.getBundleContext()).thenReturn(null);
        final PathMapping path = new PathMapping("/libs/foo", null, null);

        final BundleResourceProvider provider = new BundleResourceProvider(new BundleResourceCache(bundle), path);
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            provider.registerService();
        });
        assertTrue(exception.getMessage().contains("No BundleContext was found"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void verifyNoIllegalStateExceptionWhenBundleContextIsAvailable() throws IOException {
        final Bundle bundle = mock(Bundle.class);
        final BundleContext bc = mock(BundleContext.class);
        when(bundle.getBundleContext()).thenReturn(bc);
        @SuppressWarnings("rawtypes")
        final ServiceRegistration<ResourceProvider> svcReg = mock(ServiceRegistration.class);
        @SuppressWarnings("rawtypes")
        final ServiceReference<ResourceProvider> svcRef = mock(ServiceReference.class);
        when(svcRef.getProperty(Constants.SERVICE_ID)).thenReturn(123L);
        when(svcReg.getReference()).thenReturn(svcRef);

        final PathMapping path = new PathMapping("/libs/foo", null, null);
        final BundleResourceProvider provider = new BundleResourceProvider(new BundleResourceCache(bundle), path);
        when(bc.registerService(eq(ResourceProvider.class), eq(provider), any(Dictionary.class))).thenReturn(svcReg);
        assertEquals(123L, provider.registerService());
    }

}
