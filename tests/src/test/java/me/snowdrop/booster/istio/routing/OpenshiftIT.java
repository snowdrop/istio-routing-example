package me.snowdrop.booster.istio.routing;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.apache.http.HttpStatus;
import org.arquillian.cube.istio.api.IstioResource;
import org.arquillian.cube.istio.impl.IstioAssistant;
import org.arquillian.cube.openshift.impl.enricher.RouteURL;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.After;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.runners.MethodSorters;

import static org.awaitility.Awaitility.await;

/**
 * @author Martin Ocenas
 */
@RunWith(Arquillian.class)
@IstioResource("classpath:client-gateway-rule.yml")
// this is a stop gap solution until deletion of the custom resources works correctly
// see https://github.com/snowdrop/istio-java-api/issues/31
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class OpenshiftIT{
    private static final String ISTIO_NAMESPACE = "istio-system";
    private static final String ISTIO_INGRESS_GATEWAY_NAME = "istio-ingressgateway";

    private static final int MEASURMENT_COUNT = 200;

    @RouteURL(value = ISTIO_INGRESS_GATEWAY_NAME, namespace = ISTIO_NAMESPACE)
    private URL ingressGatewayURL;

    @ArquillianResource
    private IstioAssistant istioAssistant;

    private final String appUrl = "example/";
    private final String dataUrlSuffix = "request-data";

    private static List<me.snowdrop.istio.api.model.IstioResource> additionalRouteRule = null;

    @After
    public void cleanup() throws InterruptedException {
        if (additionalRouteRule != null) {
            istioAssistant.undeployIstioResources(additionalRouteRule);
            Thread.sleep(10000); //sleep 10 sec to ensure rule is removed
            additionalRouteRule = null;
        }
    }

    @Test
    public void basicAccessTest() {
        waitUntilApplicationIsReady();
        RestAssured
                .expect()
                .statusCode(HttpStatus.SC_OK)
                .when()
                .get(ingressGatewayURL + appUrl);
    }

    @Test
    public void defaultLoadBalancingTest() throws Exception {
        waitUntilApplicationIsReady();
        expectLoadBalancingRatio(50, 5);
    }

    @Test
    public void unequalLoadBalancingTest() throws Exception {
        additionalRouteRule = deployRouteRule("load-balancing-rule.yml");
        Thread.sleep(10000); //sleep 10 sec to make rule take effect
        waitUntilApplicationIsReady();
        expectLoadBalancingRatio(80, 10);
    }

    private List<me.snowdrop.istio.api.model.IstioResource> deployRouteRule(String routeRuleFile) throws IOException {
        return istioAssistant.deployIstioResources(
                Files.newInputStream(Paths.get("../rules/" + routeRuleFile)));
    }

    /**
     * Measure load balancing ratio and check if it is in acceptable difference
     * @param expectedRatio Expected load balancing ration
     * @param acceptableDifference The value of which the expected and actual ratio may differ, to make the test still pass
     * @throws Exception If measuring fails
     */
    private void expectLoadBalancingRatio(double expectedRatio, double acceptableDifference) throws Exception {
        double actualRatio = meassureLoadBalancing();
        Assert.assertTrue( "Load balancing ratio does not match the expected. Expected: " + expectedRatio + ", actual: " + actualRatio,
                Math.abs(expectedRatio -  actualRatio) <= acceptableDifference);
    }

    /**
     * Perform queries against the tested system and meassure how many responses were from which service
     * @return Percent of responses from one service (the greater number)
     * @throws Exception If data service returns an error
     */
    private double meassureLoadBalancing() throws Exception {
        int serviceACount = 0;
        int serviceBCount = 0;

        int i;
        for ( i = 0; i < MEASURMENT_COUNT ; i++){
            Response response;
            int tries=0;

            // service sometimes get overloaded and return code 503 (Connection refused - in server log)
            // retry connection in case of failure to avoid unnecessary test failure
            do {
                response = RestAssured
                    .when()
                    .get(ingressGatewayURL + appUrl + dataUrlSuffix);
               tries++;
               if (response.statusCode() != 200) {
                   Thread.sleep(10); //wait for service to recover
               }
            } while (response.statusCode() != 200 && tries <= 3);

            if (response.statusCode() != 200) {
                throw new Exception("Unable to get data from service, response code: " + response.statusCode());
            }

            // response is from the service A
            if (response.asString().contains("A!")){
                serviceACount++;
            }
            else {
                serviceBCount++;
            }
        }
        double requestsPercentRatio = i/100; // compute coefficient to recompute results to percents
        return Math.max((double)serviceACount / requestsPercentRatio, (double)serviceBCount / requestsPercentRatio);
    }

    private void waitUntilApplicationIsReady() {
        await()
                .pollInterval(1, TimeUnit.SECONDS)
                .atMost(1, TimeUnit.MINUTES)
                .untilAsserted(() ->
                        RestAssured
                                .given()
                                .baseUri(ingressGatewayURL.toString())
                                .when()
                                .get(appUrl + dataUrlSuffix)
                                .then()
                                .statusCode(200)
                );
    }
}
