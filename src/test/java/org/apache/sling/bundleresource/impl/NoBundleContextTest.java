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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.Test;
import org.osgi.framework.Bundle;

public class NoBundleContextTest {

    Bundle getBundle() {
        Bundle bundle = mock(Bundle.class);
        when(bundle.getBundleContext()).thenReturn(null);

        return bundle;
    }

    /**
     * SLING-11649 - verify check for null BundleContext while registering bundle resources
     */
    @Test(expected = IllegalStateException.class)
    public void verifyIllegalStateExceptionWhenNoBundleContextIsAvailable() throws IOException {
        final Bundle bundle = getBundle();
        final PathMapping path = new PathMapping("/libs/foo", null, null);

        final BundleResourceProvider provider = new BundleResourceProvider(new BundleResourceCache(bundle), path);
        provider.registerService();
    }

}
