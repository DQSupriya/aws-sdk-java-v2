/*
 * Copyright 2010-2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.core.client.builder;

import static software.amazon.awssdk.utils.Validate.paramNotNull;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import software.amazon.awssdk.annotations.SdkProtectedApi;
import software.amazon.awssdk.annotations.SdkTestInternalApi;
import software.amazon.awssdk.core.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.config.SdkImmutableAsyncClientConfiguration;
import software.amazon.awssdk.core.config.SdkImmutableSyncClientConfiguration;
import software.amazon.awssdk.core.config.SdkMutableClientConfiguration;
import software.amazon.awssdk.core.config.defaults.GlobalClientConfigurationDefaults;
import software.amazon.awssdk.core.config.defaults.SdkClientConfigurationDefaults;
import software.amazon.awssdk.core.http.loader.DefaultSdkAsyncHttpClientBuilder;
import software.amazon.awssdk.core.http.loader.DefaultSdkHttpClientBuilder;
import software.amazon.awssdk.http.AbortableCallable;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkRequestContext;
import software.amazon.awssdk.http.async.AbortableRunnable;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.async.SdkHttpRequestProvider;
import software.amazon.awssdk.http.async.SdkHttpResponseHandler;
import software.amazon.awssdk.utils.AttributeMap;
import software.amazon.awssdk.utils.Either;
import software.amazon.awssdk.utils.Validate;

/**
 * An SDK-internal implementation of the methods in {@link SdkClientBuilder}, {@link SdkAsyncClientBuilder} and
 * {@link SdkSyncClientBuilder}. This implements all methods required by those interfaces, allowing service-specific builders to
 * just implement the configuration they wish to add.
 *
 * <p>By implementing both the sync and async interface's methods, service-specific builders can share code between their sync
 * and
 * async variants without needing one to extend the other. Note: This only defines the methods in the sync and async builder
 * interfaces. It does not implement the interfaces themselves. This is because the sync and async client builder interfaces both
 * require a type-constrained parameter for use in fluent chaining, and a generic type parameter conflict is introduced into the
 * class hierarchy by this interface extending the builder interfaces themselves.</p>
 *
 * <p>Like all {@link SdkClientBuilder}s, this class is not thread safe.</p>
 *
 * @param <B> The type of builder, for chaining.
 * @param <C> The type of client generated by this builder.
 */
@SdkProtectedApi
public abstract class SdkDefaultClientBuilder<B extends SdkClientBuilder<B, C>, C> implements SdkClientBuilder<B, C> {
    private static final SdkHttpClient.Builder DEFAULT_HTTP_CLIENT_BUILDER = new DefaultSdkHttpClientBuilder();
    private static final SdkAsyncHttpClient.Builder DEFAULT_ASYNC_HTTP_CLIENT_BUILDER = new DefaultSdkAsyncHttpClientBuilder();

    protected ExecutorProvider asyncExecutorProvider;

    private final SdkHttpClient.Builder defaultHttpClientBuilder;
    private final SdkAsyncHttpClient.Builder defaultAsyncHttpClientBuilder;

    private SdkMutableClientConfiguration mutableClientConfiguration = new SdkMutableClientConfiguration();

    private SdkHttpClient httpClient;
    private SdkHttpClient.Builder httpClientBuilder;
    private SdkAsyncHttpClient asyncHttpClient;
    private SdkAsyncHttpClient.Builder asyncHttpClientBuilder;

    protected SdkDefaultClientBuilder() {
        this(DEFAULT_HTTP_CLIENT_BUILDER, DEFAULT_ASYNC_HTTP_CLIENT_BUILDER);
    }

    @SdkTestInternalApi
    protected SdkDefaultClientBuilder(SdkHttpClient.Builder defaultHttpClientBuilder,
                                      SdkAsyncHttpClient.Builder defaultAsyncHttpClientBuilder) {
        this.defaultHttpClientBuilder = defaultHttpClientBuilder;
        this.defaultAsyncHttpClientBuilder = defaultAsyncHttpClientBuilder;
    }

    /**
     * Build a client using the current state of this builder. This is marked final in order to allow this class to add standard
     * "build" logic between all service clients. Service clients are expected to implement the {@link #buildClient} method, that
     * accepts the immutable client configuration generated by this build method.
     */
    public final C build() {
        return buildClient();
    }

