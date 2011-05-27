/**
 * Copyright (c) 2008-2011 Sonatype, Inc.
 *
 * All rights reserved. Includes the third-party code listed at http://www.sonatype.com/products/nexus/attributions.
 * Sonatype and Sonatype Nexus are trademarks of Sonatype, Inc. Apache Maven is a trademark of the Apache Foundation.
 * M2Eclipse is a trademark of the Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package com.sonatype.nexus.plugin.obr.test.nxcm1573;

import org.junit.Assert;

import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.restlet.data.MediaType;
import org.sonatype.nexus.configuration.model.CRepository;
import org.sonatype.nexus.rest.model.RepositoryProxyResource;
import org.sonatype.nexus.rest.model.RepositoryResourceRemoteStorage;
import org.sonatype.nexus.test.utils.RepositoryMessageUtil;
import org.testng.annotations.Test;

import com.sonatype.nexus.plugin.obr.test.AbstractOBRIntegrationTest;

public class NXCM1573UpdateOBRProxyUrlIT
    extends AbstractOBRIntegrationTest
{
    RepositoryMessageUtil repoMessageUtil;

    public NXCM1573UpdateOBRProxyUrlIT()
        throws Exception
    {
        repoMessageUtil = new RepositoryMessageUtil( this, getJsonXStream(), MediaType.APPLICATION_JSON );
    }

    @Test
    public void validateOBRProxyUrlChanges()
        throws Exception
    {
        RepositoryProxyResource proxyRepo = new RepositoryProxyResource();
        proxyRepo.setRepoType( "proxy" );
        proxyRepo.setId( "obr" );
        proxyRepo.setName( "obr" );
        proxyRepo.setBrowseable( true );
        proxyRepo.setIndexable( false );
        proxyRepo.setNotFoundCacheTTL( 1440 );
        proxyRepo.setArtifactMaxAge( -1 );
        proxyRepo.setMetadataMaxAge( 1440 );
        proxyRepo.setRepoPolicy( "RELEASE" );
        proxyRepo.setProvider( "obr-proxy" );
        proxyRepo.setProviderRole( "org.sonatype.nexus.proxy.repository.Repository" );
        proxyRepo.setOverrideLocalStorageUrl( null );
        proxyRepo.setDefaultLocalStorageUrl( null );
        proxyRepo.setDownloadRemoteIndexes( false );
        proxyRepo.setExposed( true );
        proxyRepo.setChecksumPolicy( "WARN" );

        RepositoryResourceRemoteStorage remoteStorage = new RepositoryResourceRemoteStorage();
        remoteStorage.setRemoteStorageUrl( "http://sigil.codecauldron.org/spring-external.obr" );
        remoteStorage.setAuthentication( null );
        remoteStorage.setConnectionSettings( null );
        remoteStorage.setHttpProxySettings( null );

        proxyRepo.setRemoteStorage( remoteStorage );

        //create the repo
        repoMessageUtil.createRepository( proxyRepo, false );

        //check for equality here
        assertObrPath( getNexusConfigUtil().getRepo( "obr" ), "http://sigil.codecauldron.org/", "/spring-external.obr" );

        //note internal opposed to external
        proxyRepo.getRemoteStorage().setRemoteStorageUrl( "http://sigil.codecauldron.org/spring-internal.obr" );

        //update the repo
        repoMessageUtil.updateRepo( proxyRepo, false );

        //check again for equality here
        assertObrPath( getNexusConfigUtil().getRepo( "obr" ), "http://sigil.codecauldron.org/", "/spring-internal.obr" );

        //note sigil2
        proxyRepo.getRemoteStorage().setRemoteStorageUrl( "http://sigil2.codecauldron.org/spring-external.obr" );

        //update the repo
        repoMessageUtil.updateRepo( proxyRepo, false );

        //check again for equality here
        assertObrPath( getNexusConfigUtil().getRepo( "obr" ), "http://sigil2.codecauldron.org/", "/spring-external.obr" );

        //note sigil3 and external -> internal
        proxyRepo.getRemoteStorage().setRemoteStorageUrl( "http://sigil3.codecauldron.org/spring-internal.obr" );

        //update the repo
        repoMessageUtil.updateRepo( proxyRepo, false );

        //check again for equality here
        assertObrPath( getNexusConfigUtil().getRepo( "obr" ), "http://sigil3.codecauldron.org/", "/spring-internal.obr" );
    }

    private void assertObrPath( CRepository repository, String remoteUrlShouldBe, String obrPathShouldBe )
    {
        Assert.assertEquals( remoteUrlShouldBe, repository.getRemoteStorage().getUrl() );

        Xpp3Dom dom = ( Xpp3Dom ) repository.getExternalConfiguration();

        Xpp3Dom child = dom.getChild( "obrPath" );

        Assert.assertEquals( obrPathShouldBe, child.getValue() );
    }
}
