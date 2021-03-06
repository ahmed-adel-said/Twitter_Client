package com.ahmedadel.twitterclient.data;

import com.ahmedadel.twitterclient.model.RealmTweetsJsonSerializerDeserializer;
import com.ahmedadel.twitterclient.model.RealmUserJsonSerializerDeserializer;
import com.ahmedadel.twitterclient.model.Tweets;
import com.ahmedadel.twitterclient.model.User;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.twitter.sdk.android.core.AuthenticatedClient;
import com.twitter.sdk.android.core.Session;
import com.twitter.sdk.android.core.TwitterAuthConfig;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.internal.TwitterApi;
import com.twitter.sdk.android.core.models.BindingValues;
import com.twitter.sdk.android.core.models.BindingValuesAdapter;
import com.twitter.sdk.android.core.models.SafeListAdapter;
import com.twitter.sdk.android.core.models.SafeMapAdapter;
import com.twitter.sdk.android.core.services.AccountService;
import com.twitter.sdk.android.core.services.CollectionService;
import com.twitter.sdk.android.core.services.ConfigurationService;
import com.twitter.sdk.android.core.services.FavoriteService;
import com.twitter.sdk.android.core.services.ListService;
import com.twitter.sdk.android.core.services.MediaService;
import com.twitter.sdk.android.core.services.SearchService;
import com.twitter.sdk.android.core.services.StatusesService;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import javax.net.ssl.SSLSocketFactory;

import io.realm.RealmList;
import retrofit.RestAdapter;
import retrofit.android.MainThreadExecutor;
import retrofit.converter.GsonConverter;

/**
 * Created by ahmedadel on 25/06/16.
 *
 * TwitterApiClient is an customize version of real TwitterApiClient that exists inside Twitter SDK.
 * We create a customize one to handle edit GsonBuilder and add our custom registerTypeAdapter.
 * We have a problem with Retrofit and Gson.Gson doesn’t know that RealmList ist just a wrapper class. So that's why
 * we made a our custom JsonSerializer and JsonDeserializer for User and Tweets Model. Because these two models are used
 * inside Followers and UserTweets Models as List so they couldn't be casted into normal List<> class
 *
 */
public class TwitterApiClient {
    private static final String UPLOAD_ENDPOINT = "https://upload.twitter.com";
    final ConcurrentHashMap<Class, Object> services;
    final RestAdapter apiAdapter;
    final RestAdapter uploadAdapter;

    TwitterApiClient(TwitterAuthConfig authConfig,
                     Session session,
                     TwitterApi twitterApi,
                     SSLSocketFactory sslSocketFactory, ExecutorService executorService) {

        if (session == null) {
            throw new IllegalArgumentException("Session must not be null.");
        }

        this.services = new ConcurrentHashMap<>();

        final Gson gson = new GsonBuilder()
                .registerTypeAdapterFactory(new SafeListAdapter())
                .registerTypeAdapterFactory(new SafeMapAdapter())
                // Using RealmUserJsonSerializerDeserializer class for Users List
                .registerTypeAdapter(new TypeToken<RealmList<User>>() {
                        }.getType(),
                        new RealmUserJsonSerializerDeserializer())
                // Using RealmTweetsJsonSerializerDeserializer class for Tweets List
                .registerTypeAdapter(new TypeToken<RealmList<Tweets>>() {
                        }.getType(),
                        new RealmTweetsJsonSerializerDeserializer())
                .registerTypeAdapter(BindingValues.class, new BindingValuesAdapter())
                .create();

        apiAdapter = new RestAdapter.Builder()
                .setClient(new AuthenticatedClient(authConfig, session, sslSocketFactory))
                .setEndpoint(twitterApi.getBaseHostUrl())
                .setConverter(new GsonConverter(gson))
                .setExecutors(executorService, new MainThreadExecutor())
                .build();

        uploadAdapter = new RestAdapter.Builder()
                .setClient(new AuthenticatedClient(authConfig, session, sslSocketFactory))
                .setEndpoint(UPLOAD_ENDPOINT)
                .setConverter(new GsonConverter(gson))
                .setExecutors(executorService, new MainThreadExecutor())
                .build();
    }

    /**
     * Must be instantiated after {@link com.twitter.sdk.android.core.TwitterCore} has been
     * initialized via {@link io.fabric.sdk.android.Fabric#with(android.content.Context, io.fabric.sdk.android.Kit[])}.
     *
     * @param session Session to be used to create the API calls.
     * @throws IllegalArgumentException if TwitterSession argument is null
     */
    public TwitterApiClient(Session session) {
        this(TwitterCore.getInstance().getAuthConfig(), session, new TwitterApi(),
                TwitterCore.getInstance().getSSLSocketFactory(),
                TwitterCore.getInstance().getFabric().getExecutorService());
    }

    /**
     * @return {@link com.twitter.sdk.android.core.services.AccountService} to access TwitterApi
     */
    public AccountService getAccountService() {
        return getService(AccountService.class);
    }

    /**
     * @return {@link com.twitter.sdk.android.core.services.FavoriteService} to access TwitterApi
     */
    public FavoriteService getFavoriteService() {
        return getService(FavoriteService.class);
    }

    /**
     * @return {@link com.twitter.sdk.android.core.services.StatusesService} to access TwitterApi
     */
    public StatusesService getStatusesService() {
        return getService(StatusesService.class);
    }

    /**
     * @return {@link com.twitter.sdk.android.core.services.SearchService} to access TwitterApi
     */
    public SearchService getSearchService() {
        return getService(SearchService.class);
    }

    /**
     * @return {@link com.twitter.sdk.android.core.services.ListService} to access TwitterApi
     */
    public ListService getListService() {
        return getService(ListService.class);
    }

    /**
     * Use CollectionTimeline directly, CollectionService is expected to change.
     *
     * @return {@link CollectionService} to access TwitterApi
     */
    public CollectionService getCollectionService() {
        return getService(CollectionService.class);
    }

    /**
     * @return {@link com.twitter.sdk.android.core.services.ConfigurationService} to access TwitterApi
     */
    public ConfigurationService getConfigurationService() {
        return getService(ConfigurationService.class);
    }

    /**
     * @return {@link com.twitter.sdk.android.core.services.MediaService} to access Twitter API
     * upload endpoints.
     */
    public MediaService getMediaService() {
        return getAdapterService(uploadAdapter, MediaService.class);
    }

    /**
     * Converts Retrofit style interface into instance for API access
     *
     * @param cls Retrofit style interface
     * @return instance of cls
     */
    @SuppressWarnings("unchecked")
    protected <T> T getService(Class<T> cls) {
        return getAdapterService(apiAdapter, cls);
    }

    /**
     * Converts a Retrofit style interfaces into an instance using the given RestAdapter.
     *
     * @param adapter the retrofit RestAdapter to use to generate a service instance
     * @param cls     Retrofit style service interface
     * @return instance of cls
     */
    @SuppressWarnings("unchecked")
    protected <T> T getAdapterService(RestAdapter adapter, Class<T> cls) {
        if (!services.contains(cls)) {
            services.putIfAbsent(cls, adapter.create(cls));
        }
        return (T) services.get(cls);
    }
}
