package com.juryopt.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteLocator;

@SpringBootTest(properties = "ORCHESTRATOR_SERVICE_URL=http://localhost:8081")
class ApiGatewayRoutesTest {

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    void contextLoads_andDeclaresAllExpectedRoutes() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();

        assertThat(routes)
                .isNotNull()
                .extracting(Route::getId)
                .containsExactlyInAnyOrder("orchestrator-api", "orchestrator-docs", "orchestrator-health");

        assertThat(routes)
                .allSatisfy(route -> assertThat(route.getUri()).isEqualTo(URI.create("http://localhost:8081")));
    }

    @Test
    void routeDefinitions_shouldExposeExpectedPathsAndHealthRewriteFilter() {
        List<RouteDefinition> definitions = routeDefinitionLocator.getRouteDefinitions().collectList().block();

        assertThat(definitions).isNotNull();

        RouteDefinition apiRoute = findById(definitions, "orchestrator-api");
        assertThat(apiRoute.getPredicates())
                .anySatisfy(p -> {
                    assertThat(p.getName()).isEqualTo("Path");
                    assertThat(p.getArgs().values()).contains("/api/v1/**");
                });

        RouteDefinition docsRoute = findById(definitions, "orchestrator-docs");
        assertThat(docsRoute.getPredicates())
                .anySatisfy(p -> {
                    assertThat(p.getName()).isEqualTo("Path");
                    assertThat(p.getArgs().values()).contains("/swagger-ui.html", "/v3/api-docs.yaml");
                });

        RouteDefinition healthRoute = findById(definitions, "orchestrator-health");
        assertThat(healthRoute.getPredicates())
                .anySatisfy(p -> {
                    assertThat(p.getName()).isEqualTo("Path");
                    assertThat(p.getArgs().values()).contains("/ops/orchestrator/health");
                });
        assertThat(healthRoute.getFilters())
                .anySatisfy(f -> {
                    assertThat(f.getName()).isEqualTo("SetPath");
                    assertThat(f.getArgs().values()).contains("/actuator/health");
                });
    }

    private RouteDefinition findById(List<RouteDefinition> definitions, String id) {
        return definitions.stream()
                .filter(d -> id.equals(d.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Route introuvable : " + id));
    }
}