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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.spi.resource.provider.ResourceProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Basic unit tests for BundleResourceWebConsolePlugin to exercise activation, simple doGet rendering,
 * and deactivation/unregister behavior.
 *
 * - Calls initPlugin(BundleContext) with a mocked BundleContext that returns a mocked ServiceRegistration.
 * - Reflectively obtains the created plugin instance and invokes doGet to capture the generated HTML.
 * - Calls destroyPlugin() and verifies the ServiceRegistration.unregister() was invoked.
 */
class BundleResourceWebConsolePluginTest {

    @Test
    void testDoGetWithCaughtIOException() throws Exception {
        testPlugin((ctx, reg, plugin) -> {
            plugin = Mockito.spy(plugin);
            Mockito.doNothing().when(plugin).log(anyString(), any(Exception.class));

            // Prepare mocks for servlet invocation and capture output
            HttpServletRequest req = mock(HttpServletRequest.class);
            HttpServletResponse resp = mock(HttpServletResponse.class);
            Mockito.doThrow(IOException.class).when(resp).getWriter();

            plugin.doGet(req, resp);

            // verify the error log message was invoked
            verify(plugin, times(1)).log(anyString(), any(Exception.class));
        });
    }

    @Test
    void testActivateDoGetDeactivate() throws Exception {
        testPlugin((ctx, reg, plugin) -> {
            // calling init again should do no additional work
            BundleResourceWebConsolePlugin.initPlugin(ctx);
            BundleResourceWebConsolePlugin plugin2 = getInstanceField();
            assertSame(plugin2, plugin);

            mockBundleResourceProvider(ctx, plugin);

            // Prepare mocks for servlet invocation and capture output
            HttpServletRequest req = mock(HttpServletRequest.class);
            HttpServletResponse resp = mock(HttpServletResponse.class);

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            when(resp.getWriter()).thenReturn(pw);

            // Invoke doGet which should render an HTML table (even with no providers)
            plugin.doGet(req, resp);
            pw.flush();
            String output = sw.toString();

            // Assert: basic expected content is present
            assertTrue(output.contains("Bundle Resource Provider"), "Output should contain the webconsole title");
            assertTrue(output.contains("<table"), "Output should contain a table element");

            // Clean up: destroy plugin (should unregister the service)
            BundleResourceWebConsolePlugin.destroyPlugin();
            // calling a second time should do no additional work
            BundleResourceWebConsolePlugin.destroyPlugin();

            // Verify unregister was called on the registration
            verify(reg, times(1)).unregister();
        });
    }

    @Test
    void testDeactivateTwice() throws Exception {
        testPlugin((ctx, reg, plugin) -> {
            plugin.deactivate();
            // Verify unregister was called on the registration
            verify(reg, times(1)).unregister();

            // call deactivate again should do no addiional work
            Mockito.reset((Object) reg);
            plugin.deactivate();
            // Verify unregister was called on the registration
            verify(reg, times(0)).unregister();
        });
    }

    @Test
    void testDeactivateWithServiceRegAlreadyUnregistered() throws Exception {
        testPlugin((ctx, reg, plugin) -> {
            Mockito.doThrow(IllegalStateException.class).when(reg).unregister();
            plugin.deactivate();
            // Verify unregister was called on the registration
            verify(reg, times(1)).unregister();
        });
    }

    /**
     * Simulate a BundleResourceProvider service being registered and added to the service tracker
     */
    private void mockBundleResourceProvider(BundleContext ctx, BundleResourceWebConsolePlugin plugin)
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        @SuppressWarnings("rawtypes")
        final ServiceTracker<ResourceProvider, ResourceProvider> providerTracker = getProviderTrackerField(plugin);
        assertNotNull(providerTracker);
        final BundleResourceProvider mockResourceProvider1 = Mockito.mock(BundleResourceProvider.class);
        BundleResourceCache cache = Mockito.mock(BundleResourceCache.class);
        Bundle mockBundle = Mockito.mock(Bundle.class);
        Dictionary<String, Object> headers = new Hashtable<>(Map.of(Constants.BUNDLE_NAME, "test.bundle1"));
        Mockito.doReturn(headers).when(mockBundle).getHeaders();
        Mockito.doReturn(mockBundle).when(cache).getBundle();
        Mockito.doReturn(cache).when(mockResourceProvider1).getBundleResourceCache();

        PathMapping pathMapping = Mockito.mock(PathMapping.class);
        Mockito.doReturn(pathMapping).when(mockResourceProvider1).getMappedPath();

        @SuppressWarnings({"unchecked", "rawtypes"})
        ServiceReference<ResourceProvider> ref1 = mock(ServiceReference.class);
        Mockito.doReturn(1L).when(ref1).getProperty(BundleResourceProvider.PROP_BUNDLE);
        Mockito.doReturn(mockResourceProvider1).when(ctx).getService(ref1);
        providerTracker.addingService(ref1);
    }

    /**
     * Reflectively clear the private static instance so we can start from scratch
     */
    private void clearInstanceField()
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        Field instanceField = BundleResourceWebConsolePlugin.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    /**
     * Reflectively fetch the private static instance so we can call doGet on it
     */
    private BundleResourceWebConsolePlugin getInstanceField()
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        Field instanceField = BundleResourceWebConsolePlugin.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        return (BundleResourceWebConsolePlugin) instanceField.get(null);
    }

    /**
     * Reflectively fetch the private static instance so we can call doGet on it
     */
    @SuppressWarnings("rawtypes")
    private ServiceTracker<ResourceProvider, ResourceProvider> getProviderTrackerField(
            BundleResourceWebConsolePlugin plugin)
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        Field instanceField = BundleResourceWebConsolePlugin.class.getDeclaredField("providerTracker");
        instanceField.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<ServiceTracker<ResourceProvider, ResourceProvider>> ref =
                (AtomicReference<ServiceTracker<ResourceProvider, ResourceProvider>>) instanceField.get(plugin);
        return ref.get();
    }

    @SuppressWarnings("unchecked")
    protected void testPlugin(PluginWorker worker) throws Exception {
        BundleContext ctx = mock(BundleContext.class);
        ServiceRegistration<Servlet> reg = mock(ServiceRegistration.class);

        // registerService should return our mock registration
        when(ctx.registerService(eq(Servlet.class), any(Servlet.class), any(Dictionary.class)))
                .thenReturn(reg);

        // make sure the is nothing left in this field
        clearInstanceField();

        // Act: initialize plugin (this creates and activates the plugin instance)
        BundleResourceWebConsolePlugin.initPlugin(ctx);

        // Reflectively fetch the private static instance so we can call doGet on it
        BundleResourceWebConsolePlugin plugin = getInstanceField();
        // ensure we have a plugin instance
        assertNotNull(plugin, "Plugin instance should have been created");

        worker.doWork(ctx, reg, plugin);
    }

    public static interface PluginWorker {
        public void doWork(BundleContext ctx, ServiceRegistration<Servlet> reg, BundleResourceWebConsolePlugin plugin)
                throws Exception;
    }
}
