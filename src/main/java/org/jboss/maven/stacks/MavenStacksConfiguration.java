/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.maven.stacks;

import org.apache.maven.settings.Proxy;
import org.jboss.jdf.stacks.client.DefaultStacksClientConfiguration;
import org.jboss.jdf.stacks.client.StacksClientConfiguration;

/**
 * @author <a href="mailto:benevides@redhat.com">Rafael Benevides</a>
 *
 */
public class MavenStacksConfiguration extends DefaultStacksClientConfiguration implements StacksClientConfiguration {

    private Proxy proxy;

    /**
     * @param proxy
     */
    public MavenStacksConfiguration(Proxy proxy) {
        this.proxy = proxy;
    }
    
    /* (non-Javadoc)
     * @see org.jboss.jdf.stacks.client.DefaultStacksClientConfiguration#getProxyHost()
     */
    @Override
    public String getProxyHost() {
        return proxy == null?null:proxy.getHost();
    }
    
    /* (non-Javadoc)
     * @see org.jboss.jdf.stacks.client.DefaultStacksClientConfiguration#getProxyPort()
     */
    @Override
    public int getProxyPort() {
        return proxy == null?null:proxy.getPort();
    }
    
    /* (non-Javadoc)
     * @see org.jboss.jdf.stacks.client.DefaultStacksClientConfiguration#getProxyUser()
     */
    @Override
    public String getProxyUser() {
        return proxy == null?null:proxy.getUsername();
    }
    
    /* (non-Javadoc)
     * @see org.jboss.jdf.stacks.client.DefaultStacksClientConfiguration#getProxyPassword()
     */
    @Override
    public String getProxyPassword() {
        return proxy == null?null:proxy.getPassword();
    }

    
}
