package org.sonatype.security.configuration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusTestCase;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.util.FileUtils;

public class SecurityConfigurationManagerTest
    extends PlexusTestCase
{
    private File PLEXUS_HOME = new File( "./target/plexus-home/" );

    private File APP_CONF = new File( PLEXUS_HOME, "conf" );

    private static final String CONFIG_FILE_NAME = "security-configuration.xml";

    @Override
    protected void customizeContainerConfiguration( ContainerConfiguration configuration )
    {
        configuration.setClassPathScanning( PlexusConstants.SCANNING_INDEX );
    }
    
    @Override
    protected void customizeContext( Context context )
    {
        super.customizeContext( context );

        context.put( "application-conf", APP_CONF.getAbsolutePath() );
    }

    @Override
    protected void setUp()
        throws Exception
    {
        // delete the plexus home dir
        FileUtils.deleteDirectory( PLEXUS_HOME );

        super.setUp();
    }

    public void testLoadEmptyDefaults()
        throws Exception
    {
        SecurityConfigurationManager config = this.lookup( SecurityConfigurationManager.class );

        Assert.assertNotNull( config );

        Assert.assertEquals( "anonymous-pass", config.getAnonymousPassword() );
        Assert.assertEquals( "anonymous-user", config.getAnonymousUsername() );

        Assert.assertEquals( false, config.isAnonymousAccessEnabled() );
        Assert.assertEquals( true, config.isEnabled() );

        List<String> realms = config.getRealms();
        Assert.assertEquals( 2, realms.size() );
        Assert.assertEquals( "MyRealmHint1", realms.get( 0 ) );
        Assert.assertEquals( "MyRealmHint2", realms.get( 1 ) );
    }

    public void testWrite()
        throws Exception
    {
        SecurityConfigurationManager config = this.lookup( SecurityConfigurationManager.class );


        config.setAnonymousAccessEnabled( true );
        config.setEnabled( false );
        config.setAnonymousPassword( "new-pass" );
        config.setAnonymousUsername( "new-user" );

        List<String> realms = new ArrayList<String>(config.getRealms());
        realms.remove( 1 );
        config.setRealms( realms );

        config.save();

        config.clearCache();
        
        Assert.assertEquals( "new-pass", config.getAnonymousPassword() );
        Assert.assertEquals( "new-user", config.getAnonymousUsername() );

        Assert.assertEquals( true, config.isAnonymousAccessEnabled() );
        Assert.assertEquals( false, config.isEnabled() );

        realms = config.getRealms();
        Assert.assertEquals( 1, realms.size() );
        Assert.assertEquals( "MyRealmHint1", realms.get( 0 ) );

    }
}
