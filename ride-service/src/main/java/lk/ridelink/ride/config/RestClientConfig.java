package lk.ridelink.ride.config;

import java.time.Duration;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link RestClient}s this service uses to call the other three.
 *
 * <p>Timeouts are the point of this class. Without them a single unresponsive service
 * would hold a passenger's HTTP request open indefinitely and, under any load, exhaust
 * this service's own thread pool - one slow dependency taking down a healthy service. The
 * values are deliberately short: a passenger waiting for a price will not tolerate more
 * than a few seconds, and failing fast lets them retry.</p>
 *
 * <p>Separate clients per downstream, rather than one shared instance, so each carries its
 * own base URL and so a future change to one service's timeouts does not affect the
 * others.</p>
 */
@Configuration
public class RestClientConfig {

    private final RideLinkProperties properties;

    public RestClientConfig(RideLinkProperties properties) {
        this.properties = properties;
    }

    @Bean
    public RestClient accountRestClient(RestClient.Builder builder) {
        return builder.clone()
                .baseUrl(properties.clients().accountUrl())
                .requestFactory(requestFactory())
                .build();
    }

    @Bean
    public RestClient driverRestClient(RestClient.Builder builder) {
        return builder.clone()
                .baseUrl(properties.clients().driverUrl())
                .requestFactory(requestFactory())
                .build();
    }

    @Bean
    public RestClient fareRestClient(RestClient.Builder builder) {
        return builder.clone()
                .baseUrl(properties.clients().fareUrl())
                .requestFactory(requestFactory())
                .build();
    }

    /**
     * A plain JDK-backed factory with explicit timeouts.
     *
     * <p>Deliberately the simple implementation rather than a pooled HTTP client: the call
     * volume here is low, and every member has to be able to explain this configuration.
     * What matters is that both timeouts are set, not which transport carries them.</p>
     */
    private ClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.clients().connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.clients().readTimeoutMs()));
        return factory;
    }

    /**
     * Forwards the correlation id on every outgoing call, so one passenger action can be
     * followed through all four services' logs.
     */
    @Bean
    public RestClientCustomizer correlationIdCustomizer() {
        return builder -> builder.requestInterceptor((request, body, execution) -> {
            String correlationId = CorrelationIdFilter.current();
            if (correlationId != null) {
                request.getHeaders().add(CorrelationIdFilter.HEADER, correlationId);
            }
            return execution.execute(request, body);
        });
    }
}
