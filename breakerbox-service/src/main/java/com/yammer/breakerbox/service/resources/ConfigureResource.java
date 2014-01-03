package com.yammer.breakerbox.service.resources;

import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.yammer.breakerbox.service.azure.DependencyEntity;
import com.yammer.breakerbox.service.comparable.SortRowFirst;
import com.yammer.breakerbox.service.core.BreakerboxStore;
import com.yammer.breakerbox.service.core.Instances;
import com.yammer.breakerbox.service.core.SyncComparator;
import com.yammer.breakerbox.service.store.TenacityPropertyKeysStore;
import com.yammer.breakerbox.service.util.SimpleDateParser;
import com.yammer.breakerbox.service.views.ConfigureView;
import com.yammer.breakerbox.service.views.NoPropertyKeysView;
import com.yammer.breakerbox.service.views.OptionItem;
import com.yammer.breakerbox.store.DependencyId;
import com.yammer.breakerbox.store.ServiceId;
import com.yammer.dropwizard.auth.Auth;
import com.yammer.dropwizard.auth.basic.BasicCredentials;
import com.yammer.dropwizard.views.View;
import com.yammer.metrics.annotation.Timed;
import com.yammer.tenacity.core.config.CircuitBreakerConfiguration;
import com.yammer.tenacity.core.config.TenacityConfiguration;
import com.yammer.tenacity.core.config.ThreadPoolConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;

