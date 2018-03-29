## Purpose 

This booster showcases using Istio's A/B Testing capabilities with a set of example applications configured with appropriate routing and rules.

The primary objectives are:
 * Deploy a client UI application (client-service-consumer) and a load-balanced service (my-service) with two pods running different implementations (versions): service-a, and service-b.
 * Demonstrate creation of an Istio Ingress rule to expose the client UI.
 * Demonstrate that pods are load-balanced with equal weights by default, using OpenShift's default service clustering mechanism.
 * Demonstrate that Istio can be used to dynamically re-balance deployed pods in a service using a RouteRule.

## Prerequisites
 * Minishift is installed and running on a the developer's environment, or the developer has access to an OpenShift instance that meets all prerequisites.
 * Developer has installed Istio 0.7.0 onto the Minishift/OpenShift instance.
 * Developer is logged in to Minishift/OpenShift with the Admin user.

## Steps & Procedure

First, create a new project on the cluster instance and grant service account privileges enabling its pods to access features not available to normal (restricted) pods. Then ensure that the istio-ingress service has been exposed --- this enables us to access our applications via the Istio system, and is the first step to using Istio in any project with externally facing services.

```bash
oc new-project demo-istio
# (manually change the policy field to disabled in configmap istio-inject in the istio-system namespace)
oc label namespace demo-istio istio-injection=enabled
# (ensure Istio is accessible from a public URL)
oc expose svc istio-ingress -n istio-system
```

Next, build and deploy the application using the Fabric8 Maven Plugin (FMP). Configuration for the FMP may be found both in pom.xml and `src/main/fabric8` files/folders. This configuration is used to define service names and deployments that control how pods are labeled/versioned on the OpenShift cluster. Labels and versions are key concepts for creating A/B testing or multi-versioned pods in a service.

Create a route rule to properly forward traffic to the demo application. This is only necessary if your application accepts traffic at a different port/url than the default. In this case, our application accepts traffic at '/', but we will access it with the path '/example'.

```bash
mvn clean package fabric8:deploy -Popenshift
oc create -f rules/client-route-rule.yml  
```
Finally, access the application via the istio-system istio-ingress application URL. Run this command to determine the appropriate URL to access our demo:

---For Minishift---

```bash
echo http://$(minishift openshift service istio-ingress -n istio-system --url)/example/
```
---For a hosted OpenShift cluster---

```bash
echo http://$(oc get route istio-ingress -o jsonpath='{.spec.host}{"\n"}' -n istio-system)/example/
```
Make sure you access the above url with the HTTP scheme. HTTPS is NOT enabled by default.

Click "Invoke Service" in the client UI; do this several times. You will notice that the services are currently load-balanced at exactly 50%. This is not always desireable for an A/B deployment. Sometimes it is important to slowly direct traffic to a new service over time. In this case, we can supply an Istio RouteRule to control load balancing behavior:

```bash
oc create -f rules/ab-test-rule.yml
```

Note that the RouteRule defined in the file above uses labels "a" and "b" to identify each unique version of the service. If multiple services match any of these labels, traffic will be divided between them accordingly. Additional routes/weights can be supplied using additional labels/service versions as desired.

Click "Invoke Service" in the client UI; do this several times. You will notice that traffic is no longer routed at 50/50%, and more traffic is directed to service version B than service version A. Adjust the weights in the rule-file and re-run the command above. You should see traffic adjust accordingly.

Congratulations! You now know how to direct traffic between different versions of a service using Istio RouteRules.