    /**
     * Implemented by child classes to create a client using the provided immutable configuration objects. The async and sync
     * configurations are not yet immutable. Child classes will need to make them immutable in order to validate them and pass
     * them to the client's constructor.
     *
     * @return A client based on the provided configuration.
     */
    protected abstract C buildClient();

    /**
     * An optional hook that can be overridden by service client builders to set service-specific defaults.
     *
     * @return The service defaults that should be applied.
     */
    protected SdkClientConfigurationDefaults serviceDefaults() {
        return new SdkClientConfigurationDefaults() {
        };
    }

    /**
     * An optional hook that can be overridden by service client builders to supply service-specific defaults for HTTP related
     * configuraton.
     *
     * @return The service defaults that should be applied.
     */
    protected AttributeMap serviceSpecificHttpConfig() {
        return AttributeMap.empty();
    }

    /**
     * Return a sync client configuration object, populated with the following chain of priorities.
     * <ol>
     * <li>Customer Configuration</li>
     * <li>Builder-Specific Default Configuration</li>
     * <li>Service-Specific Default Configuration</li>
     * <li>Global Default Configuration</li>
     * </ol>
     */
    protected SdkImmutableSyncClientConfiguration syncClientConfiguration() {
        SdkMutableClientConfiguration configuration = mutableClientConfiguration.clone();
        builderDefaults().applySyncDefaults(configuration);
        serviceDefaults().applySyncDefaults(configuration);
        new GlobalClientConfigurationDefaults().applySyncDefaults(configuration);
        applySdkHttpClient(configuration);
        return new SdkImmutableSyncClientConfiguration(configuration);
    }

    /**
     * Return an async client configuration object, populated with the following chain of priorities.
     * <ol>
     * <li>Customer Configuration</li>
     * <li>Builder-Specific Default Configuration</li>
     * <li>Service-Specific Default Configuration</li>
     * <li>Global Default Configuration</li>
     * </ol>
     */
    protected SdkImmutableAsyncClientConfiguration asyncClientConfiguration() {
        SdkMutableClientConfiguration configuration = mutableClientConfiguration.clone();
        builderDefaults().applyAsyncDefaults(configuration);
        serviceDefaults().applyAsyncDefaults(configuration);
        new GlobalClientConfigurationDefaults().applyAsyncDefaults(configuration);
        applySdkAsyncHttpClient(configuration);
        return new SdkImmutableAsyncClientConfiguration(configuration);
    }

    protected void applySdkHttpClient(SdkMutableClientConfiguration config) {
        config.httpClient(resolveSdkHttpClient());
    }

    private SdkHttpClient resolveSdkHttpClient() {
        Validate.isTrue(httpClient == null || httpClientBuilder == null,
                        "The httpClient and the httpClientBuilder can't both be configured.");
        return Either.fromNullable(httpClient, httpClientBuilder)
                     .map(e -> e.map(NonManagedSdkHttpClient::new, b -> b.buildWithDefaults(serviceSpecificHttpConfig())))
                     .orElseGet(() -> defaultHttpClientBuilder.buildWithDefaults(serviceSpecificHttpConfig()));
    }

    protected void applySdkAsyncHttpClient(SdkMutableClientConfiguration config) {
        config.asyncHttpClient(resolveSdkAsyncHttpClient());
    }

    private SdkAsyncHttpClient resolveSdkAsyncHttpClient() {
        Validate.isTrue(asyncHttpClient == null || asyncHttpClientBuilder == null,
                        "The asyncHttpClient and the asyncHttpClientBuilder can't both be configured.");
        return Either.fromNullable(asyncHttpClient, asyncHttpClientBuilder)
                     .map(e -> e.map(NonManagedSdkAsyncHttpClient::new, b -> b.buildWithDefaults(serviceSpecificHttpConfig())))
                     .orElseGet(() -> defaultAsyncHttpClientBuilder.buildWithDefaults(serviceSpecificHttpConfig()));
    }

    /**
     * Add builder-specific configuration on top of the customer-defined configuration, if needed. Specifically, if the customer
     * has specified a region in place of an endpoint, this will determine the endpoint to be used for AWS communication.
     */
    protected SdkClientConfigurationDefaults builderDefaults() {
        return new SdkClientConfigurationDefaults() {
            /**
             * If the customer did not specify an endpoint themselves, attempt to generate one automatically.
             */
            @Override
            protected URI getEndpointDefault() {
                return resolveEndpoint().orElse(null);
            }

            /**
             * Create the async executor service that should be used for async client executions.
             */
            @Override
            protected ScheduledExecutorService getAsyncExecutorDefault() {
                return Optional.ofNullable(asyncExecutorProvider).map(ExecutorProvider::get).orElse(null);
            }
        };
    }

