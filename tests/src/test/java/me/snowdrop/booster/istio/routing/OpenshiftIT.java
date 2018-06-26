package me.snowdrop.booster.istio.routing;

import io.fabric8.openshift.api.model.v3_1.Route;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import me.snowdrop.istio.api.model.IstioResource;
import org.apache.http.HttpStatus;
import org.arquillian.cube.istio.impl.IstioAssistant;
import org.arquillian.cube.openshift.impl.client.OpenShiftAssistant;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

@RunWith(Arquillian.class)
public class OpenshiftIT{
    @ArquillianResource
    private IstioAssistant istioAssistant;

    @ArquillianResource
    private OpenShiftAssistant openShiftAssistant;

    private String istioURL;

    private final String appUrl = "example/";
    private final String dataUrlSuffix = "request-data";

    private List<IstioResource> clientRouteRule = null;
    private List<IstioResource> additionalRouteRule = null;

    @Before
    public void init() throws Exception {
        // get istio ingress route
        Route route = openShiftAssistant.getClient()
                .routes()
                .inNamespace("istio-system")
                .withName("istio-ingress")
                .get();
        if (route == null){
            throw new Exception("Istio ingress route not found");
        }
        istioURL = "http://" + route.getSpec().getHost() + "/";

        clientRouteRule = deployRouteRule("client-route-rule.yml");
        await()
                .pollInterval(1, TimeUnit.SECONDS)
                .atMost(1, TimeUnit.MINUTES)
                .until(() -> {
            try {
                Response response = RestAssured.get(istioURL + appUrl);
                return response.getStatusCode() == 200;
            } catch (Exception ignored) {
                return false;
            }
        });
    }

    @After
    public void cleanup(){
        if (clientRouteRule != null) {
            istioAssistant.undeployIstioResources(clientRouteRule);
            clientRouteRule = null;
        }
        if (additionalRouteRule != null) {
            istioAssistant.undeployIstioResources(additionalRouteRule);
            additionalRouteRule = null;
        }
    }

    @Test
    public void basicAccessTest() {
        RestAssured
                .expect()
                .statusCode(HttpStatus.SC_OK)
                .when()
                .get(istioURL + appUrl);
    }

    @Test
    public void defaultLoadBalancingTest() throws Exception {
        expectLoadBalancingRatio(50, 5);
    }

    @Test
    public void unequalLoadBalancingTest() throws Exception {
        additionalRouteRule = deployRouteRule("load-balancing-rule.yml");
        Thread.sleep(10000); //sleep 10 sec to make rule take effect
        expectLoadBalancingRatio(80, 10);
    }

    private List<IstioResource> deployRouteRule(String routeRuleFile) throws IOException {
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
        for ( i = 0; i < 100 ; i++){
            Response response;
            int tries=0;

            // service sometimes get overloaded and return code 503 (Connection refused - in server log)
            // retry connection in case of failure to avoid unnecessary test failure
            do {
                response = RestAssured
                    .when()
                    .get(istioURL + appUrl + dataUrlSuffix);
               tries++;
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
        double requestsPercentRatio = i/100;
        return Math.max((double)serviceACount / requestsPercentRatio, (double)serviceBCount / requestsPercentRatio);
    }
}
