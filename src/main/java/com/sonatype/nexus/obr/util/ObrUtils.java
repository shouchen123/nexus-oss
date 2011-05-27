/**
 * Copyright (c) 2008-2011 Sonatype, Inc.
 *
 * All rights reserved. Includes the third-party code listed at http://www.sonatype.com/products/nexus/attributions.
 * Sonatype and Sonatype Nexus are trademarks of Sonatype, Inc. Apache Maven is a trademark of the Apache Foundation.
 * M2Eclipse is a trademark of the Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package com.sonatype.nexus.obr.util;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.osgi.impl.bundle.obr.resource.ResourceImpl.UrlTransformer;
import org.osgi.service.obr.Resource;
import org.sonatype.nexus.proxy.AccessDeniedException;
import org.sonatype.nexus.proxy.IllegalOperationException;
import org.sonatype.nexus.proxy.ItemNotFoundException;
import org.sonatype.nexus.proxy.ResourceStoreRequest;
import org.sonatype.nexus.proxy.StorageException;
import org.sonatype.nexus.proxy.item.ContentLocator;
import org.sonatype.nexus.proxy.item.DefaultStorageCollectionItem;
import org.sonatype.nexus.proxy.item.DefaultStorageFileItem;
import org.sonatype.nexus.proxy.item.RepositoryItemUid;
import org.sonatype.nexus.proxy.item.StorageCollectionItem;
import org.sonatype.nexus.proxy.item.StorageFileItem;
import org.sonatype.nexus.proxy.item.StorageItem;
import org.sonatype.nexus.proxy.item.StringContentLocator;
import org.sonatype.nexus.proxy.repository.Repository;
import org.sonatype.nexus.proxy.storage.local.LocalRepositoryStorage;
import org.sonatype.nexus.proxy.walker.AbstractFileWalkerProcessor;
import org.sonatype.nexus.proxy.walker.DefaultWalkerContext;
import org.sonatype.nexus.proxy.walker.Walker;
import org.sonatype.nexus.proxy.walker.WalkerContext;
import org.sonatype.nexus.proxy.walker.WalkerException;

import com.sonatype.nexus.obr.metadata.ManagedObrSite;
import com.sonatype.nexus.obr.metadata.ObrMetadataSource;
import com.sonatype.nexus.obr.metadata.ObrResourceReader;
import com.sonatype.nexus.obr.metadata.ObrResourceWriter;
import com.sonatype.nexus.obr.shadow.ObrShadowRepository;

/**
 * Various utility methods specific to handling OBRs.
 */
public final class ObrUtils
{
    /**
     * Standard location of general metadata inside a Nexus repository.
     */
    private static final String META_PATH = "/.meta";

    /**
     * Standard location of OBR metadata inside a Nexus repository.
     */
    private static final String OBR_PATH = META_PATH + "/obr.xml";

    /**
     * Ignore files with paths like "/.foo" as well as known text extensions.
     */
    private static final Pattern IGNORE_ITEM_PATH_PATTERN =
        Pattern.compile( "(.*?/\\.[^/.].*)|(.*?\\.(txt|pom|xml|sha1|md5|asc))" );

    /**
     * Separate OBR URL into a hosting site and a path to the OBR metadata.
     */
    private static final Pattern OBR_SITE_AND_PATH_PATTERN =
        Pattern.compile( "(.*?)([^/]+((\\.(xml|zip|obr))|([?&=][^/]*)+))" );

    private ObrUtils()
    {
        // utility class, no instances
    }

    /**
     * Tests whether we should consider this item as an OSGi bundle.
     * 
     * @param item the item
     * @return true if it might be an OSGi bundle, otherwise false
     */
    public static boolean acceptItem( StorageItem item )
    {
        if ( null == item || item instanceof StorageCollectionItem )
        {
            return false; // ignore directories
        }

        RepositoryItemUid uid = item.getRepositoryItemUid();

        if ( IGNORE_ITEM_PATH_PATTERN.matcher( uid.getPath() ).matches() )
        {
            return false; // ignore text files
        }

        if ( uid.getRepository() instanceof ObrShadowRepository )
        {
            return false; // ignore shadowed items as we already know about them
        }

        return true;
    }

    /**
     * Tests whether the given resource request is for OBR metadata.
     * 
     * @param request the resource request
     * @return true if this request is for OBR metadata, otherwise false
     */
    public static boolean isObrMetadataRequest( ResourceStoreRequest request )
    {
        return OBR_PATH.equals( request.getRequestPath() );
    }

