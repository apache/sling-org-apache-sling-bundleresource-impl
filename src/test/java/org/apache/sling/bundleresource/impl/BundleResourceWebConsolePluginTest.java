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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Dictionary;

import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    @SuppressWarnings("unchecked")
    @Test
    void testActivateDoGetDeactivate() throws Exception {
        // Arrange: mock BundleContext and ServiceRegistration
        BundleContext ctx = mock(BundleContext.class);
        ServiceRegistration<Servlet> reg = mock(ServiceRegistration.class);

        // registerService should return our mock registration
        when(ctx.registerService(eq(Servlet.class), any(Servlet.class), any(Dictionary.class)))
                .thenReturn(reg);

        // Act: initialize plugin (this creates and activates the plugin instance)
        BundleResourceWebConsolePlugin.initPlugin(ctx);

        // Reflectively fetch the private static INSTANCE so we can call doGet on it
        Field instanceField = BundleResourceWebConsolePlugin.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        BundleResourceWebConsolePlugin plugin = (BundleResourceWebConsolePlugin) instanceField.get(null);
        // ensure we have a plugin instance
        assertNotNull(plugin, "Plugin instance should have been created");

        // calling init again should do no additional work
        BundleResourceWebConsolePlugin.initPlugin(ctx);
        BundleResourceWebConsolePlugin plugin2 = (BundleResourceWebConsolePlugin) instanceField.get(null);
        assertSame(plugin2, plugin);

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
    }
}
