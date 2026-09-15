package com.roots.account_management_client_bff.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.roots.account_management_client_bff.dto.response.AccountProfileResponse;
import reactor.core.publisher.Mono;

@Service
public class AccountManagementProxyService {
    private final WebClient webClient;
    private final String accountManagementLocation;

    public AccountManagementProxyService(
            WebClient webClient,
            @Value("${account-management.location}") String accountManagementLocation
    ) {
        this.webClient = webClient;
        this.accountManagementLocation = removeTrailingSlash(accountManagementLocation);
    }

    public Mono<ResponseEntity<AccountProfileResponse>> getAccountProfile(String userGUID) {
        return webClient
                .get()
                .uri(accountManagementLocation + "/api/account/profile?userGUID=" + userGUID)
                .exchangeToMono(clientResponse -> clientResponse.toEntity(AccountProfileResponse.class))
                .map(response -> ResponseEntity.status(response.getStatusCode()).body(response.getBody()));
    }

    private String removeTrailingSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