    /**
     * Creates a new UID that points to the OBR metadata for the given repository.
     * 
     * @param repository the Nexus repository
     * @return a new UID pointing to the OBR metadata
     */
    public static RepositoryItemUid createObrUid( Repository repository )
    {
        return repository.createUid( OBR_PATH );
    }

    /**
     * Splits the given URL into a site and OBR metadata path by using simple heuristics.
     * 
     * @param url the local or remote URL
     * @return an array containing the site and the OBR metadata path
     */
    public static String[] splitObrSiteAndPath( String url )
    {
        return splitObrSiteAndPath( url, true );
    }

    public static String[] splitObrSiteAndPath( String url, boolean useDefaultIfNotSet )
    {
        // is this a Nexus managed OBR?
        int i = url.lastIndexOf( OBR_PATH );
        if ( i >= 0 )
        {
            return new String[] { url.substring( 0, i + 1 ), OBR_PATH };
        }

        // don't bother testing URLs that point to local directories
        if ( !url.startsWith( "file:" ) || !new File( url ).isDirectory() )
        {
            // attempt to find the split between site and metadata path
            Matcher matcher = OBR_SITE_AND_PATH_PATTERN.matcher( url );
            if ( matcher.matches() )
            {
                return new String[] { matcher.group( 1 ), '/' + matcher.group( 2 ) };
            }
        }

        if ( useDefaultIfNotSet )
        {
        // assume OBR metadata is in repository.xml
        return new String[] { url, "/repository.xml" };
    }
        else
        {
            return new String[] { url, null };
        }
    }

    /**
     * Retrieves the OBR metadata from the given containing Nexus repository.
     * 
     * @param repository the Nexus repository
     * @return the file item containing OBR metadata
     * @throws StorageException
     */
    public static StorageFileItem retrieveObrItem( Repository repository )
        throws StorageException
    {
        ResourceStoreRequest request = new ResourceStoreRequest( OBR_PATH );

        try
        {
            StorageItem item = repository.retrieveItem( request );
            if ( item instanceof StorageFileItem )
            {
                return (StorageFileItem) item;
            }
        }
        catch ( ItemNotFoundException e )
        {
            // OBR metadata is missing, so drop through and provide blank repository
        }
        catch ( IllegalOperationException e )
        {
            throw new StorageException( e );
        }
        catch ( AccessDeniedException e )
        {
            throw new StorageException( e );
        }

        return new DefaultStorageFileItem( repository, request, true, true, new StringContentLocator( "<repository/>" ) );
    }

    /**
     * Retrieves the given item from its local repository cache.
     * 
     * @param uid the item UID
     * @return the file item, null if there was a problem retrieving it
     */
    public static StorageFileItem getCachedItem( RepositoryItemUid uid )
    {
        Repository repository = uid.getRepository();

        try
        {
            ResourceStoreRequest request = new ResourceStoreRequest( uid.getPath() );
            StorageItem item = repository.getLocalStorage().retrieveItem( repository, request );
            if ( item instanceof StorageFileItem )
            {
                return (StorageFileItem) item;
            }
        }
        catch ( ItemNotFoundException e )
        {
            // drop through
        }
        catch ( StorageException e )
        {
            // drop through
        }

        return null;
    }

    /**
     * Calculates the number of "../" segments needed to reach the root from the given path.
     * 
     * @param path the starting path
     * @return the relative path to root
     */
    public static String getPathToRoot( String path )
    {
        String normalizedPath = FileUtils.normalize( StringUtils.stripStart( path, "/" ) );
        return StringUtils.repeat( "../", StringUtils.countMatches( normalizedPath, "/" ) );
    }

    /**
     * Creates a new {@link UrlTransformer} that makes resource URLs relative to the OBR metadata path.
     * 
     * @param rootUrl the root URL
     * @param metadataPath the metadata path
     * @return a relative {@link UrlTransformer}
     */
    public static UrlTransformer getUrlChomper( URL rootUrl, String metadataPath )
    {
        final String rootUrlPattern = '^' + Pattern.quote( rootUrl.toExternalForm() ) + "/*";
        final String pathFromMetadataToRoot = getPathToRoot( metadataPath );

        return new UrlTransformer()
        {
            public String transform( URL url )
            {
                return url.toExternalForm().replaceFirst( rootUrlPattern, pathFromMetadataToRoot );
            }
        };

    }