    /**
     * Resolve the service endpoint that should be used based on the customer's configuration.
     */
    protected Optional<URI> resolveEndpoint() {
        URI configuredEndpoint = mutableClientConfiguration.endpoint();
        return configuredEndpoint != null ? Optional.of(configuredEndpoint) : Optional.empty();
    }

    @Override
    public B endpointOverride(URI endpointOverride) {
        mutableClientConfiguration.endpoint(endpointOverride);
        return thisBuilder();
    }

    public void setEndpointOverride(URI endpointOverride) {
        endpointOverride(endpointOverride);
    }

    public B asyncExecutorProvider(ExecutorProvider asyncExecutorProvider) {
        this.asyncExecutorProvider = asyncExecutorProvider;
        return thisBuilder();
    }

    public void setAsyncExecutorProvider(ExecutorProvider asyncExecutorProvider) {
        asyncExecutorProvider(asyncExecutorProvider);
    }

    // Getters and setters that just delegate to the mutable client configuration

    @Override
    public B overrideConfiguration(ClientOverrideConfiguration overrideConfiguration) {
        mutableClientConfiguration.overrideConfiguration(overrideConfiguration);
        return thisBuilder();
    }

    public void setOverrideConfiguration(ClientOverrideConfiguration overrideConfiguration) {
        overrideConfiguration(overrideConfiguration);
    }

    public final B httpClient(SdkHttpClient httpClient) {
        this.httpClient = httpClient;
        return thisBuilder();
    }

    public final B httpClientBuilder(SdkHttpClient.Builder httpClientBuilder) {
        this.httpClientBuilder = httpClientBuilder;
        return thisBuilder();
    }

    public final B asyncHttpClient(SdkAsyncHttpClient httpClient) {
        this.asyncHttpClient = httpClient;
        return thisBuilder();
    }

    public final B asyncHttpClientBuilder(SdkAsyncHttpClient.Builder httpClientBuilder) {
        this.asyncHttpClientBuilder = httpClientBuilder;
        return thisBuilder();
    }

    /**
     * Return "this" for method chaining.
     */
    @SuppressWarnings("unchecked")
    protected B thisBuilder() {
        return (B) this;
    }

    /**
     * Wrapper around {@link SdkHttpClient} to prevent it from being closed. Used when the customer provides
     * an already built client in which case they are responsible for the lifecycle of it.
     */
    @SdkTestInternalApi
    public static class NonManagedSdkHttpClient implements SdkHttpClient {

        private final SdkHttpClient delegate;

        private NonManagedSdkHttpClient(SdkHttpClient delegate) {
            this.delegate = paramNotNull(delegate, "SdkHttpClient");
        }

        @Override
        public AbortableCallable<SdkHttpFullResponse> prepareRequest(SdkHttpFullRequest request,
                                                                     SdkRequestContext requestContext) {
            return delegate.prepareRequest(request, requestContext);
        }

        @Override
        public <T> Optional<T> getConfigurationValue(SdkHttpConfigurationOption<T> key) {
            return delegate.getConfigurationValue(key);
        }

        @Override
        public void close() {
            // Do nothing, this client is managed by the customer.
        }
    }

    /**
     * Wrapper around {@link SdkAsyncHttpClient} to prevent it from being closed. Used when the customer provides
     * an already built client in which case they are responsible for the lifecycle of it.
     */
    @SdkTestInternalApi
    public static class NonManagedSdkAsyncHttpClient implements SdkAsyncHttpClient {

        private final SdkAsyncHttpClient delegate;

        NonManagedSdkAsyncHttpClient(SdkAsyncHttpClient delegate) {
            this.delegate = paramNotNull(delegate, "SdkAsyncHttpClient");
        }

        @Override
        public AbortableRunnable prepareRequest(SdkHttpRequest request, SdkRequestContext context,
                                                SdkHttpRequestProvider requestProvider, SdkHttpResponseHandler handler) {
            return delegate.prepareRequest(request, context, requestProvider, handler);
        }

        @Override
        public <T> Optional<T> getConfigurationValue(SdkHttpConfigurationOption<T> key) {
            return delegate.getConfigurationValue(key);
        }

        @Override
        public void close() {
            // Do nothing, this client is managed by the customer.
        }
    }

}