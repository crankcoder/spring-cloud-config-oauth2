package com.devkwondo.configclient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Arrays;

@Import(OAuth2ClientConfiguration.class)
@Configuration
public class CustomConfigServiceBootstrapConfiguration {

    @Autowired
    private Environment environment;

	@Value("${message:Hello default}")
	private String message;

    @Autowired
    private OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager;

    @Bean
    public ConfigClientProperties configClientProperties() {
        ConfigClientProperties client = new ConfigClientProperties(this.environment);
        client.setEnabled(false);
        return client;
    }

    @Bean
    public ConfigServicePropertySourceLocator configServicePropertySourceLocator() {
        ConfigClientProperties clientProperties = configClientProperties();
        ConfigServicePropertySourceLocator configServicePropertySourceLocator =  new ConfigServicePropertySourceLocator(clientProperties);
        configServicePropertySourceLocator.setRestTemplate(getSecureRestTemplate(clientProperties));
        return configServicePropertySourceLocator;
    }

    private RestTemplate getSecureRestTemplate(ConfigClientProperties client) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setReadTimeout((60 * 1000 * 3) + 5000); // TODO 3m5s, make configurable?
        RestTemplate template = new RestTemplate(requestFactory);
        String password = client.getPassword();
        if (password != null) {
            template.setInterceptors(Arrays
                    .<ClientHttpRequestInterceptor>asList(new AuthorizationToken(oAuth2AuthorizedClientManager)));
        }
        return template;
    }

    private static class AuthorizationToken implements ClientHttpRequestInterceptor {

        private final String authenticationToken;
        private final OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager;

        public AuthorizationToken(String authenticationToken) {
            this.authenticationToken = (authenticationToken == null ? "" : authenticationToken);
            this.oAuth2AuthorizedClientManager = null;
        }

        public AuthorizationToken(OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager) {
            this.authenticationToken = null;
            this.oAuth2AuthorizedClientManager = oAuth2AuthorizedClientManager;
        }

        //        @Override
//        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
//                throws IOException {
//
//            request.getHeaders().add("Authorization", authenticationToken);
//            return execution.execute(request, body);
//        }


        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution clientHttpRequestExecution) throws IOException {

            Authentication principal = new AnonymousAuthenticationToken
                    ("key", "anonymous", AuthorityUtils.createAuthorityList("SCOPE_message.read"));

            OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId("messaging-client-client-creds")
                    .principal(principal)
//                    .attributes(attrs -> {
//                        attrs.put(HttpServletRequest.class.getName(), servletRequest);
//                        attrs.put(HttpServletResponse.class.getName(), servletResponse);
//                    })
                    .build();
            OAuth2AuthorizedClient authorizedClient = oAuth2AuthorizedClientManager.authorize(authorizeRequest);
            request.getHeaders().setBearerAuth(authorizedClient.getAccessToken().getTokenValue());
            return clientHttpRequestExecution.execute(request, body);
        }
    }

}