    /**
     * Builds OBR metadata by walking the target repository and processing any potential OSGi bundle resources.
     * 
     * @param source the OBR metadata source
     * @param uid the metadata UID
     * @param target the target repository
     * @param walker the repository walker
     * @throws StorageException
     */
    public static void buildObr( final ObrMetadataSource source, RepositoryItemUid uid, Repository target, Walker walker )
        throws StorageException
    {
        final ObrResourceWriter writer = source.getWriter( uid );

        try
        {
            AbstractFileWalkerProcessor obrProcessor = new AbstractFileWalkerProcessor()
            {
                @Override
                protected void processFileItem( WalkerContext context, StorageFileItem item )
                    throws IOException
                {
                    Resource resource = source.buildResource( item );
                    if ( null != resource )
                    {
                        writer.append( resource );
                    }
                }
            };

            ResourceStoreRequest request = new ResourceStoreRequest( "/" );
            DefaultWalkerContext ctx = new DefaultWalkerContext( target, request, new ObrWalkerFilter() );
            ctx.getProcessors().add( obrProcessor );
            walker.walk( ctx );

            writer.complete(); // the OBR is only updated once the stream is complete and closed
        }
        catch ( WalkerException e )
        {
            writer.complete();
        }
        finally
        {
            ObrUtils.close( writer );
        }
    }

    /**
     * Updates the OBR metadata by streaming the resources and adding/updating/removing the affected resource.
     * 
     * @param source the OBR metadata source
     * @param uid the metadata UID
     * @param resource the affected resource
     * @param adding true when adding/updating, false when removing
     * @throws StorageException
     */
    public static void updateObr( ObrMetadataSource source, RepositoryItemUid uid, Resource resource, boolean adding )
        throws StorageException
    {
        ObrResourceWriter writer = null;
        ObrResourceReader reader = null;

        try
        {
            writer = source.getWriter( uid );
            reader = source.getReader( new ManagedObrSite( retrieveObrItem( uid.getRepository() ) ) );
            for ( Resource i = reader.readResource(); i != null; i = reader.readResource() )
            {
                if ( i.equals( resource ) )
                {
                    if ( adding ) // only update once, remove any duplicates
                    {
                        writer.append( resource );
                        adding = false;
                    }
                }
                else
                {
                    writer.append( i );
                }
            }

            if ( adding ) // not seen this resource before
            {
                writer.append( resource );
            }

            writer.complete(); // the OBR is only updated once the stream is complete and closed
        }
        catch ( IOException e )
        {
            throw new StorageException( e );
        }
        finally
        {
            // avoid file locks by closing reader first
            ObrUtils.close( reader );
            ObrUtils.close( writer );
        }
    }

    /**
     * Add any relevant virtual OBR items to the given directory listing.
     * 
     * @param uid the directory item UID
     * @param items the original list of items
     * @return augmented list of items
     */
    public static Collection<StorageItem> augmentListedItems( RepositoryItemUid uid, Collection<StorageItem> items )
    {
        Repository repository = uid.getRepository();
        ResourceStoreRequest request;

        Collection<StorageItem> augmentedItems = new ArrayList<StorageItem>( items );
        LocalRepositoryStorage storage = repository.getLocalStorage();

        try
        {
            if ( "/".equals( uid.getPath() ) )
            {
                request = new ResourceStoreRequest( META_PATH );
                if ( !storage.containsItem( repository, request ) )
                {
                    // need to create /.meta so we can safely traverse into it later on...
                    StorageItem metaDir = new DefaultStorageCollectionItem( repository, request, true, true );
                    storage.storeItem( repository, metaDir );
                    augmentedItems.add( metaDir );
                }
            }
            else if ( META_PATH.equals( uid.getPath() ) )
            {
                request = new ResourceStoreRequest( OBR_PATH );
                if ( !storage.containsItem( repository, request ) )
                {
                    // add a temporary storage item to the list (don't actually store it)
                    ContentLocator content = new StringContentLocator( "<repository/>" );
                    StorageItem obrFile = new DefaultStorageFileItem( repository, request, true, true, content );
                    augmentedItems.add( obrFile );
                }
            }
        }
        catch ( Exception e )
        {
            // ignore
        }

        return augmentedItems;
    }

    /**
     * Similar to {@link IOUtil#close(InputStream)} but closes {@link Closeable} instances.
     * 
     * @param closeable the {@link Closeable} to be closed
     */
    public static void close( Closeable closeable )
    {
        try
        {
            closeable.close();
        }
        catch ( Exception e )
        {
            // ignore
        }
    }
}
