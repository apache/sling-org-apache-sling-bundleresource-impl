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
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.spi.resource.provider.ResourceProvider;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;

class BundleResourceWebConsolePlugin extends HttpServlet {

    private static final long serialVersionUID = 566337139719695235L;

    private static final String LABEL = "bundleresources";

    // runtime-only holders — transient so servlet serialization won't try to persist them
    private final transient AtomicReference<ServiceRegistration<Servlet>> serviceRegistration = new AtomicReference<>();

    @SuppressWarnings("rawtypes")
    private final transient AtomicReference<ServiceTracker<ResourceProvider, ResourceProvider>> providerTracker =
            new AtomicReference<>();

    // thread-safe list so ServiceTracker callbacks can add/remove while doGet iterates
    private final transient List<BundleResourceProvider> provider = new CopyOnWriteArrayList<>();

    // --------- setup and shutdown

    private static BundleResourceWebConsolePlugin instance;

    static void initPlugin(BundleContext context) {
        if (instance == null) {
            BundleResourceWebConsolePlugin tmp = new BundleResourceWebConsolePlugin();
            tmp.activate(context);
            instance = tmp;
        }
    }

    static void destroyPlugin() {
        if (instance != null) {
            try {
                instance.deactivate();
            } finally {
                instance = null;
            }
        }
    }

    // private constructor to force using static setup and shutdown
    private BundleResourceWebConsolePlugin() {}

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse res)
            throws ServletException, IOException {
        PrintWriter pw = res.getWriter();

        pw.println("<table class='content' cellpadding='0' cellspacing='0' width='100%'>");

        pw.println("<tr class='content'>");
        pw.println("<th colspan='2' class='content container'>Bundle Resource Provider</th>");
        pw.println("</tr>");

        BundleResourceProvider[] brp = provider.toArray(new BundleResourceProvider[provider.size()]);
        for (BundleResourceProvider bundleResourceProvider : brp) {

            BundleResourceCache cache = bundleResourceProvider.getBundleResourceCache();
            PathMapping path = bundleResourceProvider.getMappedPath();

            pw.println("<tr class='content'>");

            pw.println("<td class='content'>");
            pw.println(cache.getBundle().getBundleId());
            pw.println("</td>");

            pw.println("<td class='content'>");
            pw.println(getName(cache.getBundle()));
            pw.println("</td>");

            pw.println("</tr>");

            pw.println("<tr class='content'>");
            pw.println("<td class='content'>&nbsp;</td>");

            pw.println("<td class='content'>");

            pw.println("<table>");

            pw.println("<tr>");
            pw.println("<td>Mapping</td>");
            pw.println("<td>");
            pw.print(path.getResourceRoot());
            if (path.getEntryRoot() != null) {
                pw.print(" ==> ");
                pw.print(path.getEntryRoot());
            }
            pw.println("</td>");
            pw.println("</tr>");

            pw.println("<tr>");
            pw.println("<td>Entry Cache</td>");
            pw.printf("<td>Size: %d, Limit: %d</td>%n", cache.getEntryCacheSize(), cache.getEntryCacheMaxSize());
            pw.println("</tr>");

            pw.println("<tr>");
            pw.println("<td>List Cache</td>");
            pw.printf("<td>Size: %d, Limit: %d</td>%n", cache.getListCacheSize(), cache.getListCacheMaxSize());
            pw.println("</tr>");

            pw.println("</table>");

            pw.println("</td>");
            pw.println("</tr>");
        }

        pw.println("</table>");
    }

    @SuppressWarnings("rawtypes")
    public void activate(BundleContext context) {
        ServiceTracker<ResourceProvider, ResourceProvider> tracker =
                new ServiceTracker<ResourceProvider, ResourceProvider>(
                        context, ResourceProvider.class.getName(), null) {

                    @Override
                    public ResourceProvider addingService(final ServiceReference<ResourceProvider> reference) {
                        ResourceProvider service = null;
                        if (reference.getProperty(BundleResourceProvider.PROP_BUNDLE) != null) {
                            service = super.addingService(reference);
                            if (service instanceof BundleResourceProvider brpService) {
                                provider.add(brpService);
                            }
                        }
                        return service;
                    }

                    @Override
                    public void removedService(
                            final ServiceReference<ResourceProvider> reference, final ResourceProvider service) {
                        if (service instanceof BundleResourceProvider) {
                            provider.remove(service);
                        }
                        super.removedService(reference, service);
                    }
                };
        providerTracker.set(tracker);
        tracker.open();

        Dictionary<String, Object> props = new Hashtable<>(); // NOSONAR
        props.put(Constants.SERVICE_DESCRIPTION, "Web Console Plugin for Bundle Resource Providers");
        props.put(Constants.SERVICE_VENDOR, "The Apache Software Foundation");
        props.put("felix.webconsole.label", LABEL);
        props.put("felix.webconsole.title", "Bundle Resource Provider");
        props.put("felix.webconsole.category", "Sling");

        serviceRegistration.set(context.registerService(Servlet.class, this, props));
    }

    public void deactivate() {
        ServiceRegistration<Servlet> sr = serviceRegistration.getAndSet(null);
        if (sr != null) {
            try {
                sr.unregister();
            } catch (IllegalStateException ise) {
                // ignore if already unregistered
            }
        }

        @SuppressWarnings("rawtypes")
        ServiceTracker<ResourceProvider, ResourceProvider> t = providerTracker.getAndSet(null);
        if (t != null) {
            t.close();
        }
    }

    private String getName(final Bundle bundle) {
        String name = bundle.getHeaders().get(Constants.BUNDLE_NAME);
        if (name == null) {
            name = bundle.getSymbolicName();
            if (name == null) {
                name = bundle.getLocation();
            }
        }
        return name;
    }
}
