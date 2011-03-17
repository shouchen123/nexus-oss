/**
 * Copyright (c) 2008 Sonatype, Inc. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package org.sonatype.security.web;

import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;

import org.apache.shiro.config.Ini;
import org.apache.shiro.mgt.RealmSecurityManager;
import org.apache.shiro.web.filter.mgt.FilterChainManager;
import org.apache.shiro.web.filter.mgt.PathMatchingFilterChainResolver;
import org.apache.shiro.web.mgt.WebSecurityManager;
import org.apache.shiro.web.servlet.IniShiroFilter;
import org.sonatype.security.SecuritySystem;
import org.sonatype.security.configuration.SecurityConfigurationManager;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;

/**
 * Extension of ShiroFilter that uses SISU (with Plexus if PlexusConstants.PLEXUS_KEY in context ) lookup to get the
 * configuration, if any role param is given. Otherwise it fallbacks to the standard stuff from JSecurityFilter.
 * 
 * @author cstamas
 */
public class ShiroSecurityFilter
    extends IniShiroFilter
{
    private SecuritySystem securitySystem;

    private WebSecurityManager securityManager;

    public static final String INJECTORY_KEY = "injector.key";

    @Override
    protected Map<String, ?> applySecurityManager( Ini ini )
    {
        // use this to customize loading of the securityManager.
        // we are just going to use the component from SecuritySystem, so we do not need it.

        // bean map used to load custom filters
        // TODO: ( at least to think about ) we could inject filters using the container
        return null;
    }

    @Override
    public WebSecurityManager getSecurityManager()
    {
        return securityManager;
    }

    @Override
    protected void configure()
        throws Exception
    {
        SecurityConfigurationManager cfg = getSecurityConfigurationManager();
        cfg.setSecurityManager( "web" );

        // start up security
        this.getSecuritySystem().start();
        this.securityManager = getWebSecurityManager();

        // call super
        super.configure();

        FilterChainManager filterChainManager =
            ( (PathMatchingFilterChainResolver) super.getFilterChainResolver() ).getFilterChainManager();

        ProtectedPathManager protectedPathManager = getProtectedPathManager();
        // this cannot be injected as long as the configuration comes from the servlet config.
        // TODO: push the ini config in its own file.
        if ( FilterChainManagerAware.class.isInstance( protectedPathManager ) )
        {
            ( (FilterChainManagerAware) protectedPathManager ).setFilterChainManager( filterChainManager );
        }
    }

    private SecurityConfigurationManager getSecurityConfigurationManager()
        throws Exception
    {
        return getInstance( SecurityConfigurationManager.class );
    }

    private WebSecurityManager getWebSecurityManager()
        throws Exception
    {
        return (WebSecurityManager) getInstance( RealmSecurityManager.class, "web" );
    }

    private ProtectedPathManager getProtectedPathManager()
        throws Exception
    {
        return (ProtectedPathManager) getInstance( ProtectedPathManager.class );
    }

    private SecuritySystem getSecuritySystem()
    {
        // lazy load it using the container
        if ( this.securitySystem == null )
        {
            try
            {
                this.securitySystem = getInstance( SecuritySystem.class );
            }
            catch ( Exception e )
            {
                throw new IllegalStateException( "Failed to load the Security System.", e );
            }
        }

        return this.securitySystem;
    }

    protected <T> T getInstance( Class<T> clazz )
        throws Exception
    {
        return getInstance( clazz, null );
    }

    protected <T> T getInstance( Class<T> clazz, String name )
        throws Exception
    {
        if ( name == null )
        {
            return getInjector().getInstance( Key.get( clazz ) );
        }
        else
        {
            return getInjector().getInstance( Key.get( clazz, Names.named( name ) ) );
        }
    }

    protected Injector getInjector()
    {
        return (Injector) getServletContext().getAttribute( INJECTORY_KEY );
    }

    @Override
    protected boolean shouldNotFilter( ServletRequest request )
        throws ServletException
    {
        return !this.getSecuritySystem().isSecurityEnabled();
    }
}