@Path("/configure/{service}")
public class ConfigureResource {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigureResource.class);

    private final BreakerboxStore breakerboxStore;
    private final TenacityPropertyKeysStore tenacityPropertyKeysStore;
    private final SyncComparator syncComparator;

    public ConfigureResource(BreakerboxStore breakerboxStore,
                             TenacityPropertyKeysStore tenacityPropertyKeysStore,
                             SyncComparator syncComparator) {
        this.breakerboxStore = breakerboxStore;
        this.tenacityPropertyKeysStore = tenacityPropertyKeysStore;
        this.syncComparator = syncComparator;
    }

    @GET @Timed @Produces(MediaType.TEXT_HTML)
    public View render(@PathParam("service") String serviceName) {
        final ServiceId serviceId = ServiceId.from(serviceName);
        final Optional<String> firstDependencyKey = FluentIterable
                .from(tenacityPropertyKeysStore.tenacityPropertyKeysFor(Instances.propertyKeyUris(serviceId)))
                .first();
        if (firstDependencyKey.isPresent()) {
            return create(serviceId, DependencyId.from(firstDependencyKey.get()), Optional.<Long>absent());
        } else {
            return new NoPropertyKeysView(serviceId);
        }
    }

    @GET @Timed @Produces(MediaType.TEXT_HTML)
    @Path("/{dependency}")
    public ConfigureView render(@PathParam("service") String serviceName,
                                @PathParam("dependency") String dependencyName,
                                @QueryParam("version") String version) {
        return create(ServiceId.from(serviceName), DependencyId.from(dependencyName), getVersion(version));
    }

    private ConfigureView create(ServiceId serviceId,
                                 DependencyId dependencyId,
                                 Optional<Long> version) {
        final ImmutableList<DependencyEntity> dependencyEntities = breakerboxStore.listConfigurations(dependencyId, serviceId);
        final ImmutableSet<String> propertyKeys = tenacityPropertyKeysStore.tenacityPropertyKeysFor(Instances.propertyKeyUris(serviceId));
        return new ConfigureView(
                serviceId,
                syncComparator.allInSync(serviceId, propertyKeys),
                getConfiguration(dependencyId, version, serviceId),
                getDependencyVersionNameList(dependencyEntities));
    }

    private TenacityConfiguration getConfiguration(DependencyId dependencyId, Optional<Long> version, ServiceId serviceId) {
        final Optional<DependencyEntity> dependencyEntity = version.isPresent()
                ? breakerboxStore.retrieve(dependencyId, version.get(), serviceId)
                : breakerboxStore.retrieveLatest(dependencyId, serviceId);

        if (dependencyEntity.isPresent()) {
            return dependencyEntity.get().getConfiguration().get();
        } else {
            return new TenacityConfiguration();
        }
    }

    private ImmutableList<OptionItem> getDependencyVersionNameList(ImmutableList<DependencyEntity> dependencyEntities) {
        final ImmutableList<DependencyEntity> sortedEntities =
                Ordering.from(new SortRowFirst())
                        .immutableSortedCopy(dependencyEntities);

        final ImmutableList.Builder<OptionItem> builder = ImmutableList.builder();
        if (sortedEntities.isEmpty()) {
            builder.add(new OptionItem("Default", 0l));
        } else {
            for (DependencyEntity entity : sortedEntities) {
                builder.add(new OptionItem(SimpleDateParser.millisToDate(entity.getRowKey()) + " - " + entity.getUser(), entity.getConfigurationTimestamp()));
            }
        }
        return builder.build();
    }

    private Optional<Long> getVersion(String version) {
        if (version != null) {
            try {
                return Optional.of(Long.parseLong(version));
            } catch (Exception e) {
                LOG.warn("failed to parse version {}. {}", version, e);
            }
        }
        return Optional.absent();
    }

    @GET @Timed @Produces(MediaType.APPLICATION_JSON)
    @Path("/{dependency}")
    public TenacityConfiguration get(@PathParam("service") String serviceName,
                                     @PathParam("dependency") String dependencyName) {
        final Optional<DependencyEntity> entity = breakerboxStore.retrieveLatest(DependencyId.from(dependencyName), ServiceId.from(serviceName));
        if (entity.isPresent()) {
            return entity.get().getConfiguration().or(DependencyEntity.defaultConfiguration());
        }
        throw new WebApplicationException();
    }


    @POST @Timed @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Path("/{dependency}")
    public Response configure(@Auth BasicCredentials creds,
                              @PathParam("service") String serviceName,
                              @PathParam("dependency") String dependencyName,
                              @FormParam("executionTimeout") Integer executionTimeout,
                              @FormParam("requestVolumeThreshold") Integer requestVolumeThreshold,
                              @FormParam("errorThresholdPercentage") Integer errorThresholdPercentage,
                              @FormParam("sleepWindow") Integer sleepWindow,
                              @FormParam("circuitBreakerstatisticalWindow") Integer circuitBreakerstatisticalWindow,
                              @FormParam("circuitBreakerStatisticalWindowBuckets") Integer circuitBreakerStatisticalWindowBuckets,
                              @FormParam("threadPoolCoreSize") Integer threadPoolCoreSize,
                              @FormParam("keepAliveMinutes") Integer keepAliveMinutes,
                              @FormParam("maxQueueSize") Integer maxQueueSize,
                              @FormParam("queueSizeRejectionThreshold") Integer queueSizeRejectionThreshold,
                              @FormParam("threadpoolStatisticalWindow") Integer threadpoolStatisticalWindow,
                              @FormParam("threadpoolStatisticalWindowBuckets") Integer threadpoolStatisticalWindowBuckets) {
        final TenacityConfiguration tenacityConfiguration = new TenacityConfiguration(
                new ThreadPoolConfiguration(
                        threadPoolCoreSize,
                        keepAliveMinutes,
                        maxQueueSize,
                        queueSizeRejectionThreshold,
                        threadpoolStatisticalWindow,
                        threadpoolStatisticalWindowBuckets),
                new CircuitBreakerConfiguration(
                        requestVolumeThreshold,
                        sleepWindow,
                        errorThresholdPercentage,
                        circuitBreakerstatisticalWindow,
                        circuitBreakerStatisticalWindowBuckets),
                executionTimeout);
        if (breakerboxStore.store(ServiceId.from(serviceName), DependencyId.from(dependencyName), tenacityConfiguration, creds.getUsername())) {
            return Response
                    .created(URI.create(String.format("/configuration/%s/%s", serviceName, dependencyName)))
                    .build();
        } else {
            return Response.serverError().build();
        }
    }

}
